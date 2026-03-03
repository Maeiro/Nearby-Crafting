package dev.maeiro.nearbycrafting.registry;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade.AdvancedCraftingUpgradeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
	private static final String SOPHISTICATED_BACKPACKS_MOD_ID = "sophisticatedbackpacks";
	private static final String SOPHISTICATED_CORE_MOD_ID = "sophisticatedcore";

	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, NearbyCrafting.MOD_ID);

	public static final RegistryObject<Item> NEARBY_CRAFTING_TABLE = ITEMS.register(
			"nearby_crafting_table",
			() -> new BlockItem(ModBlocks.NEARBY_CRAFTING_TABLE.get(), new Item.Properties())
	);
	public static final RegistryObject<Item> ADVANCED_CRAFTING_UPGRADE = ITEMS.register(
			"advanced_crafting_upgrade",
			() -> {
				if (!ModList.get().isLoaded(SOPHISTICATED_BACKPACKS_MOD_ID) || !ModList.get().isLoaded(SOPHISTICATED_CORE_MOD_ID)) {
					return new Item(new Item.Properties().stacksTo(16));
				}
				return AdvancedCraftingUpgradeItem.createDefaultLimited();
			}
	);

	private ModItems() {
	}
}
