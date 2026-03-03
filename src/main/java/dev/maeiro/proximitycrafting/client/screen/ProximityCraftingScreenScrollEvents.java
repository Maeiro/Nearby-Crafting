package dev.maeiro.proximitycrafting.client.screen;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProximityCrafting.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ProximityCraftingScreenScrollEvents {
	private ProximityCraftingScreenScrollEvents() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
		if (!(event.getScreen() instanceof ProximityCraftingScreen screen)) {
			return;
		}

		boolean handled = screen.tryHandleRecipeScaleScroll(
				event.getMouseX(),
				event.getMouseY(),
				event.getScrollDelta(),
				"forge_pre"
		);
		if (handled) {
			event.setCanceled(true);
		}
	}
}


