package dev.maeiro.nearbycrafting.client.compat;

import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeSourceSnapshotCache {
	private static final Map<Integer, List<NearbyCraftingMenu.RecipeBookSourceEntry>> SNAPSHOTS = new ConcurrentHashMap<>();

	private RecipeSourceSnapshotCache() {
	}

	public static void update(int containerId, List<NearbyCraftingMenu.RecipeBookSourceEntry> entries) {
		SNAPSHOTS.put(containerId, entries == null ? List.of() : List.copyOf(entries));
	}

	public static List<NearbyCraftingMenu.RecipeBookSourceEntry> get(int containerId) {
		return SNAPSHOTS.getOrDefault(containerId, List.of());
	}

	public static void clear(int containerId) {
		SNAPSHOTS.remove(containerId);
	}
}
