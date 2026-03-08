package dev.maeiro.proximitycrafting.client;

import dev.maeiro.proximitycrafting.client.net.NeoForgeClientRequestSender;
import dev.maeiro.proximitycrafting.client.net.ProximityClientServices;
import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import dev.maeiro.proximitycrafting.neoforge.client.NeoForgeClientRuntimeHooks;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientSetup {
	private ClientSetup() {
	}

	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			ProximityClientServices.registerClientRequestSender(new NeoForgeClientRequestSender());
			ProximityClientServices.registerClientRuntimeHooks(new NeoForgeClientRuntimeHooks());
			MenuScreens.register(ModMenuTypes.PROXIMITY_CRAFTING_MENU.get(), ProximityCraftingScreen::new);
		});
	}
}
