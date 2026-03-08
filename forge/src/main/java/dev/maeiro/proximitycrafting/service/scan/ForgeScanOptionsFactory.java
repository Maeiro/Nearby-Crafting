package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;

public final class ForgeScanOptionsFactory {
	private ForgeScanOptionsFactory() {
	}

	public static ScanOptions fromMenu(ProximityCraftingMenu menu) {
		return new ScanOptions(
				ProximityCraftingConfig.SERVER.scanRadius.get(),
				ProximityCraftingConfig.SERVER.minSlotCount.get(),
				menu.isIncludePlayerInventory(),
				menu.getSourcePriority()
		);
	}
}
