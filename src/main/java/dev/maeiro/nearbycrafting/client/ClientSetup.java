package dev.maeiro.nearbycrafting.client;

import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.networking.C2SSyncSharedPreferences;
import dev.maeiro.nearbycrafting.networking.NearbyCraftingNetwork;
import dev.maeiro.nearbycrafting.client.screen.NearbyCraftingScreen;
import dev.maeiro.nearbycrafting.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {
	private ClientSetup() {
	}

	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			MenuScreens.register(ModMenuTypes.NEARBY_CRAFTING_MENU.get(), NearbyCraftingScreen::new);
			MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientPlayerLoggedIn);
		});
	}

	private static void onClientPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
		NearbyCraftingNetwork.CHANNEL.sendToServer(
				new C2SSyncSharedPreferences(
						NearbyCraftingConfig.CLIENT.includePlayerInventory.get(),
						NearbyCraftingConfig.CLIENT.sourcePriority.get()
				)
		);
	}
}
