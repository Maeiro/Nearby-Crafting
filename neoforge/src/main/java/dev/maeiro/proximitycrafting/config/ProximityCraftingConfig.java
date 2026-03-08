package dev.maeiro.proximitycrafting.config;

import java.nio.file.Path;

public final class ProximityCraftingConfig {
	private static ClientPreferences clientPreferences = ClientPreferences.defaults();
	private static ClientUiState clientUiState = ClientUiState.defaults();
	private static ServerRuntimeSettings serverRuntimeSettings = ServerRuntimeSettings.defaults();
	private static Path clientConfigPath;
	private static Path serverConfigPath;

	private ProximityCraftingConfig() {
	}

	public static void initialize(Path configDirectory) {
		clientConfigPath = configDirectory.resolve("proximitycrafting-client.toml");
		serverConfigPath = configDirectory.resolve("proximitycrafting-server.toml");

		LoadedClientConfig loadedClient = ProximityConfigPersistence.loadClient(clientConfigPath);
		clientPreferences = loadedClient.preferences();
		clientUiState = loadedClient.uiState();

		LoadedServerConfig loadedServer = ProximityConfigPersistence.loadServer(serverConfigPath);
		serverRuntimeSettings = loadedServer.settings();
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
		if (serverConfigPath != null) {
			ProximityConfigPersistence.saveServer(serverConfigPath, serverRuntimeSettings);
		}
	}

	public static ClientPreferences clientPreferences() {
		return clientPreferences;
	}

	public static void setClientPreferences(ClientPreferences preferences) {
		clientPreferences = preferences == null ? ClientPreferences.defaults() : preferences;
		if (clientConfigPath != null) {
			ProximityConfigPersistence.saveClient(clientConfigPath, clientPreferences, clientUiState);
		}
	}

	public static ClientUiState clientUiState() {
		return clientUiState;
	}

	public static void setClientUiState(ClientUiState uiState) {
		clientUiState = uiState == null ? ClientUiState.defaults() : uiState;
		if (clientConfigPath != null) {
			ProximityConfigPersistence.saveClient(clientConfigPath, clientPreferences, clientUiState);
		}
	}
}
