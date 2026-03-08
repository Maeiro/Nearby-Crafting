package dev.maeiro.proximitycrafting.fabric.client;

import dev.maeiro.proximitycrafting.client.runtime.ActiveClientSessionHandle;
import dev.maeiro.proximitycrafting.client.runtime.ClientRuntimeHooks;
import org.jetbrains.annotations.Nullable;

public final class FabricClientRuntimeHooks implements ClientRuntimeHooks {
	@Override
	@Nullable
	public ActiveClientSessionHandle getActiveSession(int containerId) {
		return null;
	}

	@Override
	@Nullable
	public ActiveClientSessionHandle getActiveSession() {
		return null;
	}
}
