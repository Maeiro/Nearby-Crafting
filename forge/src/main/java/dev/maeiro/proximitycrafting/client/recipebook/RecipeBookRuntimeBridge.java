package dev.maeiro.proximitycrafting.client.recipebook;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

public interface RecipeBookRuntimeBridge {
	@Nullable
	ResourceLocation resolveHoveredRecipeId(RecipeBookComponent component);

	void onSlotClicked(RecipeBookComponent component, Slot slot);

	void onRecipesUpdated(RecipeBookComponent component);
}
