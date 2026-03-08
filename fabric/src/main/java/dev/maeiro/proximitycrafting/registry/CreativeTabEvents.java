package dev.maeiro.proximitycrafting.registry;

import dev.architectury.registry.CreativeTabRegistry;
import net.minecraft.world.item.CreativeModeTabs;

public final class CreativeTabEvents {
	private CreativeTabEvents() {
	}

	public static void init() {
		CreativeTabRegistry.append(CreativeModeTabs.FUNCTIONAL_BLOCKS, ModItems.PROXIMITY_CRAFTING_TABLE);
	}
}
