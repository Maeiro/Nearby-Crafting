package dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.service.prefs.PlayerPreferenceStore;
import dev.maeiro.nearbycrafting.service.scan.NearbyInventoryScanner;
import dev.maeiro.nearbycrafting.service.source.ItemSourceRef;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeWrapper;

import java.util.List;
import java.util.function.Consumer;

public class AdvancedCraftingUpgradeWrapper extends CraftingUpgradeWrapper {
	private static final String TAG_SCAN_ANCHOR = "nearbycrafting_scan_anchor";
	private static final String TAG_SCAN_X = "x";
	private static final String TAG_SCAN_Y = "y";
	private static final String TAG_SCAN_Z = "z";

	public AdvancedCraftingUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
		super(storageWrapper, upgrade, upgradeSaveHandler);
	}

	@Override
	public boolean extractFromStorageOrPlayer(Player player, ItemStack toExtract) {
		if (player == null || toExtract.isEmpty()) {
			return false;
		}

		var preferenceSnapshot = PlayerPreferenceStore.get(player);
		boolean includePlayerInventory = preferenceSnapshot
				.map(PlayerPreferenceStore.PreferenceSnapshot::includePlayerInventory)
				.orElse(true);
		NearbyCraftingConfig.SourcePriority effectivePriority = preferenceSnapshot
				.map(PlayerPreferenceStore.PreferenceSnapshot::sourcePriority)
				.orElseGet(() -> NearbyCraftingConfig.SourcePriority.fromConfig(NearbyCraftingConfig.SERVER.advancedBackpackSourcePriority.get()));

		BlockPos scanAnchor = getScanAnchorOrPlayerPos(player);
		List<ItemSourceRef> sources = NearbyInventoryScanner.collectAdvancedBackpackSources(
				player.level(),
				scanAnchor,
				player,
				includePlayerInventory,
				effectivePriority,
				storageWrapper.getInventoryHandler()
		);

		if (NearbyCraftingConfig.SERVER.debugLogging.get()) {
			int containerSources = 0;
			int playerSources = 0;
			int backpackSources = 0;
			for (ItemSourceRef source : sources) {
				switch (source.sourceType()) {
					case CONTAINER -> containerSources++;
					case PLAYER -> playerSources++;
					case PLAYER_BACKPACK -> backpackSources++;
				}
			}
			int availableMatching = countMatchingItems(sources, toExtract);
			NearbyCrafting.LOGGER.info(
					"[NC-ADV-UPGRADE] extract request player={} stack={}x{} anchor={} includePlayer={} priority={} sources(total={}, containers={}, player={}, backpacks={}) availableMatching={}",
					player.getGameProfile().getName(),
					toExtract.getItem(),
					toExtract.getCount(),
					scanAnchor,
					includePlayerInventory,
					effectivePriority,
					sources.size(),
					containerSources,
					playerSources,
					backpackSources,
					availableMatching
			);
		}

		if (extractExactFromSources(sources, toExtract)) {
			if (NearbyCraftingConfig.SERVER.debugLogging.get()) {
				NearbyCrafting.LOGGER.info(
						"[NC-ADV-UPGRADE] extract success player={} stack={}x{}",
						player.getGameProfile().getName(),
						toExtract.getItem(),
						toExtract.getCount()
				);
			}
			return true;
		}

		// Fallback keeps vanilla crafting-upgrade behavior if a capability handler refuses extraction.
		if (NearbyCraftingConfig.SERVER.debugLogging.get()) {
			NearbyCrafting.LOGGER.info(
					"[NC-ADV-UPGRADE] extract fallback to vanilla behavior player={} stack={}x{}",
					player.getGameProfile().getName(),
					toExtract.getItem(),
					toExtract.getCount()
			);
		}
		return super.extractFromStorageOrPlayer(player, toExtract);
	}

	public void setScanAnchor(BlockPos pos) {
		if (pos == null) {
			return;
		}
		CompoundTag tag = getUpgradeStack().getOrCreateTag();
		CompoundTag anchorTag = new CompoundTag();
		anchorTag.putInt(TAG_SCAN_X, pos.getX());
		anchorTag.putInt(TAG_SCAN_Y, pos.getY());
		anchorTag.putInt(TAG_SCAN_Z, pos.getZ());
		tag.put(TAG_SCAN_ANCHOR, anchorTag);
		save();
		if (NearbyCraftingConfig.SERVER.debugLogging.get()) {
			NearbyCrafting.LOGGER.info("[NC-ADV-UPGRADE] set scan anchor to {}", pos);
		}
	}

	public BlockPos getScanAnchorOrPlayerPos(Player player) {
		CompoundTag root = getUpgradeStack().getTag();
		if (root != null && root.contains(TAG_SCAN_ANCHOR, Tag.TAG_COMPOUND)) {
			CompoundTag anchorTag = root.getCompound(TAG_SCAN_ANCHOR);
			return new BlockPos(
					anchorTag.getInt(TAG_SCAN_X),
					anchorTag.getInt(TAG_SCAN_Y),
					anchorTag.getInt(TAG_SCAN_Z)
			);
		}
		return player.blockPosition();
	}

	public IItemHandler getHostBackpackInventory() {
		return storageWrapper.getInventoryHandler();
	}

	private static boolean extractExactFromSources(List<ItemSourceRef> sources, ItemStack toExtract) {
		int remaining = toExtract.getCount();
		for (ItemSourceRef sourceRef : sources) {
			if (remaining <= 0) {
				return true;
			}

			ItemStack sourceStack = sourceRef.handler().getStackInSlot(sourceRef.slot());
			if (sourceStack.isEmpty() || !ItemStack.isSameItemSameTags(sourceStack, toExtract)) {
				continue;
			}

			int requested = Math.min(remaining, sourceStack.getCount());
			if (requested <= 0) {
				continue;
			}

			ItemStack simulated = sourceRef.handler().extractItem(sourceRef.slot(), requested, true);
			if (simulated.isEmpty() || !ItemStack.isSameItemSameTags(simulated, toExtract)) {
				continue;
			}

			ItemStack extracted = sourceRef.handler().extractItem(sourceRef.slot(), simulated.getCount(), false);
			if (extracted.isEmpty() || !ItemStack.isSameItemSameTags(extracted, toExtract)) {
				continue;
			}

			remaining -= extracted.getCount();
		}

		return remaining <= 0;
	}

	private static int countMatchingItems(List<ItemSourceRef> sources, ItemStack toExtract) {
		int count = 0;
		for (ItemSourceRef source : sources) {
			ItemStack sourceStack = source.handler().getStackInSlot(source.slot());
			if (!sourceStack.isEmpty() && ItemStack.isSameItemSameTags(sourceStack, toExtract)) {
				count += sourceStack.getCount();
			}
		}
		return count;
	}
}
