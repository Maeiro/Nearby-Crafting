package dev.maeiro.proximitycrafting.client;

import dev.maeiro.proximitycrafting.client.net.ForgeClientRequestSender;
import dev.maeiro.proximitycrafting.client.net.ProximityClientServices;
import dev.maeiro.proximitycrafting.client.runtime.ForgeClientRuntimeHooks;
import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {
	private ClientSetup() {
	}

	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			ProximityClientServices.registerClientRequestSender(new ForgeClientRequestSender());
			ProximityClientServices.registerClientRuntimeHooks(new ForgeClientRuntimeHooks());
			MenuScreens.register(ModMenuTypes.PROXIMITY_CRAFTING_MENU.get(), ProximityCraftingScreen::new);
		});
	}
}



