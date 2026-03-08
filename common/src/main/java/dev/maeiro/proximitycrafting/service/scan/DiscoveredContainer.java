package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ItemSourceSlot;
import net.minecraft.core.BlockPos;

import java.util.List;

public record DiscoveredContainer(
		String typeId,
		BlockPos blockPos,
		List<ItemSourceSlot> slots
) {
	public DiscoveredContainer {
		slots = List.copyOf(slots);
	}
}
