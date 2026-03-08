package dev.maeiro.proximitycrafting.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProximityConfigCodec {
	private static final String SECTION_HEADER = "[proximityCrafting]";

	private ProximityConfigCodec() {
	}

	public static LoadedClientConfig decodeClient(Map<String, String> values) {
		Map<String, String> resolvedValues = values == null ? Map.of() : values;
		return new LoadedClientConfig(
				ClientPreferences.fromConfigValues(
						parseBoolean(resolvedValues, "autoRefillAfterCraft", ProximityConfigDefaults.CLIENT_AUTO_REFILL_AFTER_CRAFT),
						parseBoolean(resolvedValues, "includePlayerInventory", ProximityConfigDefaults.CLIENT_INCLUDE_PLAYER_INVENTORY),
						parseString(resolvedValues, "sourcePriority", ProximityConfigDefaults.CLIENT_SOURCE_PRIORITY.name())
				),
				new ClientUiState(
						parseBoolean(resolvedValues, "rememberToggleStates", ProximityConfigDefaults.CLIENT_REMEMBER_TOGGLE_STATES),
						parseBoolean(resolvedValues, "proximityItemsPanelOpen", ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OPEN),
						parseInt(resolvedValues, "proximityItemsPanelOffsetX", ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OFFSET_X),
						parseInt(resolvedValues, "proximityItemsPanelOffsetY", ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OFFSET_Y),
						parseBoolean(resolvedValues, "debugLogging", ProximityConfigDefaults.CLIENT_DEBUG_LOGGING)
				)
		);
	}

	public static LoadedServerConfig decodeServer(Map<String, String> values) {
		Map<String, String> resolvedValues = values == null ? Map.of() : values;
		return new LoadedServerConfig(ServerRuntimeSettings.of(
				parseInt(resolvedValues, "scanRadius", ProximityConfigDefaults.SERVER_SCAN_RADIUS),
				parseInt(resolvedValues, "minSlotCount", ProximityConfigDefaults.SERVER_MIN_SLOT_COUNT),
				parseInt(resolvedValues, "maxShiftCraftIterations", ProximityConfigDefaults.SERVER_MAX_SHIFT_CRAFT_ITERATIONS),
				parseBoolean(resolvedValues, "debugLogging", ProximityConfigDefaults.SERVER_DEBUG_LOGGING),
				parseStringList(resolvedValues, "blockEntityBlacklist", ProximityConfigDefaults.SERVER_BLOCK_ENTITY_BLACKLIST)
		));
	}

	public static String encodeClient(ClientPreferences preferences, ClientUiState uiState) {
		ClientPreferences resolvedPreferences = preferences == null ? ClientPreferences.defaults() : preferences;
		ClientUiState resolvedUiState = uiState == null ? ClientUiState.defaults() : uiState;
		return SECTION_HEADER + System.lineSeparator()
				+ "autoRefillAfterCraft = " + resolvedPreferences.autoRefillAfterCraft() + System.lineSeparator()
				+ "includePlayerInventory = " + resolvedPreferences.includePlayerInventory() + System.lineSeparator()
				+ "sourcePriority = \"" + resolvedPreferences.sourcePriorityValue() + "\"" + System.lineSeparator()
				+ "rememberToggleStates = " + resolvedUiState.rememberToggleStates() + System.lineSeparator()
				+ "proximityItemsPanelOpen = " + resolvedUiState.ingredientsPanelOpen() + System.lineSeparator()
				+ "proximityItemsPanelOffsetX = " + resolvedUiState.ingredientsPanelOffsetX() + System.lineSeparator()
				+ "proximityItemsPanelOffsetY = " + resolvedUiState.ingredientsPanelOffsetY() + System.lineSeparator()
				+ "debugLogging = " + resolvedUiState.debugLogging() + System.lineSeparator();
	}

	public static String encodeServer(ServerRuntimeSettings settings) {
		ServerRuntimeSettings resolvedSettings = settings == null ? ServerRuntimeSettings.defaults() : settings;
		return SECTION_HEADER + System.lineSeparator()
				+ "scanRadius = " + resolvedSettings.scanRadius() + System.lineSeparator()
				+ "minSlotCount = " + resolvedSettings.minSlotCount() + System.lineSeparator()
				+ "blockEntityBlacklist = " + formatStringList(resolvedSettings.blacklistedContainerTypeIds()) + System.lineSeparator()
				+ "maxShiftCraftIterations = " + resolvedSettings.maxShiftCraftIterations() + System.lineSeparator()
				+ "debugLogging = " + resolvedSettings.debugLogging() + System.lineSeparator();
	}

	public static String defaultClientToml() {
		return SECTION_HEADER + System.lineSeparator()
				+ "autoRefillAfterCraft = " + ProximityConfigDefaults.CLIENT_AUTO_REFILL_AFTER_CRAFT + System.lineSeparator()
				+ "includePlayerInventory = " + ProximityConfigDefaults.CLIENT_INCLUDE_PLAYER_INVENTORY + System.lineSeparator()
				+ "sourcePriority = \"" + ProximityConfigDefaults.CLIENT_SOURCE_PRIORITY.name() + "\"" + System.lineSeparator()
				+ "rememberToggleStates = " + ProximityConfigDefaults.CLIENT_REMEMBER_TOGGLE_STATES + System.lineSeparator()
				+ "proximityItemsPanelOpen = " + ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OPEN + System.lineSeparator()
				+ "proximityItemsPanelOffsetX = " + ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OFFSET_X + System.lineSeparator()
				+ "proximityItemsPanelOffsetY = " + ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OFFSET_Y + System.lineSeparator()
				+ "debugLogging = " + ProximityConfigDefaults.CLIENT_DEBUG_LOGGING + System.lineSeparator();
	}

	public static String defaultServerToml() {
		return SECTION_HEADER + System.lineSeparator()
				+ "scanRadius = " + ProximityConfigDefaults.SERVER_SCAN_RADIUS + System.lineSeparator()
				+ "minSlotCount = " + ProximityConfigDefaults.SERVER_MIN_SLOT_COUNT + System.lineSeparator()
				+ "blockEntityBlacklist = " + formatStringList(ProximityConfigDefaults.SERVER_BLOCK_ENTITY_BLACKLIST) + System.lineSeparator()
				+ "maxShiftCraftIterations = " + ProximityConfigDefaults.SERVER_MAX_SHIFT_CRAFT_ITERATIONS + System.lineSeparator()
				+ "debugLogging = " + ProximityConfigDefaults.SERVER_DEBUG_LOGGING + System.lineSeparator();
	}

	public static Map<String, String> parseKeyValues(List<String> lines) {
		Map<String, String> values = new LinkedHashMap<>();
		if (lines == null) {
			return values;
		}
		for (String rawLine : lines) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith("[") || !line.contains("=")) {
				continue;
			}
			int separator = line.indexOf('=');
			String key = line.substring(0, separator).trim();
			String value = line.substring(separator + 1).trim();
			values.put(key, value);
		}
		return values;
	}

	private static boolean parseBoolean(Map<String, String> values, String key, boolean fallback) {
		String value = values.get(key);
		return value == null ? fallback : Boolean.parseBoolean(stripQuotes(value));
	}

	private static int parseInt(Map<String, String> values, String key, int fallback) {
		String value = values.get(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(stripQuotes(value));
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static String parseString(Map<String, String> values, String key, String fallback) {
		String value = values.get(key);
		return value == null ? fallback : stripQuotes(value);
	}

	private static List<String> parseStringList(Map<String, String> values, String key, List<String> fallback) {
		String value = values.get(key);
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
			return fallback;
		}
		String body = trimmed.substring(1, trimmed.length() - 1).trim();
		if (body.isEmpty()) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (String entry : body.split(",")) {
			String cleaned = stripQuotes(entry.trim());
			if (!cleaned.isEmpty()) {
				result.add(cleaned);
			}
		}
		return result;
	}

	private static String stripQuotes(String value) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static String formatStringList(List<String> values) {
		if (values == null || values.isEmpty()) {
			return "[]";
		}
		StringBuilder builder = new StringBuilder("[");
		for (int index = 0; index < values.size(); index++) {
			if (index > 0) {
				builder.append(", ");
			}
			builder.append('"').append(values.get(index)).append('"');
		}
		builder.append(']');
		return builder.toString();
	}
}
