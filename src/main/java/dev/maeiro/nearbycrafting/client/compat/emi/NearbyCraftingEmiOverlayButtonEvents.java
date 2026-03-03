package dev.maeiro.nearbycrafting.client.compat.emi;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.client.compat.RecipeSourceSnapshotCache;
import dev.maeiro.nearbycrafting.client.screen.NearbyCraftingScreen;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.networking.C2SRequestAdvancedBackpackRecipeBookSources;
import dev.maeiro.nearbycrafting.networking.NearbyCraftingNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
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
	private static final long BACKPACK_SOURCE_SYNC_INTERVAL_MS = 300L;

	@Nullable
	private static Rect2i lastRenderedButtonBounds;
	private static int lastRenderedContainerId = -1;
	private static long lastBackpackSourceSyncAtMs = 0L;
	private static int lastBackpackSourceSyncContainerId = -1;

	private NearbyCraftingEmiOverlayButtonEvents() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onScreenInit(ScreenEvent.Init.Post event) {
		OverlayContext context = resolveContext(event.getScreen());
		if (context == null || !context.isBackpack()) {
			return;
		}
		if (!NearbyCraftingEmiCraftableFilterController.isRuntimeAvailable()) {
			return;
		}

		if (NearbyCraftingConfig.CLIENT.rememberToggleStates.get() && NearbyCraftingConfig.CLIENT.emiCraftableOnlyEnabled.get()) {
			NearbyCraftingEmiCraftableFilterController.setEnabled(context.containerId(), true);
			requestBackpackSourceSync(context.containerId(), true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
		if (event.getButton() != 0 || !NearbyCraftingEmiCraftableFilterController.isRuntimeAvailable()) {
			return;
		}

		OverlayContext context = resolveContext(event.getScreen());
		if (context == null) {
			return;
		}

		Rect2i buttonBounds = getButtonBoundsWithCache(context);
		if (buttonBounds != null && buttonBounds.contains((int) event.getMouseX(), (int) event.getMouseY())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
		if (event.getButton() != 0 || !NearbyCraftingEmiCraftableFilterController.isRuntimeAvailable()) {
			return;
		}

		OverlayContext context = resolveContext(event.getScreen());
		if (context == null) {
			return;
		}

		Rect2i buttonBounds = getButtonBoundsWithCache(context);
		if (buttonBounds == null || !buttonBounds.contains((int) event.getMouseX(), (int) event.getMouseY())) {
			if (NearbyCraftingEmiCraftableFilterController.isEnabledFor(context.containerId())) {
				boolean handled = context.isBackpack()
						? NearbyCraftingEmiCraftableFilterController.handleIngredientClickBackpack(
						context.containerId(),
						context.screen(),
						event.getMouseX(),
						event.getMouseY(),
						event.getButton()
				)
						: NearbyCraftingEmiCraftableFilterController.handleIngredientClick(
						context.nearbyMenu(),
						event.getMouseX(),
						event.getMouseY(),
						event.getButton()
				);
				if (handled) {
					if (context.nearbyScreen() != null) {
						context.nearbyScreen().requestImmediateSourceSyncAndRefresh();
					} else {
						requestBackpackSourceSync(context.containerId(), false);
					}
					event.setCanceled(true);
				}
			}
			return;
		}

		if (NearbyCraftingEmiCraftableFilterController.isTransitionBlockingInput()) {
			event.setCanceled(true);
			return;
		}

		boolean nextEnabled = !NearbyCraftingEmiCraftableFilterController.isEnabledFor(context.containerId());
		if (context.nearbyMenu() != null) {
			NearbyCraftingEmiCraftableFilterController.setEnabled(context.nearbyMenu(), nextEnabled);
		} else {
			NearbyCraftingEmiCraftableFilterController.setEnabled(context.containerId(), nextEnabled);
		}

		if (NearbyCraftingConfig.CLIENT.rememberToggleStates.get()) {
			NearbyCraftingConfig.CLIENT.emiCraftableOnlyEnabled.set(nextEnabled);
		}
		if (nextEnabled) {
			if (context.nearbyScreen() != null) {
				context.nearbyScreen().requestImmediateSourceSyncAndRefresh();
			} else {
				requestBackpackSourceSync(context.containerId(), true);
			}
		}

		Component statusMessage = Component.translatable(
				nextEnabled ? "nearbycrafting.emi.updating.enable" : "nearbycrafting.emi.updating.disable"
		);
		if (context.nearbyScreen() != null) {
			context.nearbyScreen().showInfoStatusMessage(statusMessage);
		} else if (Minecraft.getInstance().gui != null) {
			Minecraft.getInstance().gui.setOverlayMessage(statusMessage, false);
		}

		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		event.setCanceled(true);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
		if (!NearbyCraftingEmiCraftableFilterController.isRuntimeAvailable()) {
			clearCachedBounds();
			return;
		}

		OverlayContext context = resolveContext(event.getScreen());
		if (context == null) {
			clearCachedBounds();
			return;
		}

		if (context.nearbyMenu() != null) {
			NearbyCraftingEmiCraftableFilterController.enforceCraftableSidebarIfEnabled(context.nearbyMenu());
		} else {
			NearbyCraftingEmiCraftableFilterController.enforceCraftableSidebarIfEnabled(context.containerId());
			NearbyCraftingEmiCraftableFilterController.refreshIfEnabledBackpack(context.screen(), context.containerId());
			if (NearbyCraftingEmiCraftableFilterController.isEnabledFor(context.containerId())) {
				requestBackpackSourceSync(context.containerId(), false);
			}
		}

		Rect2i buttonBounds = getButtonBounds(context);
		if (buttonBounds == null) {
			clearCachedBounds();
			return;
		}

		lastRenderedButtonBounds = buttonBounds;
		lastRenderedContainerId = context.containerId();

		int mouseX = (int) event.getMouseX();
		int mouseY = (int) event.getMouseY();
		boolean hovered = buttonBounds.contains(mouseX, mouseY);
		boolean enabled = NearbyCraftingEmiCraftableFilterController.isEnabledFor(context.containerId());
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

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onScreenClosing(ScreenEvent.Closing event) {
		OverlayContext context = resolveContext(event.getScreen());
		if (context == null || context.isBackpack()) {
			if (context != null) {
				NearbyCraftingEmiCraftableFilterController.handleMenuClosed(context.containerId());
				RecipeSourceSnapshotCache.clear(context.containerId());
			}
			clearCachedBounds();
		}
	}

	private static void requestBackpackSourceSync(int containerId, boolean force) {
		long now = System.currentTimeMillis();
		if (!force && containerId == lastBackpackSourceSyncContainerId && now - lastBackpackSourceSyncAtMs < BACKPACK_SOURCE_SYNC_INTERVAL_MS) {
			return;
		}
		lastBackpackSourceSyncContainerId = containerId;
		lastBackpackSourceSyncAtMs = now;
		NearbyCraftingNetwork.CHANNEL.sendToServer(new C2SRequestAdvancedBackpackRecipeBookSources(containerId));
	}

	@Nullable
	private static Rect2i getButtonBounds(OverlayContext context) {
		Rect2i searchFieldBounds = NearbyCraftingEmiCraftableFilterController.getEmiSearchFieldBounds();
		if (searchFieldBounds == null) {
			int screenWidth = context.screen() == null ? Minecraft.getInstance().getWindow().getGuiScaledWidth() : context.screen().width;
			int screenHeight = context.screen() == null ? Minecraft.getInstance().getWindow().getGuiScaledHeight() : context.screen().height;
			int searchX = (screenWidth - EMI_SEARCH_FALLBACK_WIDTH) / 2;
			int searchY = screenHeight - EMI_SEARCH_FALLBACK_Y_OFFSET;
			searchFieldBounds = new Rect2i(searchX, searchY, EMI_SEARCH_FALLBACK_WIDTH, EMI_SEARCH_FALLBACK_HEIGHT);
		}

		int x = searchFieldBounds.getX() - BUTTON_WIDTH - BUTTON_PADDING_X;
		int y = searchFieldBounds.getY() + (searchFieldBounds.getHeight() - BUTTON_HEIGHT) / 2;
		return new Rect2i(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	@Nullable
	private static Rect2i getButtonBoundsWithCache(OverlayContext context) {
		Rect2i liveBounds = getButtonBounds(context);
		if (liveBounds != null) {
			lastRenderedButtonBounds = liveBounds;
			lastRenderedContainerId = context.containerId();
			return liveBounds;
		}

		if (lastRenderedButtonBounds != null && lastRenderedContainerId == context.containerId()) {
			return lastRenderedButtonBounds;
		}
		return null;
	}

	private static void clearCachedBounds() {
		lastRenderedButtonBounds = null;
		lastRenderedContainerId = -1;
	}

	@Nullable
	private static OverlayContext resolveContext(Screen screen) {
		if (screen instanceof NearbyCraftingScreen nearbyScreen) {
			NearbyCraftingMenu menu = nearbyScreen.getMenu();
			return new OverlayContext(screen, menu.containerId, nearbyScreen, menu, false);
		}

		@Nullable BackpackContainer backpackMenu = AdvancedBackpackScreenHelper.getBackpackMenu(screen).orElse(null);
		if (backpackMenu != null && AdvancedBackpackScreenHelper.getAdvancedContainer(backpackMenu).isPresent()) {
			return new OverlayContext(screen, backpackMenu.containerId, null, null, true);
		}
		return null;
	}

	private record OverlayContext(
			Screen screen,
			int containerId,
			@Nullable NearbyCraftingScreen nearbyScreen,
			@Nullable NearbyCraftingMenu nearbyMenu,
			boolean isBackpack
	) {
	}
}
