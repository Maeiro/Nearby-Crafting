package dev.maeiro.nearbycrafting.service.prefs;

import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerPreferenceStore {
	private static final Map<UUID, PreferenceSnapshot> PREFERENCES = new ConcurrentHashMap<>();

	private PlayerPreferenceStore() {
	}

	public static void update(UUID playerId, boolean includePlayerInventory, NearbyCraftingConfig.SourcePriority sourcePriority) {
		PREFERENCES.put(playerId, new PreferenceSnapshot(includePlayerInventory, sourcePriority));
	}

	public static Optional<PreferenceSnapshot> get(Player player) {
		if (player == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(PREFERENCES.get(player.getUUID()));
	}

	public static void clear(Player player) {
		if (player != null) {
			PREFERENCES.remove(player.getUUID());
		}
	}

	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity());
	}

	public record PreferenceSnapshot(boolean includePlayerInventory, NearbyCraftingConfig.SourcePriority sourcePriority) {
	}
}
