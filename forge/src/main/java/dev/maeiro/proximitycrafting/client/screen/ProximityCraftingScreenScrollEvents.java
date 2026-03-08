package dev.maeiro.proximitycrafting.client.screen;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.compat.jei.ProximityCraftingJeiCraftableFilterController;
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
			return;
		}

		// While JEI craftable-only is active, block JEI's own page scrolling so it
		// does not conflict with Proximity Crafting incremental scroll behavior.
		if (ProximityCraftingJeiCraftableFilterController.isEnabledFor(screen.getMenu().containerId)) {
			event.setCanceled(true);
		}
	}
}


