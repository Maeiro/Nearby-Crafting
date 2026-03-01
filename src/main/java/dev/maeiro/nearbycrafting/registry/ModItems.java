package dev.maeiro.nearbycrafting.registry;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, NearbyCrafting.MOD_ID);

	public static final RegistryObject<Item> NEARBY_CRAFTING_TABLE = ITEMS.register(
			"nearby_crafting_table",
			() -> new BlockItem(ModBlocks.NEARBY_CRAFTING_TABLE.get(), new Item.Properties())
	);

	private ModItems() {
	}
}

