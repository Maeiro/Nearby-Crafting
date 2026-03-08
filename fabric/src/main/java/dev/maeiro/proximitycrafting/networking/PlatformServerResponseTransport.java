package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;
import dev.maeiro.proximitycrafting.networking.request.ServerResponseTransport;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

final class PlatformServerResponseTransport implements ServerResponseTransport {
	@Override
	public void sendRecipeFillFeedback(ServerPlayer player, RecipeFillFeedbackPayload payload) {
		ProximityCraftingNetwork.CHANNEL.sendToPlayer(
				player,
				new S2CRecipeFillFeedback(payload.success(), payload.messageKey(), payload.craftedAmount())
		);
	}

	@Override
	public void sendRecipeBookSourceSnapshot(ServerPlayer player, int containerId, List<RecipeBookSourceEntry> entries) {
		ProximityCraftingNetwork.CHANNEL.sendToPlayer(
				player,
				new S2CRecipeBookSourceSnapshot(containerId, entries)
		);
	}
}
