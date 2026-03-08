package dev.maeiro.proximitycrafting.fabric.client;

import dev.maeiro.proximitycrafting.client.ClientSetup;
import net.fabricmc.api.ClientModInitializer;

public final class ProximityCraftingFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientSetup.onClientSetup();
	}
}
