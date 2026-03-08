package dev.maeiro.proximitycrafting.client.compat.jei;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class ProximityCraftingJeiCraftableFilterController {
	private ProximityCraftingJeiCraftableFilterController() {
	}

	public static boolean isEnabledFor(int containerId) {
		return false;
	}

	public static void setEnabled(ProximityCraftingMenu menu, boolean enabled) {
	}

	public static void prewarmSnapshot(ProximityCraftingMenu menu, String reason) {
	}

	public static void refreshIfEnabled(ProximityCraftingMenu menu) {
	}

	@Nullable
	public static ResourceLocation resolveHoveredRecipeId(ProximityCraftingMenu menu) {
		return null;
	}

	public static void handleMenuClosed(int containerId) {
	}
}
