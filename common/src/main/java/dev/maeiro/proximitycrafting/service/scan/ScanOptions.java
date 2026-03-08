package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.SourcePriority;

public record ScanOptions(
		int scanRadius,
		int minSlotCount,
		boolean includePlayerInventory,
		SourcePriority sourcePriority
) {
}
