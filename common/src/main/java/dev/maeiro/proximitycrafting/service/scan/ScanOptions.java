package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.SourcePriority;

import java.util.List;

public record ScanOptions(
		int scanRadius,
		int minSlotCount,
		boolean includePlayerInventory,
		SourcePriority sourcePriority,
		List<String> blacklistedContainerTypeIds
) {
	public ScanOptions {
		blacklistedContainerTypeIds = List.copyOf(blacklistedContainerTypeIds);
	}
}
