package dev.maeiro.proximitycrafting.config;

import dev.maeiro.proximitycrafting.service.source.SourcePriority;

import java.util.List;

public final class ProximityConfigDefaults {
	public static final int SERVER_SCAN_RADIUS = 6;
	public static final int SERVER_MIN_SLOT_COUNT = 6;
	public static final List<String> SERVER_BLOCK_ENTITY_BLACKLIST = List.of(
			"minecraft:furnace",
			"minecraft:blast_furnace",
			"minecraft:smoker"
	);
	public static final int SERVER_MAX_SHIFT_CRAFT_ITERATIONS = 64;
	public static final boolean SERVER_DEBUG_LOGGING = false;

	public static final boolean CLIENT_AUTO_REFILL_AFTER_CRAFT = true;
	public static final boolean CLIENT_INCLUDE_PLAYER_INVENTORY = true;
	public static final SourcePriority CLIENT_SOURCE_PRIORITY = SourcePriority.CONTAINERS_FIRST;
	public static final boolean CLIENT_REMEMBER_TOGGLE_STATES = true;
	public static final boolean CLIENT_INGREDIENTS_PANEL_OPEN = true;
	public static final int CLIENT_INGREDIENTS_PANEL_OFFSET_X = 0;
	public static final int CLIENT_INGREDIENTS_PANEL_OFFSET_Y = 0;
	public static final boolean CLIENT_JEI_CRAFTABLE_ONLY_ENABLED = false;
	public static final boolean CLIENT_EMI_CRAFTABLE_ONLY_ENABLED = false;
	public static final boolean CLIENT_DEBUG_LOGGING = false;

	private ProximityConfigDefaults() {
	}
}
