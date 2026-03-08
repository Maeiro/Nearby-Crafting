package dev.maeiro.proximitycrafting.networking.payload;

public record UpdateClientPreferencesRequestPayload(
		int containerId,
		boolean autoRefillAfterCraft,
		boolean includePlayerInventory,
		String sourcePriority
) {
}
