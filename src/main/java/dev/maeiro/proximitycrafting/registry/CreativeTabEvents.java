package dev.maeiro.proximitycrafting.registry;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProximityCrafting.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreativeTabEvents {
	private CreativeTabEvents() {
	}

	@SubscribeEvent
	public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			event.accept(ModItems.PROXIMITY_CRAFTING_TABLE.get());
		}
	}
}



