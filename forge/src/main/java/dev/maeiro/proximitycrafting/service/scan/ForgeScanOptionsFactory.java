package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;

public final class ForgeScanOptionsFactory {
	private ForgeScanOptionsFactory() {
	}

	public static ScanOptions fromMenu(ProximityCraftingMenu menu) {
		return ProximityCraftingConfig.serverRuntimeSettings().scanOptions(
				menu.isIncludePlayerInventory(),
				menu.getSourcePriority()
		);
	}
}
