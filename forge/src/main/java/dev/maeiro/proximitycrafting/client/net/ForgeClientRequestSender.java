package dev.maeiro.proximitycrafting.client.net;

import dev.maeiro.proximitycrafting.networking.C2SAdjustRecipeLoad;
import dev.maeiro.proximitycrafting.networking.C2SClearCraftGrid;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeBookSources;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.proximitycrafting.networking.C2SUpdateClientPreferences;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import net.minecraft.resources.ResourceLocation;

public final class ForgeClientRequestSender implements ClientRequestSender {
	@Override
	public void requestRecipeFill(ResourceLocation recipeId, boolean craftAll) {
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(recipeId, craftAll));
	}

	@Override
	public void adjustRecipeLoad(int steps) {
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SAdjustRecipeLoad(steps));
	}

	@Override
	public void requestRecipeBookSources(int containerId) {
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeBookSources(containerId));
	}

	@Override
	public void clearCraftGrid(int containerId) {
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SClearCraftGrid(containerId));
	}

	@Override
	public void updateClientPreferences(int containerId, boolean autoRefillAfterCraft, boolean includePlayerInventory, String sourcePriority) {
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SUpdateClientPreferences(
				containerId,
				autoRefillAfterCraft,
				includePlayerInventory,
				sourcePriority
		));
	}
}
