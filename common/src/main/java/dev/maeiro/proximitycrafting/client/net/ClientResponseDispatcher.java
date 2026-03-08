package dev.maeiro.proximitycrafting.client.net;

import dev.maeiro.proximitycrafting.client.runtime.ActiveClientSessionHandle;
import dev.maeiro.proximitycrafting.client.runtime.ClientRuntimeHooks;
import dev.maeiro.proximitycrafting.client.session.RecipeActionFeedbackApplyResult;
import dev.maeiro.proximitycrafting.client.session.SourceSnapshotApplyResult;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceSnapshotPayload;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;

import java.util.Objects;

public final class ClientResponseDispatcher {
	private final ClientRuntimeHooks runtimeHooks;

	public ClientResponseDispatcher(ClientRuntimeHooks runtimeHooks) {
		this.runtimeHooks = Objects.requireNonNull(runtimeHooks, "runtimeHooks");
	}

	public boolean handleRecipeBookSourceSnapshot(RecipeBookSourceSnapshotPayload payload) {
		ActiveClientSessionHandle sessionHandle = runtimeHooks.getActiveSession(payload.containerId());
		if (sessionHandle == null) {
			return false;
		}

		boolean sourcesChanged = sessionHandle.replaceSupplementalSources(payload.sourceEntries());
		SourceSnapshotApplyResult result = sessionHandle.sessionState().applySourceSnapshot(System.currentTimeMillis(), sourcesChanged);
		sessionHandle.afterSourceSnapshotApplied(payload.sourceEntries().size(), sourcesChanged, result);
		return true;
	}

	public boolean handleRecipeFillFeedback(RecipeFillFeedbackPayload payload) {
		ActiveClientSessionHandle sessionHandle = runtimeHooks.getActiveSession();
		if (sessionHandle == null) {
			return false;
		}

		RecipeActionFeedbackApplyResult result = sessionHandle.sessionState().applyRecipeActionFeedback();
		sessionHandle.afterRecipeActionFeedbackApplied(payload, result);
		return true;
	}
}
