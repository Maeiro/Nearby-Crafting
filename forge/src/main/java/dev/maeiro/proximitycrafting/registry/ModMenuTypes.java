package dev.maeiro.proximitycrafting.registry;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ProximityCrafting.MOD_ID);

	public static final RegistryObject<MenuType<ProximityCraftingMenu>> PROXIMITY_CRAFTING_MENU = MENU_TYPES.register(
			ProximityContentDescriptors.PROXIMITY_CRAFTING_MENU.path(),
			() -> IForgeMenuType.create((windowId, inventory, data) -> new ProximityCraftingMenu(windowId, inventory, data.readBlockPos()))
	);

	private ModMenuTypes() {
	}
}



