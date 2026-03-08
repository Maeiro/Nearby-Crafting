package dev.maeiro.proximitycrafting.neoforge;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.ClientSetup;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import dev.maeiro.proximitycrafting.registry.CreativeTabEvents;
import dev.maeiro.proximitycrafting.registry.ModBlocks;
import dev.maeiro.proximitycrafting.registry.ModItems;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(ProximityCrafting.MOD_ID)
public class ProximityCraftingNeoForge {
	public ProximityCraftingNeoForge() {
		ProximityCrafting.init();

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ModBlocks.BLOCKS.register(modBus);
		ModItems.ITEMS.register(modBus);
		ModMenuTypes.MENU_TYPES.register(modBus);
		modBus.addListener(CreativeTabEvents::onBuildCreativeModeTabContents);
		ProximityCraftingNetwork.register();

		if (FMLEnvironment.dist == Dist.CLIENT) {
			modBus.addListener(this::onClientSetup);
		}
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		ClientSetup.onClientSetup(event);
	}
}
