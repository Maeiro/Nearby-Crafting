package dev.maeiro.proximitycrafting.client.runtime;

import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

public final class ForgeClientRuntimeHooks implements dev.maeiro.proximitycrafting.client.runtime.ClientRuntimeHooks {
	@Override
	@Nullable
	public dev.maeiro.proximitycrafting.client.runtime.ActiveClientSessionHandle getActiveSession(int containerId) {
		if (!(Minecraft.getInstance().screen instanceof ProximityCraftingScreen screen)) {
			return null;
		}
		if (screen.getMenu().containerId != containerId) {
			return null;
		}
		return new ForgeActiveClientSessionHandle(screen);
	}

	@Override
	@Nullable
	public dev.maeiro.proximitycrafting.client.runtime.ActiveClientSessionHandle getActiveSession() {
		if (!(Minecraft.getInstance().screen instanceof ProximityCraftingScreen screen)) {
			return null;
		}
		return new ForgeActiveClientSessionHandle(screen);
	}
}
