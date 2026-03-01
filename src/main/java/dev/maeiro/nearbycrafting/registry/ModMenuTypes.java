package dev.maeiro.nearbycrafting.registry;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, NearbyCrafting.MOD_ID);

	public static final RegistryObject<MenuType<NearbyCraftingMenu>> NEARBY_CRAFTING_MENU = MENU_TYPES.register(
			"nearby_crafting",
			() -> IForgeMenuType.create((windowId, inventory, data) -> new NearbyCraftingMenu(windowId, inventory, data.readBlockPos()))
	);

	private ModMenuTypes() {
	}
}

