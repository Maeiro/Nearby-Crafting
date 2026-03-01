package dev.maeiro.nearbycrafting.service.crafting;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.service.scan.NearbyInventoryScanner;
import dev.maeiro.nearbycrafting.service.source.IngredientSourcePool;
import dev.maeiro.nearbycrafting.service.source.ItemSourceRef;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipeFillService {
	private RecipeFillService() {
	}

	public static FillResult fillFromRecipe(NearbyCraftingMenu menu, CraftingRecipe recipe, boolean craftAll) {
		List<Ingredient> targetGrid = buildTargetGrid(recipe);
		List<ItemSourceRef> sources = NearbyInventoryScanner.collectSources(
				menu.getLevel(),
				menu.getTablePos(),
				menu.getPlayer(),
				menu.isIncludePlayerInventory(),
				menu.getSourcePriority()
		);
		IngredientSourcePool pool = new IngredientSourcePool(sources);
		Optional<ExtractionPlan> planOptional = pool.plan(targetGrid);

		if (planOptional.isEmpty()) {
			return FillResult.failure("nearbycrafting.feedback.not_enough_ingredients");
		}

		ExtractionPlan plan = planOptional.get();
		ExtractionCommitResult commitResult = plan.commit();
		if (commitResult == null) {
			return FillResult.failure("nearbycrafting.feedback.fill_failed");
		}
		ItemStack[] extractedStacks = commitResult.extractedStacks();
		ItemSourceRef[] sourceRefs = commitResult.sourceRefs();

		menu.clearCraftGridToPlayerOrDrop();
		for (int slot = 0; slot < 9; slot++) {
			menu.setCraftSlotFromSource(slot, extractedStacks[slot], sourceRefs[slot]);
		}

		menu.setLastPlacedRecipe(recipe);
		menu.slotsChanged(menu.getCraftSlots());
		menu.broadcastChanges();

		if (NearbyCrafting.LOGGER.isDebugEnabled()) {
			NearbyCrafting.LOGGER.debug("Filled nearby crafting grid for recipe {}", recipe.getClass().getSimpleName());
		}

		if (craftAll) {
			int crafted = CraftConsumeService.craftAll(menu, menu.getPlayer());
			return FillResult.success("nearbycrafting.feedback.crafted_all", crafted);
		}

		return FillResult.success("nearbycrafting.feedback.filled", 0);
	}

	public static FillResult refillLastRecipe(NearbyCraftingMenu menu) {
		CraftingRecipe lastRecipe = menu.getLastPlacedRecipe();
		if (lastRecipe == null) {
			return FillResult.failure("nearbycrafting.feedback.no_recipe_selected");
		}

		return fillFromRecipe(menu, lastRecipe, false);
	}

	public static List<Ingredient> buildTargetGrid(CraftingRecipe recipe) {
		List<Ingredient> target = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) {
			target.add(Ingredient.EMPTY);
		}

		if (recipe instanceof ShapedRecipe shapedRecipe) {
			NonNullList<Ingredient> ingredients = shapedRecipe.getIngredients();
			int width = shapedRecipe.getWidth();
			int height = shapedRecipe.getHeight();

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int sourceIndex = x + y * width;
					if (sourceIndex >= ingredients.size()) {
						continue;
					}
					Ingredient ingredient = ingredients.get(sourceIndex);
					if (!ingredient.isEmpty()) {
						target.set(x + y * 3, ingredient);
					}
				}
			}
			return target;
		}

		int targetIndex = 0;
		for (Ingredient ingredient : recipe.getIngredients()) {
			if (ingredient.isEmpty()) {
				continue;
			}
			if (targetIndex >= target.size()) {
				break;
			}
			target.set(targetIndex, ingredient);
			targetIndex++;
		}
		return target;
	}
}
