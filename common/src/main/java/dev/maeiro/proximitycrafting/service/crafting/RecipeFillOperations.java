package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.service.scan.ScanOptions;
import dev.maeiro.proximitycrafting.service.scan.SourceCollector;
import dev.maeiro.proximitycrafting.service.source.IngredientSourcePool;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecipeFillOperations {
	private RecipeFillOperations() {
	}

	public static FillResult fillFromRecipe(
			CraftingSessionPort session,
			SourceCollector sourceCollector,
			ScanOptions scanOptions,
			CraftingRecipe recipe,
			boolean craftAll
	) {
		long totalStartNs = System.nanoTime();
		session.beginCraftGridBulkMutation();
		session.setLastPlacedRecipe(recipe);
		List<Ingredient> targetGrid = buildTargetGrid(recipe);

		long clearStartNs = System.nanoTime();
		session.clearCraftGridToSourcesPlayerOrDrop();
		long clearEndNs = System.nanoTime();

		long scanStartNs = System.nanoTime();
		List<ItemSourceRef> sources = collectSources(session, sourceCollector, scanOptions);
		long scanEndNs = System.nanoTime();
		IngredientSourcePool pool = new IngredientSourcePool(sources);
		long planStartNs = System.nanoTime();
		Optional<ExtractionPlan> firstPlanOptional = pool.plan(targetGrid);
		long planEndNs = System.nanoTime();

		if (firstPlanOptional.isEmpty()) {
			long slotsChangedStartNs = System.nanoTime();
			session.endCraftGridBulkMutation();
			long slotsChangedEndNs = System.nanoTime();
			long broadcastStartNs = System.nanoTime();
			session.broadcastChanges();
			long broadcastEndNs = System.nanoTime();
			logPerf(
					"fillFromRecipe.fail.no_plan",
					session,
					recipe,
					craftAll,
					sources.size(),
					clearStartNs,
					clearEndNs,
					scanStartNs,
					scanEndNs,
					planStartNs,
					planEndNs,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					slotsChangedStartNs,
					slotsChangedEndNs,
					broadcastStartNs,
					broadcastEndNs,
					totalStartNs
			);
			return FillResult.failure("proximitycrafting.feedback.not_enough_ingredients");
		}

		long commitStartNs = System.nanoTime();
		ExtractionCommitResult firstCommit = firstPlanOptional.get().commit();
		long commitEndNs = System.nanoTime();
		if (firstCommit == null) {
			long slotsChangedStartNs = System.nanoTime();
			session.endCraftGridBulkMutation();
			long slotsChangedEndNs = System.nanoTime();
			long broadcastStartNs = System.nanoTime();
			session.broadcastChanges();
			long broadcastEndNs = System.nanoTime();
			logPerf(
					"fillFromRecipe.fail.commit_null",
					session,
					recipe,
					craftAll,
					sources.size(),
					clearStartNs,
					clearEndNs,
					scanStartNs,
					scanEndNs,
					planStartNs,
					planEndNs,
					commitStartNs,
					commitEndNs,
					0L,
					0L,
					0L,
					0L,
					slotsChangedStartNs,
					slotsChangedEndNs,
					broadcastStartNs,
					broadcastEndNs,
					totalStartNs
			);
			return FillResult.failure("proximitycrafting.feedback.fill_failed");
		}

		long applyStartNs = System.nanoTime();
		if (!applyCommitAsSet(session, firstCommit)) {
			long applyEndNs = System.nanoTime();
			rollbackCommit(firstCommit);
			long slotsChangedStartNs = System.nanoTime();
			session.endCraftGridBulkMutation();
			long slotsChangedEndNs = System.nanoTime();
			long broadcastStartNs = System.nanoTime();
			session.broadcastChanges();
			long broadcastEndNs = System.nanoTime();
			logPerf(
					"fillFromRecipe.fail.apply_set",
					session,
					recipe,
					craftAll,
					sources.size(),
					clearStartNs,
					clearEndNs,
					scanStartNs,
					scanEndNs,
					planStartNs,
					planEndNs,
					commitStartNs,
					commitEndNs,
					applyStartNs,
					applyEndNs,
					0L,
					0L,
					slotsChangedStartNs,
					slotsChangedEndNs,
					broadcastStartNs,
					broadcastEndNs,
					totalStartNs
			);
			return FillResult.failure("proximitycrafting.feedback.fill_failed");
		}
		long applyEndNs = System.nanoTime();

		int loadedCrafts = 1;
		long additionalStartNs = 0L;
		long additionalEndNs = 0L;
		if (craftAll) {
			additionalStartNs = System.nanoTime();
			loadedCrafts += fillAdditionalCrafts(session, pool, targetGrid);
			additionalEndNs = System.nanoTime();
		}

		long slotsChangedStartNs = System.nanoTime();
		session.endCraftGridBulkMutation();
		long slotsChangedEndNs = System.nanoTime();
		long broadcastStartNs = System.nanoTime();
		session.broadcastChanges();
		long broadcastEndNs = System.nanoTime();

		if (ProximityCrafting.LOGGER.isDebugEnabled()) {
			ProximityCrafting.LOGGER.debug("Filled proximity crafting grid for recipe {}", recipe.getClass().getSimpleName());
		}

		logPerf(
				"fillFromRecipe.success",
				session,
				recipe,
				craftAll,
				sources.size(),
				clearStartNs,
				clearEndNs,
				scanStartNs,
				scanEndNs,
				planStartNs,
				planEndNs,
				commitStartNs,
				commitEndNs,
				applyStartNs,
				applyEndNs,
				additionalStartNs,
				additionalEndNs,
				slotsChangedStartNs,
				slotsChangedEndNs,
				broadcastStartNs,
				broadcastEndNs,
				totalStartNs
		);

		if (craftAll) {
			return FillResult.success("proximitycrafting.feedback.filled_max", loadedCrafts);
		}

		return FillResult.success("proximitycrafting.feedback.filled", 0);
	}

	public static FillResult refillLastRecipe(
			CraftingSessionPort session,
			SourceCollector sourceCollector,
			ScanOptions scanOptions
	) {
		long startNs = System.nanoTime();
		session.beginCraftGridBulkMutation();
		CraftingRecipe lastRecipe = session.getLastPlacedRecipe();
		if (lastRecipe == null) {
			session.endCraftGridBulkMutation();
			return FillResult.failure("proximitycrafting.feedback.no_recipe_selected");
		}

		List<Ingredient> targetGrid = buildTargetGrid(lastRecipe);
		List<ItemSourceRef> sources = collectSources(session, sourceCollector, scanOptions);
		IngredientSourcePool pool = new IngredientSourcePool(sources);
		Optional<ExtractionPlan> refillPlanOptional = pool.plan(targetGrid);
		if (refillPlanOptional.isEmpty()) {
			session.endCraftGridBulkMutation();
			return FillResult.failure("proximitycrafting.feedback.not_enough_ingredients");
		}

		ExtractionCommitResult refillCommit = refillPlanOptional.get().commit();
		if (refillCommit == null) {
			session.endCraftGridBulkMutation();
			return FillResult.failure("proximitycrafting.feedback.fill_failed");
		}

		if (!applyCommitAsAdd(session, refillCommit)) {
			rollbackCommit(refillCommit);
			session.endCraftGridBulkMutation();
			return FillResult.failure("proximitycrafting.feedback.fill_failed");
		}

		session.endCraftGridBulkMutation();
		session.broadcastChanges();
		if (session.isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] refillLastRecipe menu={} recipe={} sourceSlots={} took={}ms",
					session.debugContextId(),
					lastRecipe.getId(),
					sources.size(),
					formatMs(System.nanoTime() - startNs)
			);
		}
		return FillResult.success("proximitycrafting.feedback.filled", 0);
	}

	public static FillResult addSingleCraft(
			CraftingSessionPort session,
			SourceCollector sourceCollector,
			ScanOptions scanOptions,
			CraftingRecipe recipe
	) {
		FillResult result = addCrafts(session, sourceCollector, scanOptions, recipe, 1);
		if (!result.success()) {
			return result;
		}
		return FillResult.success("proximitycrafting.feedback.filled", 0);
	}

	public static FillResult addCrafts(
			CraftingSessionPort session,
			SourceCollector sourceCollector,
			ScanOptions scanOptions,
			CraftingRecipe recipe,
			int requestedCrafts
	) {
		long startNs = System.nanoTime();
		session.beginCraftGridBulkMutation();
		if (requestedCrafts <= 0) {
			session.endCraftGridBulkMutation();
			return FillResult.success("proximitycrafting.feedback.filled", 0);
		}

		List<Ingredient> targetGrid = buildTargetGrid(recipe);
		if (!hasRoomForSingleCraftAdd(session, targetGrid)) {
			session.endCraftGridBulkMutation();
			return FillResult.failure("proximitycrafting.feedback.not_enough_space");
		}

		List<ItemSourceRef> sources = collectSources(session, sourceCollector, scanOptions);
		IngredientSourcePool pool = new IngredientSourcePool(sources);
		int appliedCrafts = 0;
		String failureKey = "proximitycrafting.feedback.fill_failed";
		for (int craftIndex = 0; craftIndex < requestedCrafts; craftIndex++) {
			if (!hasRoomForSingleCraftAdd(session, targetGrid)) {
				failureKey = "proximitycrafting.feedback.not_enough_space";
				break;
			}

			Optional<ExtractionPlan> refillPlanOptional = pool.plan(targetGrid);
			if (refillPlanOptional.isEmpty()) {
				failureKey = "proximitycrafting.feedback.not_enough_ingredients";
				break;
			}

			ExtractionCommitResult refillCommit = refillPlanOptional.get().commit();
			if (refillCommit == null) {
				failureKey = "proximitycrafting.feedback.fill_failed";
				break;
			}

			if (!applyCommitAsAdd(session, refillCommit)) {
				rollbackCommit(refillCommit);
				failureKey = "proximitycrafting.feedback.fill_failed";
				break;
			}

			appliedCrafts++;
		}

		if (appliedCrafts > 0) {
			session.endCraftGridBulkMutation();
			session.broadcastChanges();
			if (session.isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] addCrafts menu={} recipe={} requested={} applied={} sourceSlots={} took={}ms",
						session.debugContextId(),
						recipe.getId(),
						requestedCrafts,
						appliedCrafts,
						sources.size(),
						formatMs(System.nanoTime() - startNs)
				);
			}
			return FillResult.success("proximitycrafting.feedback.filled", appliedCrafts);
		}
		session.endCraftGridBulkMutation();

		if (session.isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] addCrafts.fail menu={} recipe={} requested={} applied={} reason={} sourceSlots={} took={}ms",
					session.debugContextId(),
					recipe.getId(),
					requestedCrafts,
					appliedCrafts,
					failureKey,
					sources.size(),
					formatMs(System.nanoTime() - startNs)
			);
		}
		return FillResult.failure(failureKey);
	}

	public static FillResult removeSingleCraft(CraftingSessionPort session, CraftingRecipe recipe) {
		FillResult result = removeCrafts(session, recipe, 1);
		if (!result.success()) {
			return result;
		}
		return FillResult.success("proximitycrafting.feedback.filled", 0);
	}

	public static FillResult removeCrafts(CraftingSessionPort session, CraftingRecipe recipe, int requestedCrafts) {
		long startNs = System.nanoTime();
		session.beginCraftGridBulkMutation();
		if (requestedCrafts <= 0) {
			session.endCraftGridBulkMutation();
			return FillResult.success("proximitycrafting.feedback.filled", 0);
		}

		List<Ingredient> targetGrid = buildTargetGrid(recipe);
		int maxRemovableCrafts = Integer.MAX_VALUE;
		for (int slot = 0; slot < 9; slot++) {
			if (targetGrid.get(slot).isEmpty()) {
				continue;
			}
			ItemStack current = session.getCraftSlotItem(slot);
			if (current.isEmpty() || !targetGrid.get(slot).test(current)) {
				session.endCraftGridBulkMutation();
				return FillResult.failure("proximitycrafting.feedback.cannot_reduce_loaded_recipe");
			}
			maxRemovableCrafts = Math.min(maxRemovableCrafts, current.getCount());
		}
		if (maxRemovableCrafts <= 0 || maxRemovableCrafts == Integer.MAX_VALUE) {
			session.endCraftGridBulkMutation();
			return FillResult.failure("proximitycrafting.feedback.cannot_reduce_loaded_recipe");
		}

		int craftsToRemove = Math.min(requestedCrafts, maxRemovableCrafts);
		for (int slot = 0; slot < 9; slot++) {
			if (targetGrid.get(slot).isEmpty()) {
				continue;
			}
			if (!session.removeFromCraftSlotToSources(slot, craftsToRemove)) {
				session.endCraftGridBulkMutation();
				return FillResult.failure("proximitycrafting.feedback.fill_failed");
			}
		}

		session.endCraftGridBulkMutation();
		session.broadcastChanges();
		if (session.isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] removeCrafts menu={} recipe={} requested={} applied={} took={}ms",
					session.debugContextId(),
					recipe.getId(),
					requestedCrafts,
					craftsToRemove,
					formatMs(System.nanoTime() - startNs)
			);
		}
		return FillResult.success("proximitycrafting.feedback.filled", craftsToRemove);
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

	private static List<ItemSourceRef> collectSources(
			CraftingSessionPort session,
			SourceCollector sourceCollector,
			ScanOptions scanOptions
	) {
		return sourceCollector.collectSources(session.getLevel(), session.getTablePos(), session.getPlayer(), scanOptions);
	}

	private static int fillAdditionalCrafts(CraftingSessionPort session, IngredientSourcePool pool, List<Ingredient> targetGrid) {
		int additionalCrafts = 0;
		int maxIterations = session.getMaxShiftCraftIterations();
		for (int iteration = 1; iteration < maxIterations; iteration++) {
			if (!hasRoomForAnotherCraft(session, targetGrid)) {
				break;
			}

			List<ItemStack> exactTemplate = buildExactTemplate(session, targetGrid);
			Optional<ExtractionPlan> planOptional = pool.planExactStacks(exactTemplate);
			if (planOptional.isEmpty()) {
				break;
			}

			ExtractionCommitResult commitResult = planOptional.get().commit();
			if (commitResult == null) {
				break;
			}
			if (!applyCommitAsAdd(session, commitResult)) {
				rollbackCommit(commitResult);
				break;
			}
			additionalCrafts++;
		}
		return additionalCrafts;
	}

	private static boolean applyCommitAsSet(CraftingSessionPort session, ExtractionCommitResult commitResult) {
		ItemStack[] extractedStacks = commitResult.extractedStacks();
		ItemSourceRef[] sourceRefs = commitResult.sourceRefs();
		for (int slot = 0; slot < 9; slot++) {
			session.setCraftSlotFromSource(slot, extractedStacks[slot], sourceRefs[slot]);
		}
		return true;
	}

	private static boolean applyCommitAsAdd(CraftingSessionPort session, ExtractionCommitResult commitResult) {
		ItemStack[] extractedStacks = commitResult.extractedStacks();
		ItemSourceRef[] sourceRefs = commitResult.sourceRefs();

		for (int slot = 0; slot < 9; slot++) {
			ItemStack extracted = extractedStacks[slot];
			if (!session.canAcceptCraftSlotStack(slot, extracted)) {
				return false;
			}
		}

		for (int slot = 0; slot < 9; slot++) {
			ItemStack extracted = extractedStacks[slot];
			if (extracted.isEmpty()) {
				continue;
			}
			if (!session.addCraftSlotFromSource(slot, extracted, sourceRefs[slot])) {
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

			ItemStack notInserted = sourceRef.slotRef().insert(extracted.copy(), false);
			if (!notInserted.isEmpty()) {
				ProximityCrafting.LOGGER.warn(
						"Could not fully rollback extracted stack {} for source {}:{}",
						notInserted,
						sourceRef.sourceType(),
						sourceRef.slot()
				);
			}
		}
	}

	private static boolean hasRoomForAnotherCraft(CraftingSessionPort session, List<Ingredient> targetGrid) {
		for (int slot = 0; slot < 9; slot++) {
			if (targetGrid.get(slot).isEmpty()) {
				continue;
			}

			ItemStack current = session.getCraftSlotItem(slot);
			if (current.isEmpty()) {
				return false;
			}

			int slotLimit = Math.min(session.getCraftGridMaxStackSize(), current.getMaxStackSize());
			if (current.getCount() >= slotLimit) {
				return false;
			}
		}
		return true;
	}

	private static List<ItemStack> buildExactTemplate(CraftingSessionPort session, List<Ingredient> targetGrid) {
		List<ItemStack> template = new ArrayList<>(9);
		for (int slot = 0; slot < 9; slot++) {
			if (targetGrid.get(slot).isEmpty()) {
				template.add(ItemStack.EMPTY);
				continue;
			}

			ItemStack stack = session.getCraftSlotItem(slot);
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

	private static boolean hasRoomForSingleCraftAdd(CraftingSessionPort session, List<Ingredient> targetGrid) {
		for (int slot = 0; slot < 9; slot++) {
			Ingredient ingredient = targetGrid.get(slot);
			if (ingredient.isEmpty()) {
				continue;
			}

			ItemStack current = session.getCraftSlotItem(slot);
			if (current.isEmpty()) {
				continue;
			}
			if (!ingredient.test(current)) {
				return false;
			}

			int slotLimit = Math.min(session.getCraftGridMaxStackSize(), current.getMaxStackSize());
			if (current.getCount() >= slotLimit) {
				return false;
			}
		}
		return true;
	}

	private static void logPerf(
			String stage,
			CraftingSessionPort session,
			CraftingRecipe recipe,
			boolean craftAll,
			int sourceSlots,
			long clearStartNs,
			long clearEndNs,
			long scanStartNs,
			long scanEndNs,
			long planStartNs,
			long planEndNs,
			long commitStartNs,
			long commitEndNs,
			long applyStartNs,
			long applyEndNs,
			long additionalStartNs,
			long additionalEndNs,
			long slotsChangedStartNs,
			long slotsChangedEndNs,
			long broadcastStartNs,
			long broadcastEndNs,
			long totalStartNs
	) {
		if (!session.isDebugLoggingEnabled()) {
			return;
		}
		long totalEndNs = System.nanoTime();
		ProximityCrafting.LOGGER.info(
				"[PROXC-PERF] {} menu={} recipe={} craftAll={} sourceSlots={} clearMs={} scanMs={} planMs={} commitMs={} applyMs={} additionalMs={} slotsChangedMs={} broadcastMs={} totalMs={}",
				stage,
				session.debugContextId(),
				recipe.getId(),
				craftAll,
				sourceSlots,
				formatMs(clearEndNs - clearStartNs),
				formatMs(scanEndNs - scanStartNs),
				formatMs(planEndNs - planStartNs),
				commitStartNs == 0L ? "0.000" : formatMs(commitEndNs - commitStartNs),
				applyStartNs == 0L ? "0.000" : formatMs(applyEndNs - applyStartNs),
				additionalStartNs == 0L ? "0.000" : formatMs(additionalEndNs - additionalStartNs),
				slotsChangedStartNs == 0L ? "0.000" : formatMs(slotsChangedEndNs - slotsChangedStartNs),
				broadcastStartNs == 0L ? "0.000" : formatMs(broadcastEndNs - broadcastStartNs),
				formatMs(totalEndNs - totalStartNs)
		);
	}

	private static String formatMs(long nanos) {
		return String.format("%.3f", nanos / 1_000_000.0D);
	}
}
