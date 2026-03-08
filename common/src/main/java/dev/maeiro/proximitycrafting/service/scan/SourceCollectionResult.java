package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;

import java.util.ArrayList;
import java.util.List;

public record SourceCollectionResult(
		List<ItemSourceRef> containerSources,
		List<ItemSourceRef> playerInventorySources,
		List<ItemSourceRef> playerBackpackSources
) {
	public SourceCollectionResult {
		containerSources = List.copyOf(containerSources);
		playerInventorySources = List.copyOf(playerInventorySources);
		playerBackpackSources = List.copyOf(playerBackpackSources);
	}

	public List<ItemSourceRef> playerSources() {
		List<ItemSourceRef> result = new ArrayList<>(playerInventorySources.size() + playerBackpackSources.size());
		result.addAll(playerInventorySources);
		result.addAll(playerBackpackSources);
		return result;
	}

	public List<ItemSourceRef> mergedSources(SourcePriority priority) {
		List<ItemSourceRef> playerSources = playerSources();
		List<ItemSourceRef> result = new ArrayList<>(containerSources.size() + playerSources.size());
		if (priority == SourcePriority.PLAYER_FIRST) {
			result.addAll(playerSources);
			result.addAll(containerSources);
		} else {
			result.addAll(containerSources);
			result.addAll(playerSources);
		}
		return result;
	}

	public List<ItemSourceRef> recipeBookSupplementalSources() {
		List<ItemSourceRef> result = new ArrayList<>(containerSources.size() + playerBackpackSources.size());
		result.addAll(containerSources);
		result.addAll(playerBackpackSources);
		return result;
	}
}
