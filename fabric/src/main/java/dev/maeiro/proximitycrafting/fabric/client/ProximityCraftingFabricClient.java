package dev.maeiro.proximitycrafting.fabric.client;

import dev.maeiro.proximitycrafting.client.net.ProximityClientServices;
import net.fabricmc.api.ClientModInitializer;

public final class ProximityCraftingFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ProximityClientServices.registerClientRuntimeHooks(new FabricClientRuntimeHooks());
	}
}
