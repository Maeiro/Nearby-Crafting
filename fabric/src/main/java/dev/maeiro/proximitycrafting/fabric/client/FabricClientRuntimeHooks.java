package dev.maeiro.proximitycrafting.fabric.client;

import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import net.minecraft.client.Minecraft;
import dev.maeiro.proximitycrafting.client.runtime.ActiveClientSessionHandle;
import dev.maeiro.proximitycrafting.client.runtime.ClientRuntimeHooks;
import org.jetbrains.annotations.Nullable;

public final class FabricClientRuntimeHooks implements ClientRuntimeHooks {
	@Override
	@Nullable
	public ActiveClientSessionHandle getActiveSession(int containerId) {
		if (!(Minecraft.getInstance().screen instanceof ProximityCraftingScreen screen)) {
			return null;
		}
		if (screen.getMenu().containerId != containerId) {
			return null;
		}
		return new FabricActiveClientSessionHandle(screen);
	}

	@Override
	@Nullable
	public ActiveClientSessionHandle getActiveSession() {
		if (!(Minecraft.getInstance().screen instanceof ProximityCraftingScreen screen)) {
			return null;
		}
		return new FabricActiveClientSessionHandle(screen);
	}
}
