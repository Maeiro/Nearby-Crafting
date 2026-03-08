package dev.maeiro.proximitycrafting.networking.request;

import dev.maeiro.proximitycrafting.config.ClientPreferences;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import dev.maeiro.proximitycrafting.service.crafting.MenuRuntimeHost;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface ServerMenuRequestHost extends MenuRuntimeHost {
	FillResult fillRecipeById(ResourceLocation recipeId, boolean craftAll);

	FillResult adjustRecipeLoad(int steps);

	boolean shouldSendSnapshotForAdjust();

	List<RecipeBookSourceEntry> getServerRecipeBookSnapshot(boolean preferCache, String reason);

	boolean hasAnyCraftGridItems();

	void clearCraftGridToPlayerOrDrop();

	void invalidateServerRecipeBookSnapshotCache();

	void setClientPreferences(ClientPreferences preferences);
}
