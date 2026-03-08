package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ServerRuntimeSettings;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class SourceScanRuntime implements SourceCollector {
	private final ContainerSourceCollector containerSourceCollector;
	private final CompositeSourceCollector delegate;

	public SourceScanRuntime(
			ContainerDiscoveryPort containerDiscoveryPort,
			PlayerInventorySourceCollector playerInventorySourceCollector,
			PlayerBackpackSourceCollector playerBackpackSourceCollector
	) {
		this.containerSourceCollector = new NearbyContainerSourceCollector(containerDiscoveryPort);
		this.delegate = new CompositeSourceCollector(
				containerSourceCollector,
				playerInventorySourceCollector,
				playerBackpackSourceCollector
		);
	}

	public List<ItemSourceRef> collectSources(
			Level level,
			BlockPos centerPos,
			Player player,
			ServerRuntimeSettings runtimeSettings,
			boolean includePlayerInventory,
			SourcePriority priority
	) {
		return collectSources(level, centerPos, player, runtimeSettings.scanOptions(includePlayerInventory, priority), runtimeSettings.debugLogging());
	}

	public List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions, boolean debugLogging) {
		long startNs = System.nanoTime();
		SourceCollectionResult sourceCollectionResult = collectSourceResult(level, centerPos, player, scanOptions);
		List<ItemSourceRef> containerSources = sourceCollectionResult.containerSources();
		List<ItemSourceRef> playerInventorySources = sourceCollectionResult.playerInventorySources();
		List<ItemSourceRef> backpackSources = sourceCollectionResult.playerBackpackSources();
		List<ItemSourceRef> result = sourceCollectionResult.mergedSources(scanOptions.sourcePriority());

		if (debugLogging) {
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
		return collectSources(level, centerPos, player, scanOptions, false);
	}

	public SourceCollectionResult collectSourceResult(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions) {
		return delegate.collectResult(level, centerPos, player, scanOptions);
	}

	public List<ItemSourceRef> collectContainerSources(Level level, BlockPos centerPos, ScanOptions scanOptions, boolean debugLogging) {
		long startNs = System.nanoTime();
		List<ItemSourceRef> sources = containerSourceCollector.collectContainerSources(level, centerPos, scanOptions);
		if (debugLogging) {
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

	public List<ItemSourceRef> collectPlayerSources(
			Player player,
			ServerRuntimeSettings runtimeSettings,
			boolean includePlayerInventory,
			SourcePriority priority
	) {
		return collectPlayerSources(player, runtimeSettings.scanOptions(includePlayerInventory, priority));
	}

	public List<ItemSourceRef> collectPlayerSources(Player player, ScanOptions scanOptions) {
		SourceCollectionResult result = delegate.collectResult(player.level(), player.blockPosition(), player, scanOptions);
		List<ItemSourceRef> sources = new ArrayList<>(result.playerInventorySources().size() + result.playerBackpackSources().size());
		sources.addAll(result.playerInventorySources());
		sources.addAll(result.playerBackpackSources());
		return sources;
	}
}
