package dev.maeiro.proximitycrafting.client.runtime;

import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import dev.maeiro.proximitycrafting.client.session.ClientRecipeSessionState;
import dev.maeiro.proximitycrafting.client.session.RecipeActionFeedbackApplyResult;
import dev.maeiro.proximitycrafting.client.session.SourceSnapshotApplyResult;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;

import java.util.List;

public final class ForgeActiveClientSessionHandle implements dev.maeiro.proximitycrafting.client.runtime.ActiveClientSessionHandle {
	private final ProximityCraftingScreen screen;

	public ForgeActiveClientSessionHandle(ProximityCraftingScreen screen) {
		this.screen = screen;
	}

	@Override
	public int containerId() {
		return screen.getMenu().containerId;
	}

	@Override
	public ClientRecipeSessionState sessionState() {
		return screen.getRecipeSessionState();
	}

	@Override
	public boolean replaceSupplementalSources(List<RecipeBookSourceEntry> sourceEntries) {
		return screen.getMenu().setClientRecipeBookSupplementalSources(sourceEntries);
	}

	@Override
	public void afterSourceSnapshotApplied(int entryCount, boolean sourcesChanged, SourceSnapshotApplyResult result) {
		screen.handleSourceSnapshotAppliedFromRuntime(entryCount, sourcesChanged, result);
	}

	@Override
	public void afterRecipeActionFeedbackApplied(RecipeFillFeedbackPayload payload, RecipeActionFeedbackApplyResult result) {
		screen.handleRecipeActionFeedbackFromRuntime(payload, result);
	}
}
