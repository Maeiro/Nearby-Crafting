package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.compat.sophisticatedbackpacks.SophisticatedBackpacksSourceCollector;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ProximityInventoryScanner {
	private static final String SOPHISTICATED_BACKPACKS_MOD_ID = "sophisticatedbackpacks";

	private ProximityInventoryScanner() {
	}

	public static List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player) {
		return collectSources(
				level,
				centerPos,
				player,
				true,
				ProximityCraftingConfig.SourcePriority.CONTAINERS_FIRST
		);
	}

	public static List<ItemSourceRef> collectSources(
			Level level,
			BlockPos centerPos,
			Player player,
			boolean includePlayerInventory,
			ProximityCraftingConfig.SourcePriority priority
	) {
		List<ItemSourceRef> containerSources = collectContainerSources(level, centerPos);
		List<ItemSourceRef> playerInventorySources = collectPlayerInventorySources(player, includePlayerInventory);
		List<ItemSourceRef> backpackSources = collectBackpackSources(player, includePlayerInventory);
		List<ItemSourceRef> playerSources = new ArrayList<>(playerInventorySources.size() + backpackSources.size());
		playerSources.addAll(playerInventorySources);
		playerSources.addAll(backpackSources);

		List<ItemSourceRef> result = new ArrayList<>(containerSources.size() + playerSources.size());
		if (priority == ProximityCraftingConfig.SourcePriority.PLAYER_FIRST) {
			result.addAll(playerSources);
			result.addAll(containerSources);
		} else {
			result.addAll(containerSources);
			result.addAll(playerSources);
		}

		if (ProximityCraftingConfig.SERVER.debugLogging.get()) {
			ProximityCrafting.LOGGER.info(
					"Collected proximity crafting sources around {} -> containers: {}, player inventory: {}, player backpacks: {}, total: {}",
					centerPos,
					containerSources.size(),
					playerInventorySources.size(),
					backpackSources.size(),
					result.size()
			);
		}

		return result;
	}

	public static List<ItemSourceRef> collectContainerSources(Level level, BlockPos centerPos) {
		int scanRadius = ProximityCraftingConfig.SERVER.scanRadius.get();
		int minSlotCount = ProximityCraftingConfig.SERVER.minSlotCount.get();
		Set<BlockEntityType<?>> blacklist = ProximityCraftingConfig.blockEntityBlacklist == null
				? Set.of()
				: new HashSet<>(ProximityCraftingConfig.blockEntityBlacklist);

		int minX = centerPos.getX() - scanRadius;
		int maxX = centerPos.getX() + scanRadius;
		int minY = centerPos.getY() - scanRadius;
		int maxY = centerPos.getY() + scanRadius;
		int minZ = centerPos.getZ() - scanRadius;
		int maxZ = centerPos.getZ() + scanRadius;

		List<BlockEntity> blockEntities = BlockPos.betweenClosedStream(minX, minY, minZ, maxX, maxY, maxZ)
				.map(level::getBlockEntity)
				.filter(Objects::nonNull)
				.filter(blockEntity -> !blacklist.contains(blockEntity.getType()))
				.filter(blockEntity -> blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
						.filter(handler -> handler.getSlots() >= minSlotCount)
						.isPresent())
				.sorted(Comparator.comparingDouble(blockEntity -> blockEntity.getBlockPos().distSqr(centerPos)))
				.collect(Collectors.toList());

		List<ItemSourceRef> sources = new ArrayList<>();
		for (BlockEntity blockEntity : blockEntities) {
			blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler ->
					addHandlerSlots(sources, handler, ItemSourceRef.SourceType.CONTAINER, blockEntity.getBlockPos())
			);
		}
		return sources;
	}

	public static List<ItemSourceRef> collectPlayerSources(Player player, boolean includePlayerInventory) {
		List<ItemSourceRef> sources = new ArrayList<>();
		sources.addAll(collectPlayerInventorySources(player, includePlayerInventory));
		sources.addAll(collectBackpackSources(player, includePlayerInventory));
		return sources;
	}

	private static List<ItemSourceRef> collectPlayerInventorySources(Player player, boolean includePlayerInventory) {
		if (!includePlayerInventory) {
			return List.of();
		}

		IItemHandler playerInventory = new InvWrapper(player.getInventory());
		List<ItemSourceRef> sources = new ArrayList<>();
		int playerSlots = Math.min(36, playerInventory.getSlots());
		for (int slot = 0; slot < playerSlots; slot++) {
			sources.add(new ItemSourceRef(playerInventory, slot, ItemSourceRef.SourceType.PLAYER, null));
		}
		return sources;
	}

	private static List<ItemSourceRef> collectBackpackSources(Player player, boolean includePlayerInventory) {
		if (!includePlayerInventory || !ModList.get().isLoaded(SOPHISTICATED_BACKPACKS_MOD_ID)) {
			return List.of();
		}
		try {
			return SophisticatedBackpacksSourceCollector.collect(player);
		} catch (LinkageError | RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Failed to collect Sophisticated Backpacks sources; skipping backpack sources", exception);
			return List.of();
		}
	}

	private static void addHandlerSlots(List<ItemSourceRef> sink, IItemHandler handler, ItemSourceRef.SourceType sourceType, BlockPos blockPos) {
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			sink.add(new ItemSourceRef(handler, slot, sourceType, blockPos));
		}
	}
}


