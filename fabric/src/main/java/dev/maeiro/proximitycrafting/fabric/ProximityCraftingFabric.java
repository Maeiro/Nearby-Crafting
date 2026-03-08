package dev.maeiro.proximitycrafting.fabric;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import dev.maeiro.proximitycrafting.registry.CreativeTabEvents;
import dev.maeiro.proximitycrafting.registry.ModBlocks;
import dev.maeiro.proximitycrafting.registry.ModItems;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import net.fabricmc.api.ModInitializer;

public class ProximityCraftingFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		ProximityCrafting.init();
		ModBlocks.init();
		ModItems.init();
		ModMenuTypes.init();
		CreativeTabEvents.init();
		ProximityCraftingNetwork.register();
	}
}
