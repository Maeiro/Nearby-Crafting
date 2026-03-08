package dev.maeiro.proximitycrafting.client.presenter;

import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class IngredientsPanelPresenter {
	private ResourceLocation cachedRecipeId;
	private long cachedGridSignature = Long.MIN_VALUE;
	private long cachedBuiltAtMs;
	private boolean cacheDirty = true;
	private List<RecipeBookSourceEntry> cachedSourcesRef = List.of();
	private List<IngredientsPanelEntry> cachedEntries = List.of();

	public IngredientsPanelUpdateResult refresh(IngredientsPanelContext context, long nowMs) {
		List<RecipeBookSourceEntry> currentSources = context.getCurrentSources();
		long gridSignature = computeCraftGridSignature(context);
		boolean dirtyBefore = cacheDirty;
		long previousBuiltAtMs = cachedBuiltAtMs;
		boolean gridChanged = gridSignature != cachedGridSignature;
		boolean sourcesChanged = currentSources != cachedSourcesRef;

		if (!dirtyBefore && !gridChanged && !sourcesChanged) {
			return new IngredientsPanelUpdateResult(
					cachedEntries,
					false,
					cachedRecipeId,
					false,
					false,
					false,
					false,
					previousBuiltAtMs == 0L ? -1L : (nowMs - previousBuiltAtMs),
					0,
					0L,
					0L,
					0L
			);
		}

		long recipeLookupStartNs = System.nanoTime();
		Optional<CraftingRecipe> recipeOptional = context.resolvePreferredRecipe();
		long recipeLookupEndNs = System.nanoTime();
		if (recipeOptional.isEmpty()) {
			cachedEntries = List.of();
			cachedRecipeId = null;
			cachedGridSignature = gridSignature;
			cachedBuiltAtMs = nowMs;
			cachedSourcesRef = currentSources;
			cacheDirty = false;
			return new IngredientsPanelUpdateResult(
					cachedEntries,
					true,
					null,
					dirtyBefore,
					gridChanged,
					sourcesChanged,
					false,
					previousBuiltAtMs == 0L ? -1L : (nowMs - previousBuiltAtMs),
					0,
					recipeLookupEndNs - recipeLookupStartNs,
					0L,
					0L
			);
		}

		CraftingRecipe recipe = recipeOptional.get();
		ResourceLocation recipeId = recipe.getId();
		boolean recipeChanged = !recipeId.equals(cachedRecipeId);
		ComputationResult computationResult = collectEntries(recipe, currentSources);

		cachedEntries = computationResult.entries();
		cachedRecipeId = recipeId;
		cachedGridSignature = gridSignature;
		cachedBuiltAtMs = nowMs;
		cachedSourcesRef = currentSources;
		cacheDirty = false;

		return new IngredientsPanelUpdateResult(
				cachedEntries,
				true,
				recipeId,
				dirtyBefore,
				gridChanged,
				sourcesChanged,
				recipeChanged,
				previousBuiltAtMs == 0L ? -1L : (nowMs - previousBuiltAtMs),
				computationResult.sourceEntriesProcessed(),
				recipeLookupEndNs - recipeLookupStartNs,
				computationResult.trackerBuildDurationNs(),
				computationResult.aggregateDurationNs()
		);
	}

	public void onSourcesChanged(List<RecipeBookSourceEntry> currentSources) {
		if (cachedRecipeId == null && cachedEntries.isEmpty() && !cacheDirty) {
			cachedSourcesRef = currentSources;
			return;
		}
		cachedSourcesRef = currentSources;
		cacheDirty = true;
	}

	public void markDirty() {
		cacheDirty = true;
	}

	private static ComputationResult collectEntries(CraftingRecipe recipe, List<RecipeBookSourceEntry> sources) {
		Map<String, IngredientTracker> ingredientTrackers = new LinkedHashMap<>();
		long trackerBuildStartNs = System.nanoTime();
		for (Ingredient ingredient : recipe.getIngredients()) {
			if (ingredient == null || ingredient.isEmpty()) {
				continue;
			}

			ItemStack displayStack = resolveDisplayStack(ingredient);
			if (displayStack.isEmpty()) {
				continue;
			}

			String ingredientKey = buildIngredientKey(ingredient);
			IngredientTracker tracker = ingredientTrackers.computeIfAbsent(
					ingredientKey,
					key -> new IngredientTracker(displayStack, ingredient)
			);
			tracker.requiredCount++;
		}
		long trackerBuildEndNs = System.nanoTime();

		if (ingredientTrackers.isEmpty()) {
			return new ComputationResult(List.of(), 0, trackerBuildEndNs - trackerBuildStartNs, 0L);
		}

		long aggregateStartNs = System.nanoTime();
		int sourceEntriesProcessed = 0;
		for (RecipeBookSourceEntry sourceEntry : sources) {
			ItemStack sourceStack = sourceEntry.stack();
			if (sourceStack.isEmpty() || sourceEntry.count() <= 0) {
				continue;
			}
			sourceEntriesProcessed++;

			for (IngredientTracker tracker : ingredientTrackers.values()) {
				if (tracker.ingredient.test(sourceStack)) {
					tracker.availableCount += sourceEntry.count();
				}
			}
		}
		long aggregateEndNs = System.nanoTime();

		List<IngredientsPanelEntry> entries = new ArrayList<>(ingredientTrackers.size());
		for (IngredientTracker tracker : ingredientTrackers.values()) {
			entries.add(new IngredientsPanelEntry(tracker.displayStack, tracker.availableCount, tracker.requiredCount));
		}
		entries.sort(Comparator.comparingInt(IngredientsPanelEntry::requiredCount).reversed());
		return new ComputationResult(
				List.copyOf(entries),
				sourceEntriesProcessed,
				trackerBuildEndNs - trackerBuildStartNs,
				aggregateEndNs - aggregateStartNs
		);
	}

	private static long computeCraftGridSignature(IngredientsPanelContext context) {
		long signature = 1469598103934665603L;
		for (int slot = 0; slot < context.getCraftGridSize(); slot++) {
			ItemStack stack = context.getCraftGridItem(slot);
			if (stack.isEmpty()) {
				signature = mixPanelSignature(signature, 0L);
				continue;
			}
			signature = mixPanelSignature(signature, Item.getId(stack.getItem()));
			signature = mixPanelSignature(signature, stack.getCount());
			signature = mixPanelSignature(signature, stack.hasTag() ? stack.getTag().hashCode() : 0);
		}
		return signature;
	}

	private static long mixPanelSignature(long current, long value) {
		current ^= value;
		return current * 1099511628211L;
	}

	private static ItemStack resolveDisplayStack(Ingredient ingredient) {
		ItemStack[] options = ingredient.getItems();
		if (options.length == 0) {
			return ItemStack.EMPTY;
		}

		ItemStack display = options[0].copy();
		display.setCount(1);
		return display;
	}

	private static String buildIngredientKey(Ingredient ingredient) {
		List<String> optionKeys = new ArrayList<>();
		for (ItemStack option : ingredient.getItems()) {
			if (option.isEmpty()) {
				continue;
			}
			ItemStack normalized = option.copy();
			normalized.setCount(1);
			optionKeys.add(buildStackKey(normalized));
		}
		optionKeys.sort(String::compareTo);
		return String.join("|", optionKeys);
	}

	private static String buildStackKey(ItemStack stack) {
		CompoundTag serialized = new CompoundTag();
		stack.save(serialized);
		serialized.remove("Count");
		return serialized.toString();
	}

	private static final class IngredientTracker {
		private final ItemStack displayStack;
		private final Ingredient ingredient;
		private int availableCount;
		private int requiredCount;

		private IngredientTracker(ItemStack displayStack, Ingredient ingredient) {
			this.displayStack = displayStack;
			this.ingredient = ingredient;
		}
	}

	private record ComputationResult(
			List<IngredientsPanelEntry> entries,
			int sourceEntriesProcessed,
			long trackerBuildDurationNs,
			long aggregateDurationNs
	) {
	}
}
