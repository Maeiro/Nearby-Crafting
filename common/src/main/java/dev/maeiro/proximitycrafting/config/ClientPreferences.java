package dev.maeiro.proximitycrafting.config;

import dev.maeiro.proximitycrafting.service.source.SourcePriority;

public record ClientPreferences(
		boolean autoRefillAfterCraft,
		boolean includePlayerInventory,
		SourcePriority sourcePriority
) {
	public static ClientPreferences defaults() {
		return new ClientPreferences(
				ProximityConfigDefaults.CLIENT_AUTO_REFILL_AFTER_CRAFT,
				ProximityConfigDefaults.CLIENT_INCLUDE_PLAYER_INVENTORY,
				ProximityConfigDefaults.CLIENT_SOURCE_PRIORITY
		);
	}

	public static ClientPreferences of(boolean autoRefillAfterCraft, boolean includePlayerInventory, SourcePriority sourcePriority) {
		return new ClientPreferences(
				autoRefillAfterCraft,
				includePlayerInventory,
				sourcePriority == null ? ProximityConfigDefaults.CLIENT_SOURCE_PRIORITY : sourcePriority
		);
	}

	public static ClientPreferences fromConfigValues(boolean autoRefillAfterCraft, boolean includePlayerInventory, String sourcePriorityValue) {
		return of(autoRefillAfterCraft, includePlayerInventory, SourcePriority.fromConfig(sourcePriorityValue));
	}

	public String sourcePriorityValue() {
		return sourcePriority.name();
	}

	public ClientPreferences withAutoRefillAfterCraft(boolean enabled) {
		return new ClientPreferences(enabled, includePlayerInventory, sourcePriority);
	}
}
