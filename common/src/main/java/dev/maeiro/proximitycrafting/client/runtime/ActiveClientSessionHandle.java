package dev.maeiro.proximitycrafting.client.runtime;

import dev.maeiro.proximitycrafting.client.session.ClientRecipeSessionState;
import dev.maeiro.proximitycrafting.client.session.RecipeActionFeedbackApplyResult;
import dev.maeiro.proximitycrafting.client.session.SourceSnapshotApplyResult;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;

import java.util.List;

public interface ActiveClientSessionHandle {
	int containerId();

	ClientRecipeSessionState sessionState();

	boolean replaceSupplementalSources(List<RecipeBookSourceEntry> sourceEntries);

	void afterSourceSnapshotApplied(int entryCount, boolean sourcesChanged, SourceSnapshotApplyResult result);

	void afterRecipeActionFeedbackApplied(RecipeFillFeedbackPayload payload, RecipeActionFeedbackApplyResult result);
}
