package dev.maeiro.proximitycrafting.registry;

import dev.architectury.registry.CreativeTabRegistry;
import net.minecraft.resources.ResourceLocation;

public final class CreativeTabEvents {
	private CreativeTabEvents() {
	}

	public static void init() {
		CreativeTabRegistry.append(
				CreativeTabRegistry.defer(new ResourceLocation("minecraft", "functional_blocks")),
				ModItems.PROXIMITY_CRAFTING_TABLE
		);
	}
}
