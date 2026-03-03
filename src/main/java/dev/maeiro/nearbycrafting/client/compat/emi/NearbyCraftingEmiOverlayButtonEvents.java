package dev.maeiro.nearbycrafting.client.compat.emi;

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
public final class NearbyCraftingEmiOverlayButtonEvents {
	private static final ResourceLocation RECIPE_BOOK_TEXTURE = new ResourceLocation("textures/gui/recipe_book.png");
	private static final int BUTTON_U = 152;
	private static final int BUTTON_V = 41;
	private static final int BUTTON_STATE_X_DIFF = 28;
	private static final int BUTTON_HOVER_Y_DIFF = 18;
	private static final int BUTTON_WIDTH = 26;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_PADDING_X = 3;
	private static final int EMI_SEARCH_FALLBACK_WIDTH = 160;
	private static final int EMI_SEARCH_FALLBACK_HEIGHT = 18;
	private static final int EMI_SEARCH_FALLBACK_Y_OFFSET = 21;

	@Nullable
	private static Rect2i lastRenderedButtonBounds;
	private static int lastRenderedContainerId = -1;

	private NearbyCraftingEmiOverlayButtonEvents() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
		if (event.getButton() != 0) {
			return;
		}
		if (!(event.getScreen() instanceof NearbyCraftingScreen screen)) {
			return;
		}
		if (!NearbyCraftingEmiCraftableFilterController.isRuntimeAvailable()) {
			return;
		}

		Rect2i buttonBounds = getButtonBoundsWithCache(screen);
		if (buttonBounds != null && buttonBounds.contains((int) event.getMouseX(), (int) event.getMouseY())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
		if (event.getButton() != 0) {
			return;
		}
		if (!(event.getScreen() instanceof NearbyCraftingScreen screen)) {
			return;
		}
		if (!NearbyCraftingEmiCraftableFilterController.isRuntimeAvailable()) {
			return;
		}

		NearbyCraftingMenu menu = screen.getMenu();
		Rect2i buttonBounds = getButtonBoundsWithCache(screen);
		if (buttonBounds == null || !buttonBounds.contains((int) event.getMouseX(), (int) event.getMouseY())) {
			if (NearbyCraftingEmiCraftableFilterController.isEnabledFor(menu.containerId)) {
				boolean handled = NearbyCraftingEmiCraftableFilterController.handleIngredientClick(
						menu,
						event.getMouseX(),
						event.getMouseY(),
						event.getButton()
				);
				if (handled) {
					screen.requestImmediateSourceSyncAndRefresh();
					event.setCanceled(true);
					return;
				}
			}
			return;
		}

		if (NearbyCraftingEmiCraftableFilterController.isTransitionBlockingInput()) {
			event.setCanceled(true);
			return;
		}

		boolean nextEnabled = !NearbyCraftingEmiCraftableFilterController.isEnabledFor(menu.containerId);
		NearbyCraftingEmiCraftableFilterController.setEnabled(menu, nextEnabled);
		if (NearbyCraftingConfig.CLIENT.rememberToggleStates.get()) {
			NearbyCraftingConfig.CLIENT.emiCraftableOnlyEnabled.set(nextEnabled);
		}
		if (nextEnabled) {
			screen.requestImmediateSourceSyncAndRefresh();
		}

		screen.showInfoStatusMessage(Component.translatable(
				nextEnabled ? "nearbycrafting.emi.updating.enable" : "nearbycrafting.emi.updating.disable"
		));
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		event.setCanceled(true);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
		if (!(event.getScreen() instanceof NearbyCraftingScreen screen)) {
			lastRenderedButtonBounds = null;
			lastRenderedContainerId = -1;
			return;
		}
		if (!NearbyCraftingEmiCraftableFilterController.isRuntimeAvailable()) {
			lastRenderedButtonBounds = null;
			lastRenderedContainerId = -1;
			return;
		}
		NearbyCraftingEmiCraftableFilterController.enforceCraftableSidebarIfEnabled(screen.getMenu());

		NearbyCraftingMenu menu = screen.getMenu();
		Rect2i buttonBounds = getButtonBounds(screen);
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
		boolean enabled = NearbyCraftingEmiCraftableFilterController.isEnabledFor(menu.containerId);
		boolean transition = NearbyCraftingEmiCraftableFilterController.isTransitionBlockingInput();

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

		if (transition) {
			event.getGuiGraphics().fill(
					buttonBounds.getX(),
					buttonBounds.getY(),
					buttonBounds.getX() + buttonBounds.getWidth(),
					buttonBounds.getY() + buttonBounds.getHeight(),
					0x88000000
			);
		}

		if (hovered) {
			Component tooltip = enabled
					? Component.translatable("gui.recipebook.toggleRecipes.craftable")
					: Component.translatable("gui.recipebook.toggleRecipes.all");
			event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
		}
	}

	@Nullable
	private static Rect2i getButtonBounds(NearbyCraftingScreen screen) {
		Rect2i searchFieldBounds = NearbyCraftingEmiCraftableFilterController.getEmiSearchFieldBounds();
		if (searchFieldBounds == null) {
			// Fallback for production/obfuscated runtimes where reflective search widget bounds are unavailable.
			int searchX = (screen.width - EMI_SEARCH_FALLBACK_WIDTH) / 2;
			int searchY = screen.height - EMI_SEARCH_FALLBACK_Y_OFFSET;
			searchFieldBounds = new Rect2i(searchX, searchY, EMI_SEARCH_FALLBACK_WIDTH, EMI_SEARCH_FALLBACK_HEIGHT);
		}

		int x = searchFieldBounds.getX() - BUTTON_WIDTH - BUTTON_PADDING_X;
		int y = searchFieldBounds.getY() + (searchFieldBounds.getHeight() - BUTTON_HEIGHT) / 2;
		return new Rect2i(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	@Nullable
	private static Rect2i getButtonBoundsWithCache(NearbyCraftingScreen screen) {
		NearbyCraftingMenu menu = screen.getMenu();
		Rect2i liveBounds = getButtonBounds(screen);
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
}
