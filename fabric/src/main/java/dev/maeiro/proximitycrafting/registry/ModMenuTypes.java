package dev.maeiro.proximitycrafting.registry;

import dev.architectury.registry.menu.MenuRegistry;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenuTypes {
	public static final MenuType<ProximityCraftingMenu> PROXIMITY_CRAFTING_MENU = Registry.register(
			BuiltInRegistries.MENU,
			ProximityContentDescriptors.PROXIMITY_CRAFTING_MENU.location(),
			MenuRegistry.ofExtended((windowId, inventory, buf) -> new ProximityCraftingMenu(windowId, inventory, buf.readBlockPos()))
	);

	private ModMenuTypes() {
	}

	public static void init() {
	}
}
