package dev.maeiro.proximitycrafting.client.net;

import net.minecraft.resources.ResourceLocation;

public interface ClientRequestSender {
	void requestRecipeFill(ResourceLocation recipeId, boolean craftAll);

	void adjustRecipeLoad(int steps);

	void requestRecipeBookSources(int containerId);

	void clearCraftGrid(int containerId);

	void updateClientPreferences(int containerId, boolean autoRefillAfterCraft, boolean includePlayerInventory, String sourcePriority);
}
