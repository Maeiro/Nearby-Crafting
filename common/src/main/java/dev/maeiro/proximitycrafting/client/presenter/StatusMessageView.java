package dev.maeiro.proximitycrafting.client.presenter;

import net.minecraft.network.chat.Component;

public record StatusMessageView(
		Component message,
		int color,
		long expiresAtMs
) {
}
