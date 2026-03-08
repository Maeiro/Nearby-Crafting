package dev.maeiro.proximitycrafting.config;

public final class ProximityCraftingConfig {
	private static ClientPreferences clientPreferences = ClientPreferences.defaults();
	private static ClientUiState clientUiState = ClientUiState.defaults();
	private static ServerRuntimeSettings serverRuntimeSettings = ServerRuntimeSettings.defaults();

	private ProximityCraftingConfig() {
	}

	public static boolean isServerDebugLoggingEnabled() {
		return serverRuntimeSettings.debugLogging();
	}

	public static boolean isClientDebugLoggingEnabled() {
		return clientUiState.debugLogging();
	}

	public static ServerRuntimeSettings serverRuntimeSettings() {
		return serverRuntimeSettings;
	}

	public static void setServerRuntimeSettings(ServerRuntimeSettings settings) {
		serverRuntimeSettings = settings == null ? ServerRuntimeSettings.defaults() : settings;
	}

	public static ClientPreferences clientPreferences() {
		return clientPreferences;
	}

	public static void setClientPreferences(ClientPreferences preferences) {
		clientPreferences = preferences == null ? ClientPreferences.defaults() : preferences;
	}

	public static ClientUiState clientUiState() {
		return clientUiState;
	}

	public static void setClientUiState(ClientUiState uiState) {
		clientUiState = uiState == null ? ClientUiState.defaults() : uiState;
	}
}
