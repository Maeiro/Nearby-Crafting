package dev.maeiro.proximitycrafting.registry;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ProximityCrafting.MOD_ID);

	public static final RegistryObject<Item> PROXIMITY_CRAFTING_TABLE = ITEMS.register(
			ProximityContentDescriptors.PROXIMITY_CRAFTING_TABLE.path(),
			() -> new BlockItem(ModBlocks.PROXIMITY_CRAFTING_TABLE.get(), new Item.Properties())
	);

	private ModItems() {
	}
}




