package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.compat.sophisticatedbackpacks.SophisticatedBackpacksSourceCollector;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.service.scan.ScanOptions;
import dev.maeiro.proximitycrafting.service.scan.SourceCollector;
import dev.maeiro.proximitycrafting.service.source.ForgeItemSourceSlot;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
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

public class ProximityInventoryScanner implements SourceCollector {
	private static final String SOPHISTICATED_BACKPACKS_MOD_ID = "sophisticatedbackpacks";
	public static final ProximityInventoryScanner INSTANCE = new ProximityInventoryScanner();

	private ProximityInventoryScanner() {
	}

	public static List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player) {
		return collectSources(
				level,
				centerPos,
				player,
				true,
				SourcePriority.CONTAINERS_FIRST
		);
	}

	public static List<ItemSourceRef> collectSources(
			Level level,
			BlockPos centerPos,
			Player player,
			boolean includePlayerInventory,
			SourcePriority priority
	) {
		return collectSourcesWithOptions(level, centerPos, player, defaultScanOptions(includePlayerInventory, priority));
	}

	public static List<ItemSourceRef> collectSourcesWithOptions(
			Level level,
			BlockPos centerPos,
			Player player,
			ScanOptions scanOptions
	) {
		long startNs = System.nanoTime();
		List<ItemSourceRef> containerSources = collectContainerSources(level, centerPos, scanOptions);
		List<ItemSourceRef> playerInventorySources = collectPlayerInventorySources(player, scanOptions.includePlayerInventory());
		List<ItemSourceRef> backpackSources = collectBackpackSources(player, scanOptions.includePlayerInventory());
		List<ItemSourceRef> playerSources = new ArrayList<>(playerInventorySources.size() + backpackSources.size());
		playerSources.addAll(playerInventorySources);
		playerSources.addAll(backpackSources);

		List<ItemSourceRef> result = new ArrayList<>(containerSources.size() + playerSources.size());
		if (scanOptions.sourcePriority() == SourcePriority.PLAYER_FIRST) {
			result.addAll(playerSources);
			result.addAll(containerSources);
		} else {
			result.addAll(containerSources);
			result.addAll(playerSources);
		}

		if (ProximityCraftingConfig.SERVER.debugLogging.get()) {
			double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] collectSources center={} includePlayer={} priority={} containerSlots={} playerSlots={} backpackSlots={} totalSlots={} took={}ms",
					centerPos,
					scanOptions.includePlayerInventory(),
					scanOptions.sourcePriority(),
					containerSources.size(),
					playerInventorySources.size(),
					backpackSources.size(),
					result.size(),
					String.format("%.3f", totalMs)
			);
		}

		return result;
	}

	@Override
	public List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions) {
		return ProximityInventoryScanner.collectSourcesWithOptions(level, centerPos, player, scanOptions);
	}

	public static List<ItemSourceRef> collectContainerSources(Level level, BlockPos centerPos) {
		return collectContainerSources(level, centerPos, defaultScanOptions(true, SourcePriority.CONTAINERS_FIRST));
	}

	public static List<ItemSourceRef> collectContainerSources(Level level, BlockPos centerPos, ScanOptions scanOptions) {
		long startNs = System.nanoTime();
		int scanRadius = scanOptions.scanRadius();
		int minSlotCount = scanOptions.minSlotCount();
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

		if (ProximityCraftingConfig.SERVER.debugLogging.get()) {
			int diameter = scanRadius * 2 + 1;
			long scannedPositions = (long) diameter * diameter * diameter;
			double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] collectContainerSources center={} radius={} scannedPositions={} minSlots={} acceptedHandlers={} containerSlots={} took={}ms",
					centerPos,
					scanRadius,
					scannedPositions,
					minSlotCount,
					blockEntities.size(),
					sources.size(),
					String.format("%.3f", totalMs)
			);
		}
		return sources;
	}

	public static List<ItemSourceRef> collectPlayerSources(Player player, boolean includePlayerInventory) {
		return collectPlayerSources(
				player,
				new ScanOptions(
						ProximityCraftingConfig.SERVER.scanRadius.get(),
						ProximityCraftingConfig.SERVER.minSlotCount.get(),
						includePlayerInventory,
						SourcePriority.CONTAINERS_FIRST
				)
		);
	}

	public static List<ItemSourceRef> collectPlayerSources(Player player, ScanOptions scanOptions) {
		List<ItemSourceRef> sources = new ArrayList<>();
		sources.addAll(collectPlayerInventorySources(player, scanOptions.includePlayerInventory()));
		sources.addAll(collectBackpackSources(player, scanOptions.includePlayerInventory()));
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
			sources.add(new ItemSourceRef(new ForgeItemSourceSlot(playerInventory, slot), ItemSourceRef.SourceType.PLAYER, null));
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
			sink.add(new ItemSourceRef(new ForgeItemSourceSlot(handler, slot), sourceType, blockPos));
		}
	}

	private static ScanOptions defaultScanOptions(boolean includePlayerInventory, SourcePriority priority) {
		return new ScanOptions(
				ProximityCraftingConfig.SERVER.scanRadius.get(),
				ProximityCraftingConfig.SERVER.minSlotCount.get(),
				includePlayerInventory,
				priority
		);
	}
}


