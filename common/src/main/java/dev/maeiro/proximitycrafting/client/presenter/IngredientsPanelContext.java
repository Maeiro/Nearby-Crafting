package dev.maeiro.proximitycrafting.client.presenter;

import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.util.List;
import java.util.Optional;

public interface IngredientsPanelContext {
	List<RecipeBookSourceEntry> getCurrentSources();

	Optional<CraftingRecipe> resolvePreferredRecipe();

	int getCraftGridSize();

	ItemStack getCraftGridItem(int slot);
}
