package dev.maeiro.proximitycrafting.config;

import dev.maeiro.proximitycrafting.service.scan.ScanOptions;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;

import java.util.List;

public record ServerRuntimeSettings(
		int scanRadius,
		int minSlotCount,
		int maxShiftCraftIterations,
		boolean debugLogging,
		List<String> blacklistedContainerTypeIds
) {
	public ServerRuntimeSettings {
		blacklistedContainerTypeIds = List.copyOf(blacklistedContainerTypeIds);
	}

	public static ServerRuntimeSettings defaults() {
		return new ServerRuntimeSettings(
				ProximityConfigDefaults.SERVER_SCAN_RADIUS,
				ProximityConfigDefaults.SERVER_MIN_SLOT_COUNT,
				ProximityConfigDefaults.SERVER_MAX_SHIFT_CRAFT_ITERATIONS,
				ProximityConfigDefaults.SERVER_DEBUG_LOGGING,
				ProximityConfigDefaults.SERVER_BLOCK_ENTITY_BLACKLIST
		);
	}

	public static ServerRuntimeSettings of(
			int scanRadius,
			int minSlotCount,
			int maxShiftCraftIterations,
			boolean debugLogging,
			List<String> blacklistedContainerTypeIds
	) {
		return new ServerRuntimeSettings(
				Math.max(0, scanRadius),
				Math.max(0, minSlotCount),
				Math.max(1, maxShiftCraftIterations),
				debugLogging,
				blacklistedContainerTypeIds == null ? ProximityConfigDefaults.SERVER_BLOCK_ENTITY_BLACKLIST : blacklistedContainerTypeIds
		);
	}

	public ScanOptions scanOptions(boolean includePlayerInventory, SourcePriority sourcePriority) {
		return new ScanOptions(
				scanRadius,
				minSlotCount,
				includePlayerInventory,
				sourcePriority == null ? ProximityConfigDefaults.CLIENT_SOURCE_PRIORITY : sourcePriority,
				blacklistedContainerTypeIds
		);
	}
}
