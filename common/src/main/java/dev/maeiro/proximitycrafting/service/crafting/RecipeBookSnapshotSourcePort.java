package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;

import java.util.List;

public interface RecipeBookSnapshotSourcePort {
	int debugContextId();

	boolean debugLoggingEnabled();

	List<ItemSourceRef> collectRecipeBookSourceRefs();
}
