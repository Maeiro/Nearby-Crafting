package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.service.scan.CompositeSourceCollector;
import dev.maeiro.proximitycrafting.service.scan.ContainerDiscoveryPort;
import dev.maeiro.proximitycrafting.service.scan.ContainerSourceCollector;
import dev.maeiro.proximitycrafting.service.scan.NearbyContainerSourceCollector;
import dev.maeiro.proximitycrafting.service.scan.SourceCollectionResult;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class ProximityInventoryScanner implements SourceCollector {
	private static final ContainerDiscoveryPort CONTAINER_DISCOVERY_PORT = new ForgeContainerDiscoveryPort();
	private static final ContainerSourceCollector CONTAINER_SOURCE_COLLECTOR =
			new NearbyContainerSourceCollector(CONTAINER_DISCOVERY_PORT);
	private static final ForgePlayerInventorySourceCollector PLAYER_INVENTORY_SOURCE_COLLECTOR = new ForgePlayerInventorySourceCollector();
	private static final ForgeBackpackSourceCollector BACKPACK_SOURCE_COLLECTOR = new ForgeBackpackSourceCollector();
	private static final CompositeSourceCollector DELEGATE = new CompositeSourceCollector(
			CONTAINER_SOURCE_COLLECTOR,
			PLAYER_INVENTORY_SOURCE_COLLECTOR,
			BACKPACK_SOURCE_COLLECTOR
	);
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
		SourceCollectionResult sourceCollectionResult = collectSourceResult(level, centerPos, player, scanOptions);
		List<ItemSourceRef> containerSources = sourceCollectionResult.containerSources();
		List<ItemSourceRef> playerInventorySources = sourceCollectionResult.playerInventorySources();
		List<ItemSourceRef> backpackSources = sourceCollectionResult.playerBackpackSources();
		List<ItemSourceRef> result = sourceCollectionResult.mergedSources(scanOptions.sourcePriority());

		if (ProximityCraftingConfig.serverRuntimeSettings().debugLogging()) {
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

	public static SourceCollectionResult collectSourceResult(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions) {
		return DELEGATE.collectResult(level, centerPos, player, scanOptions);
	}

	public static List<ItemSourceRef> collectContainerSources(Level level, BlockPos centerPos, ScanOptions scanOptions) {
		long startNs = System.nanoTime();
		List<ItemSourceRef> sources = CONTAINER_SOURCE_COLLECTOR.collectContainerSources(level, centerPos, scanOptions);
		if (ProximityCraftingConfig.serverRuntimeSettings().debugLogging()) {
			int scanRadius = scanOptions.scanRadius();
			int diameter = scanRadius * 2 + 1;
			long scannedPositions = (long) diameter * diameter * diameter;
			double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] collectContainerSources center={} radius={} scannedPositions={} minSlots={} containerSlots={} took={}ms",
					centerPos,
					scanRadius,
					scannedPositions,
					scanOptions.minSlotCount(),
					sources.size(),
					String.format("%.3f", totalMs)
			);
		}
		return sources;
	}

	public static List<ItemSourceRef> collectPlayerSources(Player player, boolean includePlayerInventory) {
		return collectPlayerSources(player, defaultScanOptions(includePlayerInventory, SourcePriority.CONTAINERS_FIRST));
	}

	public static List<ItemSourceRef> collectPlayerSources(Player player, ScanOptions scanOptions) {
		SourceCollectionResult result = DELEGATE.collectResult(player.level(), player.blockPosition(), player, scanOptions);
		List<ItemSourceRef> sources = new ArrayList<>(result.playerInventorySources().size() + result.playerBackpackSources().size());
		sources.addAll(result.playerInventorySources());
		sources.addAll(result.playerBackpackSources());
		return sources;
	}

	private static ScanOptions defaultScanOptions(boolean includePlayerInventory, SourcePriority priority) {
		return ProximityCraftingConfig.serverRuntimeSettings().scanOptions(includePlayerInventory, priority);
	}
}


