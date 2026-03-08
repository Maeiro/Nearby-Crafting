package dev.maeiro.proximitycrafting.fabric;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import dev.maeiro.proximitycrafting.registry.CreativeTabEvents;
import dev.maeiro.proximitycrafting.registry.ModBlocks;
import dev.maeiro.proximitycrafting.registry.ModItems;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ProximityCraftingFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		ProximityCrafting.init();
		ProximityCraftingConfig.initialize(FabricLoader.getInstance().getConfigDir());
		ModBlocks.init();
		ModItems.init();
		ModMenuTypes.init();
		CreativeTabEvents.init();
		ProximityCraftingNetwork.register();
	}
}
