package dev.maeiro.proximitycrafting.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ProximityConfigPersistence {
	private ProximityConfigPersistence() {
	}

	static LoadedClientConfig loadClient(Path path) {
		Map<String, String> values = loadKeyValues(path, ProximityConfigCodec.defaultClientToml());
		return ProximityConfigCodec.decodeClient(values);
	}

	static LoadedServerConfig loadServer(Path path) {
		Map<String, String> values = loadKeyValues(path, ProximityConfigCodec.defaultServerToml());
		return ProximityConfigCodec.decodeServer(values);
	}

	static void saveClient(Path path, ClientPreferences preferences, ClientUiState uiState) {
		writeString(path, ProximityConfigCodec.encodeClient(preferences, uiState));
	}

	static void saveServer(Path path, ServerRuntimeSettings settings) {
		writeString(path, ProximityConfigCodec.encodeServer(settings));
	}

	private static Map<String, String> loadKeyValues(Path path, String defaultContent) {
		ensureDefaultFile(path, defaultContent);
		try {
			return ProximityConfigCodec.parseKeyValues(Files.readAllLines(path, StandardCharsets.UTF_8));
		} catch (IOException exception) {
			return new LinkedHashMap<>();
		}
	}

	private static void ensureDefaultFile(Path path, String defaultContent) {
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			if (!Files.exists(path)) {
				Files.writeString(path, defaultContent, StandardCharsets.UTF_8);
			}
		} catch (IOException ignored) {
		}
	}

	private static void writeString(Path path, String content) {
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			Files.writeString(path, content, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}
}
