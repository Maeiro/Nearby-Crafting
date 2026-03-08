package dev.maeiro.proximitycrafting.client.presenter;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record IngredientsPanelUpdateResult(
		List<IngredientsPanelEntry> entries,
		boolean cacheUpdated,
		ResourceLocation recipeId,
		boolean dirtyBefore,
		boolean gridChanged,
		boolean sourcesChanged,
		boolean recipeChanged,
		long previousBuiltAgeMs,
		int sourceEntriesProcessed,
		long recipeLookupDurationNs,
		long trackerBuildDurationNs,
		long aggregateDurationNs
) {
}
