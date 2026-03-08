package dev.maeiro.proximitycrafting.client;

import dev.architectury.registry.menu.MenuRegistry;
import dev.maeiro.proximitycrafting.client.net.FabricClientRequestSender;
import dev.maeiro.proximitycrafting.client.net.ProximityClientServices;
import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import dev.maeiro.proximitycrafting.fabric.client.FabricClientRuntimeHooks;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;

public final class ClientSetup {
	private ClientSetup() {
	}

	public static void onClientSetup() {
		ProximityClientServices.registerClientRequestSender(new FabricClientRequestSender());
		ProximityClientServices.registerClientRuntimeHooks(new FabricClientRuntimeHooks());
		MenuRegistry.registerScreenFactory(ModMenuTypes.PROXIMITY_CRAFTING_MENU, ProximityCraftingScreen::new);
	}
}
