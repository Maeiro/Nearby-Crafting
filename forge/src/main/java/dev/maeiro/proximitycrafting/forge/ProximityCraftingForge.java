package dev.maeiro.proximitycrafting.forge;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.ClientSetup;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import dev.maeiro.proximitycrafting.registry.ModBlocks;
import dev.maeiro.proximitycrafting.registry.ModItems;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(ProximityCrafting.MOD_ID)
public class ProximityCraftingForge {
	public ProximityCraftingForge() {
		ProximityCrafting.init();

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ProximityCraftingConfig.SERVER_SPEC);
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ProximityCraftingConfig.CLIENT_SPEC);
		}
		modBus.addListener(ProximityCraftingConfig::onConfigChanged);
		modBus.addListener(this::onCommonSetup);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			modBus.addListener(this::onClientSetup);
		}

		ModBlocks.BLOCKS.register(modBus);
		ModItems.ITEMS.register(modBus);
		ModMenuTypes.MENU_TYPES.register(modBus);
	}

	private void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(ProximityCraftingNetwork::register);
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		ClientSetup.onClientSetup(event);
	}
}
