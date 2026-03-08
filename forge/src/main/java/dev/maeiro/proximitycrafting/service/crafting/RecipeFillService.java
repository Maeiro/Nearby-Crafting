package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.service.scan.ForgeScanOptionsFactory;
import dev.maeiro.proximitycrafting.service.scan.ProximityInventoryScanner;
import dev.maeiro.proximitycrafting.service.scan.ScanOptions;
import dev.maeiro.proximitycrafting.service.scan.SourceCollector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public final class RecipeFillService {
	private static final SourceCollector SOURCE_COLLECTOR = ProximityInventoryScanner.INSTANCE;

	private RecipeFillService() {
	}

	public static FillResult fillFromRecipe(ProximityCraftingMenu menu, CraftingRecipe recipe, boolean craftAll) {
		return RecipeFillOperations.fillFromRecipe(
				adapt(menu),
				SOURCE_COLLECTOR,
				scanOptions(menu),
				recipe,
				craftAll
		);
	}

	public static FillResult refillLastRecipe(ProximityCraftingMenu menu) {
		return RecipeFillOperations.refillLastRecipe(
				adapt(menu),
				SOURCE_COLLECTOR,
				scanOptions(menu)
		);
	}

	public static FillResult fillRecipeById(ProximityCraftingMenu menu, ResourceLocation recipeId, boolean craftAll) {
		return RecipeSessionOperations.fillRecipeById(
				adapt(menu),
				menu.getCraftSlots(),
				SOURCE_COLLECTOR,
				scanOptions(menu),
				recipeId,
				craftAll
		);
	}

	public static FillResult addSingleCraft(ProximityCraftingMenu menu, CraftingRecipe recipe) {
		return RecipeFillOperations.addSingleCraft(
				adapt(menu),
				SOURCE_COLLECTOR,
				scanOptions(menu),
				recipe
		);
	}

	public static FillResult addCrafts(ProximityCraftingMenu menu, CraftingRecipe recipe, int requestedCrafts) {
		return RecipeFillOperations.addCrafts(
				adapt(menu),
				SOURCE_COLLECTOR,
				scanOptions(menu),
				recipe,
				requestedCrafts
		);
	}

	public static FillResult adjustRecipeLoad(ProximityCraftingMenu menu, int steps) {
		return RecipeSessionOperations.adjustRecipeLoad(
				adapt(menu),
				menu.getCraftSlots(),
				SOURCE_COLLECTOR,
				scanOptions(menu),
				steps
		);
	}

	public static FillResult removeSingleCraft(ProximityCraftingMenu menu, CraftingRecipe recipe) {
		return RecipeFillOperations.removeSingleCraft(adapt(menu), recipe);
	}

	public static FillResult removeCrafts(ProximityCraftingMenu menu, CraftingRecipe recipe, int requestedCrafts) {
		return RecipeFillOperations.removeCrafts(adapt(menu), recipe, requestedCrafts);
	}

	public static List<Ingredient> buildTargetGrid(CraftingRecipe recipe) {
		return RecipeFillOperations.buildTargetGrid(recipe);
	}

	private static CraftingSessionPort adapt(ProximityCraftingMenu menu) {
		return new ForgeCraftingSessionAdapter(menu);
	}

	private static ScanOptions scanOptions(ProximityCraftingMenu menu) {
		return ForgeScanOptionsFactory.fromMenu(menu);
	}
}
