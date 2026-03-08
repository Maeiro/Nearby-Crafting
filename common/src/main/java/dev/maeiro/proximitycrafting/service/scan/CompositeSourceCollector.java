package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public final class CompositeSourceCollector implements SourceCollector {
	private final ContainerSourceCollector containerSourceCollector;
	private final PlayerInventorySourceCollector playerInventorySourceCollector;
	private final PlayerBackpackSourceCollector playerBackpackSourceCollector;

	public CompositeSourceCollector(
			ContainerSourceCollector containerSourceCollector,
			PlayerInventorySourceCollector playerInventorySourceCollector,
			PlayerBackpackSourceCollector playerBackpackSourceCollector
	) {
		this.containerSourceCollector = containerSourceCollector;
		this.playerInventorySourceCollector = playerInventorySourceCollector;
		this.playerBackpackSourceCollector = playerBackpackSourceCollector;
	}

	public SourceCollectionResult collectResult(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions) {
		List<ItemSourceRef> containerSources = containerSourceCollector.collectContainerSources(level, centerPos, scanOptions);
		if (!scanOptions.includePlayerInventory()) {
			return new SourceCollectionResult(containerSources, List.of(), List.of());
		}

		List<ItemSourceRef> playerInventorySources = playerInventorySourceCollector.collectPlayerInventorySources(player, scanOptions);
		List<ItemSourceRef> playerBackpackSources = playerBackpackSourceCollector.collectPlayerBackpackSources(player, scanOptions);
		return new SourceCollectionResult(containerSources, playerInventorySources, playerBackpackSources);
	}

	@Override
	public List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions) {
		return collectResult(level, centerPos, player, scanOptions).mergedSources(scanOptions.sourcePriority());
	}
}
