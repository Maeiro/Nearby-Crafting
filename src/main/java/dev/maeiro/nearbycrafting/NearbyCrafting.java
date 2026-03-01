package dev.maeiro.nearbycrafting;

import dev.maeiro.nearbycrafting.client.ClientSetup;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.networking.NearbyCraftingNetwork;
import dev.maeiro.nearbycrafting.registry.ModBlocks;
import dev.maeiro.nearbycrafting.registry.ModItems;
import dev.maeiro.nearbycrafting.registry.ModMenuTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(NearbyCrafting.MOD_ID)
public class NearbyCrafting {
	public static final String MOD_ID = "nearbycrafting";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	public NearbyCrafting() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, NearbyCraftingConfig.SERVER_SPEC);
		modBus.addListener(NearbyCraftingConfig::onConfigChanged);
		modBus.addListener(this::onCommonSetup);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			modBus.addListener(this::onClientSetup);
		}

		ModBlocks.BLOCKS.register(modBus);
		ModItems.ITEMS.register(modBus);
		ModMenuTypes.MENU_TYPES.register(modBus);
	}

	private void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(NearbyCraftingNetwork::register);
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		ClientSetup.onClientSetup(event);
	}
}

