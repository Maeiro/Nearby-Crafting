package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.ItemSourceSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class NearbyContainerSourceCollector implements ContainerSourceCollector {
	private final ContainerDiscoveryPort discoveryPort;

	public NearbyContainerSourceCollector(ContainerDiscoveryPort discoveryPort) {
		this.discoveryPort = discoveryPort;
	}

	@Override
	public List<ItemSourceRef> collectContainerSources(Level level, BlockPos centerPos, ScanOptions scanOptions) {
		int scanRadius = scanOptions.scanRadius();
		int minX = centerPos.getX() - scanRadius;
		int maxX = centerPos.getX() + scanRadius;
		int minY = centerPos.getY() - scanRadius;
		int maxY = centerPos.getY() + scanRadius;
		int minZ = centerPos.getZ() - scanRadius;
		int maxZ = centerPos.getZ() + scanRadius;

		List<DiscoveredContainer> discoveredContainers = BlockPos.betweenClosedStream(minX, minY, minZ, maxX, maxY, maxZ)
				.map(pos -> discoveryPort.discoverContainer(level, pos.immutable()))
				.flatMap(Optional::stream)
				.filter(container -> !scanOptions.blacklistedContainerTypeIds().contains(container.typeId()))
				.filter(container -> container.slots().size() >= scanOptions.minSlotCount())
				.sorted(Comparator.comparingDouble(container -> container.blockPos().distSqr(centerPos)))
				.toList();

		List<ItemSourceRef> sources = new ArrayList<>();
		for (DiscoveredContainer container : discoveredContainers) {
			for (ItemSourceSlot slot : container.slots()) {
				sources.add(new ItemSourceRef(slot, ItemSourceRef.SourceType.CONTAINER, container.blockPos()));
			}
		}
		return sources;
	}
}
