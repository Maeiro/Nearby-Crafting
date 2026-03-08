package dev.maeiro.proximitycrafting.config;

public record ClientUiState(
		boolean rememberToggleStates,
		boolean ingredientsPanelOpen,
		int ingredientsPanelOffsetX,
		int ingredientsPanelOffsetY,
		boolean jeiCraftableOnlyEnabled,
		boolean emiCraftableOnlyEnabled,
		boolean debugLogging
) {
	public static ClientUiState defaults() {
		return new ClientUiState(
				ProximityConfigDefaults.CLIENT_REMEMBER_TOGGLE_STATES,
				ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OPEN,
				ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OFFSET_X,
				ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OFFSET_Y,
				ProximityConfigDefaults.CLIENT_JEI_CRAFTABLE_ONLY_ENABLED,
				ProximityConfigDefaults.CLIENT_EMI_CRAFTABLE_ONLY_ENABLED,
				ProximityConfigDefaults.CLIENT_DEBUG_LOGGING
		);
	}

	public ClientUiState withIngredientsPanelOpen(boolean open) {
		return new ClientUiState(
				rememberToggleStates,
				open,
				ingredientsPanelOffsetX,
				ingredientsPanelOffsetY,
				jeiCraftableOnlyEnabled,
				emiCraftableOnlyEnabled,
				debugLogging
		);
	}

	public ClientUiState withJeiCraftableOnlyEnabled(boolean enabled) {
		return new ClientUiState(
				rememberToggleStates,
				ingredientsPanelOpen,
				ingredientsPanelOffsetX,
				ingredientsPanelOffsetY,
				enabled,
				emiCraftableOnlyEnabled,
				debugLogging
		);
	}

	public ClientUiState withEmiCraftableOnlyEnabled(boolean enabled) {
		return new ClientUiState(
				rememberToggleStates,
				ingredientsPanelOpen,
				ingredientsPanelOffsetX,
				ingredientsPanelOffsetY,
				jeiCraftableOnlyEnabled,
				enabled,
				debugLogging
		);
	}
}
