package dev.maeiro.proximitycrafting.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item PROXIMITY_CRAFTING_TABLE = Registry.register(
			BuiltInRegistries.ITEM,
			ProximityContentDescriptors.PROXIMITY_CRAFTING_TABLE.location(),
			new BlockItem(ModBlocks.PROXIMITY_CRAFTING_TABLE, new Item.Properties())
	);

	private ModItems() {
	}

	public static void init() {
	}
}
