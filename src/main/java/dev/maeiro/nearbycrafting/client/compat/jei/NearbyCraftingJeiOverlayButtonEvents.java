package dev.maeiro.nearbycrafting.client.compat.jei;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.client.screen.NearbyCraftingScreen;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = NearbyCrafting.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NearbyCraftingJeiOverlayButtonEvents {
	private static final ResourceLocation RECIPE_BOOK_TEXTURE = new ResourceLocation("textures/gui/recipe_book.png");
	private static final int BUTTON_U = 152;
	private static final int BUTTON_V = 41;
	private static final int BUTTON_STATE_X_DIFF = 28;
	private static final int BUTTON_HOVER_Y_DIFF = 18;

	private static final int BUTTON_WIDTH = 26;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_PADDING_X = 1;
	@Nullable
	private static Rect2i lastRenderedButtonBounds;
	private static int lastRenderedContainerId = -1;

	private NearbyCraftingJeiOverlayButtonEvents() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
		if (event.getButton() != 0 && event.getButton() != 1) {
			return;
		}
		if (!(event.getScreen() instanceof NearbyCraftingScreen screen)) {
			return;
		}

		NearbyCraftingMenu menu = screen.getMenu();
		Rect2i buttonBounds = getButtonBoundsWithCache(menu);
		if (buttonBounds != null && buttonBounds.contains((int) event.getMouseX(), (int) event.getMouseY())) {
			return;
		}

		if (isDebugLoggingEnabled()) {
			NearbyCraftingJeiCraftableFilterController.debugProbeIngredientClick(
					menu,
					event.getButton(),
					event.getMouseX(),
					event.getMouseY(),
					"mouse_pressed_pre"
			);
		}

		boolean handledByFallback = NearbyCraftingJeiCraftableFilterController.handleIngredientClick(menu, event.getButton());
		if (isDebugLoggingEnabled()) {
			NearbyCrafting.LOGGER.info(
					"[NC-JEI] MousePressed menu={} button={} fallbackHandled={} canceledAfter={} craftableToggleEnabled={}",
					menu.containerId,
					event.getButton(),
					handledByFallback,
					handledByFallback,
					NearbyCraftingJeiCraftableFilterController.isEnabledFor(menu.containerId)
			);
		}
		if (handledByFallback) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
		if (event.getButton() != 0 && event.getButton() != 1) {
			return;
		}
		if (!(event.getScreen() instanceof NearbyCraftingScreen screen)) {
			return;
		}

		NearbyCraftingMenu menu = screen.getMenu();
		Rect2i buttonBounds = getButtonBoundsWithCache(menu);
		if (event.getButton() == 0 && buttonBounds != null && buttonBounds.contains((int) event.getMouseX(), (int) event.getMouseY())) {
			if (NearbyCraftingJeiCraftableFilterController.isTransitionBlockingInput()) {
				if (isDebugLoggingEnabled()) {
					NearbyCrafting.LOGGER.info(
							"[NC-JEI] Toggle click ignored during transition menu={} mouse=({}, {})",
							menu.containerId,
							(int) event.getMouseX(),
							(int) event.getMouseY()
					);
				}
				event.setCanceled(true);
				return;
			}

			boolean enabledBefore = NearbyCraftingJeiCraftableFilterController.isEnabledFor(menu.containerId);
			boolean nextEnabled = !NearbyCraftingJeiCraftableFilterController.isEnabledFor(menu.containerId);
			if (isDebugLoggingEnabled()) {
				NearbyCrafting.LOGGER.info(
						"[NC-JEI] Toggle click menu={} enabledBefore={} nextEnabled={} mouse=({}, {}) bounds=({}, {}, {}, {}) runtimeAvailable={}",
						menu.containerId,
						enabledBefore,
						nextEnabled,
						(int) event.getMouseX(),
						(int) event.getMouseY(),
						buttonBounds.getX(),
						buttonBounds.getY(),
						buttonBounds.getWidth(),
						buttonBounds.getHeight(),
						NearbyCraftingJeiCraftableFilterController.isRuntimeAvailable()
				);
			}

			NearbyCraftingJeiCraftableFilterController.setEnabled(menu, nextEnabled);
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			event.setCanceled(true);
			return;
		}

	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onScreenInit(ScreenEvent.Init.Post event) {
		NearbyCraftingJeiCraftableFilterController.debugProbeScreenInit(event.getScreen());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
		// Process any deferred overlay rebuilds from the JEI filter controller
		NearbyCraftingJeiCraftableFilterController.processDeferred();
		
		if (!(event.getScreen() instanceof NearbyCraftingScreen screen)) {
			lastRenderedButtonBounds = null;
			lastRenderedContainerId = -1;
			return;
		}

		NearbyCraftingMenu menu = screen.getMenu();
		Rect2i buttonBounds = getButtonBounds();
		if (buttonBounds == null) {
			lastRenderedButtonBounds = null;
			lastRenderedContainerId = -1;
			return;
		}
		lastRenderedButtonBounds = buttonBounds;
		lastRenderedContainerId = menu.containerId;

		int mouseX = (int) event.getMouseX();
		int mouseY = (int) event.getMouseY();
		boolean hovered = buttonBounds.contains(mouseX, mouseY);

		boolean enabled = NearbyCraftingJeiCraftableFilterController.isEnabledFor(menu.containerId);

		int u = BUTTON_U + (enabled ? BUTTON_STATE_X_DIFF : 0);
		int v = BUTTON_V + (hovered ? BUTTON_HOVER_Y_DIFF : 0);
		event.getGuiGraphics().blit(
				RECIPE_BOOK_TEXTURE,
				buttonBounds.getX(),
				buttonBounds.getY(),
				u,
				v,
				buttonBounds.getWidth(),
				buttonBounds.getHeight()
		);

		if (hovered) {
			Component tooltip = enabled
					? Component.translatable("gui.recipebook.toggleRecipes.craftable")
					: Component.translatable("gui.recipebook.toggleRecipes.all");
			event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
		}
	}

	@Nullable
	private static Rect2i getButtonBounds() {
		Rect2i searchFieldBounds = NearbyCraftingJeiCraftableFilterController.getJeiSearchFieldBounds();
		if (searchFieldBounds == null) {
			return null;
		}

		int x = searchFieldBounds.getX() - BUTTON_WIDTH - BUTTON_PADDING_X;
		int y = searchFieldBounds.getY() + (searchFieldBounds.getHeight() - BUTTON_HEIGHT) / 2;
		return new Rect2i(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	@Nullable
	private static Rect2i getButtonBoundsWithCache(NearbyCraftingMenu menu) {
		Rect2i liveBounds = getButtonBounds();
		if (liveBounds != null) {
			lastRenderedButtonBounds = liveBounds;
			lastRenderedContainerId = menu.containerId;
			return liveBounds;
		}

		if (lastRenderedButtonBounds != null && lastRenderedContainerId == menu.containerId) {
			return lastRenderedButtonBounds;
		}

		return null;
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return NearbyCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException ignored) {
			return false;
		}
	}
}
