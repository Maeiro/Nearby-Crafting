package dev.maeiro.nearbycrafting.service.crafting;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
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
		Optional<ExtractionPlan> firstPlanOptional = pool.plan(targetGrid);

		if (firstPlanOptional.isEmpty()) {
			return FillResult.failure("nearbycrafting.feedback.not_enough_ingredients");
		}

		ExtractionCommitResult firstCommit = firstPlanOptional.get().commit();
		if (firstCommit == null) {
			return FillResult.failure("nearbycrafting.feedback.fill_failed");
		}

		menu.clearCraftGridToPlayerOrDrop();
		if (!applyCommitAsSet(menu, firstCommit)) {
			rollbackCommit(firstCommit);
			return FillResult.failure("nearbycrafting.feedback.fill_failed");
		}

		int loadedCrafts = 1;
		if (craftAll) {
			loadedCrafts += fillAdditionalCrafts(menu, pool, targetGrid);
		}

		menu.setLastPlacedRecipe(recipe);
		menu.slotsChanged(menu.getCraftSlots());
		menu.broadcastChanges();

		if (NearbyCrafting.LOGGER.isDebugEnabled()) {
			NearbyCrafting.LOGGER.debug("Filled nearby crafting grid for recipe {}", recipe.getClass().getSimpleName());
		}

		if (craftAll) {
			return FillResult.success("nearbycrafting.feedback.filled_max", loadedCrafts);
		}

		return FillResult.success("nearbycrafting.feedback.filled", 0);
	}

	private static int fillAdditionalCrafts(NearbyCraftingMenu menu, IngredientSourcePool pool, List<Ingredient> targetGrid) {
		int additionalCrafts = 0;
		int maxIterations = NearbyCraftingConfig.SERVER.maxShiftCraftIterations.get();
		for (int iteration = 1; iteration < maxIterations; iteration++) {
			if (!hasRoomForAnotherCraft(menu, targetGrid)) {
				break;
			}

			List<ItemStack> exactTemplate = buildExactTemplate(menu, targetGrid);
			Optional<ExtractionPlan> planOptional = pool.planExactStacks(exactTemplate);
			if (planOptional.isEmpty()) {
				break;
			}

			ExtractionCommitResult commitResult = planOptional.get().commit();
			if (commitResult == null) {
				break;
			}
			if (!applyCommitAsAdd(menu, commitResult)) {
				rollbackCommit(commitResult);
				break;
			}
			additionalCrafts++;
		}
		return additionalCrafts;
	}

	private static boolean applyCommitAsSet(NearbyCraftingMenu menu, ExtractionCommitResult commitResult) {
		ItemStack[] extractedStacks = commitResult.extractedStacks();
		ItemSourceRef[] sourceRefs = commitResult.sourceRefs();
		for (int slot = 0; slot < 9; slot++) {
			menu.setCraftSlotFromSource(slot, extractedStacks[slot], sourceRefs[slot]);
		}
		return true;
	}

	private static boolean applyCommitAsAdd(NearbyCraftingMenu menu, ExtractionCommitResult commitResult) {
		ItemStack[] extractedStacks = commitResult.extractedStacks();
		ItemSourceRef[] sourceRefs = commitResult.sourceRefs();

		for (int slot = 0; slot < 9; slot++) {
			ItemStack extracted = extractedStacks[slot];
			if (!menu.canAcceptCraftSlotStack(slot, extracted)) {
				return false;
			}
		}

		for (int slot = 0; slot < 9; slot++) {
			ItemStack extracted = extractedStacks[slot];
			if (extracted.isEmpty()) {
				continue;
			}
			if (!menu.addCraftSlotFromSource(slot, extracted, sourceRefs[slot])) {
				return false;
			}
		}
		return true;
	}

	private static void rollbackCommit(ExtractionCommitResult commitResult) {
		ItemStack[] extractedStacks = commitResult.extractedStacks();
		ItemSourceRef[] sourceRefs = commitResult.sourceRefs();

		for (int slot = extractedStacks.length - 1; slot >= 0; slot--) {
			ItemStack extracted = extractedStacks[slot];
			ItemSourceRef sourceRef = sourceRefs[slot];
			if (extracted.isEmpty() || sourceRef == null) {
				continue;
			}

			ItemStack notInserted = sourceRef.handler().insertItem(sourceRef.slot(), extracted.copy(), false);
			if (!notInserted.isEmpty()) {
				NearbyCrafting.LOGGER.warn(
						"Could not fully rollback extracted stack {} for source {}:{}",
						notInserted,
						sourceRef.sourceType(),
						sourceRef.slot()
				);
			}
		}
	}

	private static boolean hasRoomForAnotherCraft(NearbyCraftingMenu menu, List<Ingredient> targetGrid) {
		for (int slot = 0; slot < 9; slot++) {
			if (targetGrid.get(slot).isEmpty()) {
				continue;
			}

			ItemStack current = menu.getCraftSlots().getItem(slot);
			if (current.isEmpty()) {
				return false;
			}

			int slotLimit = Math.min(menu.getCraftSlots().getMaxStackSize(), current.getMaxStackSize());
			if (current.getCount() >= slotLimit) {
				return false;
			}
		}
		return true;
	}

	private static List<ItemStack> buildExactTemplate(NearbyCraftingMenu menu, List<Ingredient> targetGrid) {
		List<ItemStack> template = new ArrayList<>(9);
		for (int slot = 0; slot < 9; slot++) {
			if (targetGrid.get(slot).isEmpty()) {
				template.add(ItemStack.EMPTY);
				continue;
			}

			ItemStack stack = menu.getCraftSlots().getItem(slot);
			if (stack.isEmpty()) {
				template.add(ItemStack.EMPTY);
				continue;
			}

			ItemStack oneItem = stack.copy();
			oneItem.setCount(1);
			template.add(oneItem);
		}
		return template;
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
