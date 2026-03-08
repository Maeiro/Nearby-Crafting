package dev.maeiro.proximitycrafting.service.source;

import java.util.Locale;

public enum SourcePriority {
	CONTAINERS_FIRST,
	PLAYER_FIRST;

	public static SourcePriority fromConfig(String value) {
		try {
			return SourcePriority.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return CONTAINERS_FIRST;
		}
	}
}
