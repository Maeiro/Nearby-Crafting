package dev.maeiro.proximitycrafting.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

public final class CreativeTabEvents {
	private CreativeTabEvents() {
	}

	public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			event.accept(ModItems.PROXIMITY_CRAFTING_TABLE.get());
		}
	}
}
