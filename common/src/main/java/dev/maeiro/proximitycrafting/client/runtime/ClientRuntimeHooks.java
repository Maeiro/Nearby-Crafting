package dev.maeiro.proximitycrafting.client.runtime;

import org.jetbrains.annotations.Nullable;

public interface ClientRuntimeHooks {
	@Nullable
	ActiveClientSessionHandle getActiveSession(int containerId);

	@Nullable
	ActiveClientSessionHandle getActiveSession();
}
