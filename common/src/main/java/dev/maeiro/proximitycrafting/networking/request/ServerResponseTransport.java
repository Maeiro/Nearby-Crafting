package dev.maeiro.proximitycrafting.networking.request;

import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface ServerResponseTransport {
	void sendRecipeFillFeedback(ServerPlayer player, RecipeFillFeedbackPayload payload);

	void sendRecipeBookSourceSnapshot(ServerPlayer player, int containerId, List<RecipeBookSourceEntry> entries);
}
