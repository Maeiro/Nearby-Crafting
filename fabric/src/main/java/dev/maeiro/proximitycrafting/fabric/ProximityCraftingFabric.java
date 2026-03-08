package dev.maeiro.proximitycrafting.fabric;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import net.fabricmc.api.ModInitializer;

public class ProximityCraftingFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		ProximityCrafting.init();
	}
}
