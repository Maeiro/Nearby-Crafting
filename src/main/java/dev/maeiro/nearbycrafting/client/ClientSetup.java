package dev.maeiro.nearbycrafting.client;

import dev.maeiro.nearbycrafting.client.screen.NearbyCraftingScreen;
import dev.maeiro.nearbycrafting.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {
	private ClientSetup() {
	}

	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.NEARBY_CRAFTING_MENU.get(), NearbyCraftingScreen::new));
	}
}

