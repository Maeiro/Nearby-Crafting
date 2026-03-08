package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.service.scan.ScanOptions;
import dev.maeiro.proximitycrafting.service.scan.SourceCollector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Optional;

public final class RecipeSessionOperations {
	private RecipeSessionOperations() {
	}

	public static FillResult fillRecipeById(
			CraftingSessionPort session,
			CraftingContainer craftSlots,
			SourceCollector sourceCollector,
			ScanOptions scanOptions,
			ResourceLocation recipeId,
			boolean craftAll
	) {
		Optional<? extends Recipe<?>> optionalRecipe = session.getLevel().getRecipeManager().byKey(recipeId);
		if (optionalRecipe.isEmpty()) {
			return FillResult.failure("proximitycrafting.feedback.recipe_not_found");
		}

		Recipe<?> recipe = optionalRecipe.get();
		if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
			return FillResult.failure("proximitycrafting.feedback.invalid_recipe_type");
		}

		return RecipeFillOperations.fillFromRecipe(session, sourceCollector, scanOptions, craftingRecipe, craftAll);
	}

	public static FillResult adjustRecipeLoad(
			CraftingSessionPort session,
			CraftingContainer craftSlots,
			SourceCollector sourceCollector,
			ScanOptions scanOptions,
			int steps
	) {
		long startNs = System.nanoTime();
		if (session.isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] menu adjustRecipeLoad steps={} menu={} hasLastRecipe={}",
					steps,
					session.debugContextId(),
					session.getLastPlacedRecipe() != null
			);
		}
		if (steps == 0) {
			return FillResult.success("proximitycrafting.feedback.filled", 0);
		}

		Optional<CraftingRecipe> currentRecipeOptional = CraftingResultOperations.resolvePreferredActiveRecipe(
				session.getLevel(),
				craftSlots,
				session.getLastPlacedRecipe()
		);
		CraftingRecipe activeRecipe = currentRecipeOptional.orElse(session.getLastPlacedRecipe());
		if (activeRecipe == null) {
			if (session.isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[PROXC-SCROLL] menu no active recipe to adjust");
			}
			return FillResult.failure("proximitycrafting.feedback.no_recipe_selected");
		}

		session.setLastPlacedRecipe(activeRecipe);
		int direction = steps > 0 ? 1 : -1;
		int requestedSteps = Math.abs(steps);
		FillResult batchResult = direction > 0
				? RecipeFillOperations.addCrafts(session, sourceCollector, scanOptions, activeRecipe, requestedSteps)
				: RecipeFillOperations.removeCrafts(session, activeRecipe, requestedSteps);
		int appliedSteps = batchResult.success() ? batchResult.craftedAmount() : 0;

		if (appliedSteps > 0) {
			String messageKey = direction > 0
					? "proximitycrafting.feedback.scroll_increase"
					: "proximitycrafting.feedback.scroll_decrease";
			if (session.isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] menu.adjustRecipeLoad.success menu={} steps={} applied={} recipe={} took={}ms",
						session.debugContextId(),
						steps,
						appliedSteps,
						activeRecipe.getId(),
						formatMs(System.nanoTime() - startNs)
				);
			}
			return FillResult.success(messageKey, appliedSteps);
		}

		if (session.isDebugLoggingEnabled() && batchResult.success()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] menu adjust had no effect requested={} recipe={} reason=no_applied_steps",
					requestedSteps,
					activeRecipe.getId()
			);
		}
		if (session.isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] menu.adjustRecipeLoad.fail menu={} steps={} applied={} recipe={} reason={} took={}ms",
					session.debugContextId(),
					steps,
					appliedSteps,
					activeRecipe.getId(),
					batchResult.messageKey(),
					formatMs(System.nanoTime() - startNs)
			);
		}

		return batchResult;
	}

	private static String formatMs(long nanos) {
		return String.format("%.3f", nanos / 1_000_000.0D);
	}
}
