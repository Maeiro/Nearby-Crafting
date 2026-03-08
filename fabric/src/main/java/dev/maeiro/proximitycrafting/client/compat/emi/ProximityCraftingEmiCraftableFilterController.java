package dev.maeiro.proximitycrafting.client.compat.emi;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class ProximityCraftingEmiCraftableFilterController {
	private ProximityCraftingEmiCraftableFilterController() {
	}

	public static boolean isRuntimeAvailable() {
		return false;
	}

	public static void enforceIndexOnlyMode() {
	}

	public static boolean isEnabledFor(int containerId) {
		return false;
	}

	public static void setEnabled(ProximityCraftingMenu menu, boolean enabled) {
	}

	public static void applyStartupPendingViewIfEnabled(ProximityCraftingMenu menu) {
	}

	public static void refreshIfEnabled(ProximityCraftingMenu menu) {
	}

	public static void onSourceSyncStateUpdated(ProximityCraftingMenu menu, boolean sourceSyncInFlight, boolean sourcesChanged) {
	}

	public static void onRecipeActionQueued(ProximityCraftingMenu menu) {
	}

	@Nullable
	public static ResourceLocation resolveHoveredRecipeId(ProximityCraftingMenu menu, double mouseX, double mouseY) {
		return null;
	}

	public static void handleMenuClosed(int containerId) {
	}
}
