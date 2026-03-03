package dev.maeiro.nearbycrafting.client.screen;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NearbyCrafting.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NearbyCraftingScreenScrollEvents {
	private NearbyCraftingScreenScrollEvents() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
		if (!(event.getScreen() instanceof NearbyCraftingScreen screen)) {
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
