package dev.maeiro.proximitycrafting.client.screen;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.net.ProximityClientServices;
import dev.maeiro.proximitycrafting.client.presenter.IngredientsPanelContext;
import dev.maeiro.proximitycrafting.client.presenter.IngredientsPanelEntry;
import dev.maeiro.proximitycrafting.client.presenter.IngredientsPanelPresenter;
import dev.maeiro.proximitycrafting.client.presenter.IngredientsPanelUpdateResult;
import dev.maeiro.proximitycrafting.client.session.ClientRecipeSessionState;
import dev.maeiro.proximitycrafting.client.session.RecipeActionFeedbackApplyResult;
import dev.maeiro.proximitycrafting.client.session.SourceSnapshotApplyResult;
import dev.maeiro.proximitycrafting.client.compat.emi.ProximityCraftingEmiCraftableFilterController;
import dev.maeiro.proximitycrafting.client.compat.jei.ProximityCraftingJeiCraftableFilterController;
import dev.maeiro.proximitycrafting.config.ClientPreferences;
import dev.maeiro.proximitycrafting.config.ClientUiState;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class ProximityCraftingScreen extends AbstractContainerScreen<ProximityCraftingMenu> implements RecipeUpdateListener {
	private static final ResourceLocation CRAFTING_TABLE_LOCATION = new ResourceLocation("textures/gui/container/crafting_table.png");
	private static final ResourceLocation RECIPE_BUTTON_LOCATION = new ResourceLocation("textures/gui/recipe_button.png");
	private static final ResourceLocation PROXIMITY_ITEMS_TOGGLE_ON_ICON = new ResourceLocation("proximitycrafting", "icon/toggle_on.png");
	private static final ResourceLocation PROXIMITY_ITEMS_TOGGLE_OFF_ICON = new ResourceLocation("proximitycrafting", "icon/toggle_off.png");
	private static final int RECIPE_BOOK_SOURCE_SYNC_INTERVAL_TICKS = 20;
	private static final int RECIPE_BOOK_SOURCE_SYNC_INTERVAL_TICKS_EMI_CRAFTABLE = 80;
	private static final long RECIPE_BOOK_SOURCE_SYNC_MIN_INTERVAL_MS = 90L;
	private static final long RECIPE_ACTION_SEND_INTERVAL_MS = 0L;
	private static final long RECIPE_ACTION_IN_FLIGHT_TIMEOUT_MS = 12000L;
	private static final long RECIPE_BOOK_SOURCE_SYNC_ACTION_COOLDOWN_MS = 400L;
	private static final long STALE_HOVERED_SLOT_LOG_INTERVAL_MS = 250L;
	private static final long SCROLL_DEBUG_LOG_THROTTLE_MS = 120L;
	private static final long SCROLL_PERF_LOG_INTERVAL_MS = 2000L;
	private static final long ACTION_QUEUE_LOG_INTERVAL_MS = 250L;
	private static final double SCROLL_SLOW_EVENT_MS = 4.0D;
	private static final int MAX_SCROLL_STEPS_PER_EVENT = 3;
	private static final int MAX_ABS_ADJUST_STEPS_PER_PACKET = 2;
	private static final int RECIPE_ACTION_MAX_ABS_STEPS = 256;
	private static final int STATUS_COLOR_SUCCESS = 0x55FF55;
	private static final int STATUS_COLOR_FAILURE = 0xFF5555;
	private static final int STATUS_COLOR_INFO = 0xFFFFFF;
	private static final int PROXIMITY_PANEL_WIDTH = 74;
	private static final int PROXIMITY_PANEL_PADDING = 8;
	private static final int PROXIMITY_PANEL_RECIPE_BOOK_EXTRA_SHIFT = 26;
	private static final int TOGGLE_SIZE = 18;
	private static final int CLEAR_GRID_TOGGLE_SIZE = 9;
	private static final int CLEAR_GRID_TOGGLE_SPACING = 2;
	private static final int TOGGLE_ICON_SIZE = 16;
	private static final int TOGGLE_ICON_TEX_WIDTH = 16;
	private static final int TOGGLE_ICON_TEX_HEIGHT = 16;
	private static final int PANEL_HEADER_COLOR = 0xFF202020;
	private static final int PANEL_COUNT_COLOR = 0xFF404040;
	private static final int PANEL_INNER_BG_COLOR = 0xFFC6C6C6;
	private static final int PANEL_OUTER_BG_COLOR = 0xFFB8B8B8;
	private static final int PANEL_LINE_LIGHT = 0xFFFFFFFF;
	private static final int PANEL_LINE_DARK = 0xFF555555;
	private static final int RESULT_SLOT_MENU_INDEX = 0;
	private static final int CRAFT_SLOT_MENU_END_EXCLUSIVE = 10;
	private static final int CRAFT_GRID_X = 30;
	private static final int CRAFT_GRID_Y = 17;
	private static final int CRAFT_GRID_SIZE = 54;
	private static final int RESULT_SLOT_X = 124;
	private static final int RESULT_SLOT_Y = 35;
	private static final int RESULT_SLOT_SIZE = 18;
	private static final long PANEL_PERF_LOG_INTERVAL_MS = 2000L;
	private static final long PANEL_SLOW_FRAME_LOG_INTERVAL_MS = 500L;
	private static final double PANEL_PERF_SLOW_FRAME_MS = 6.0D;
	private static final int AUTO_REFILL_TOGGLE_SIZE = 9;
	private static final int AUTO_REFILL_TOGGLE_OFFSET_BASE_X = 1;
	private static final int AUTO_REFILL_TOGGLE_OFFSET_BASE_Y = 21;
	private static final int AUTO_REFILL_TOGGLE_SCREEN_MOVE_X = -18;
	private static final int AUTO_REFILL_TOGGLE_SCREEN_MOVE_Y = 7;
	private final RecipeBookComponent recipeBookComponent = new RecipeBookComponent();
	private boolean widthTooNarrow;
	private boolean vanillaRecipeBookSuppressedByEmi;
	private int recipeBookSourceSyncTicker = 0;
	private int deferredRefreshTicks = 0;
	private boolean showProximityItemsPanel = true;
	private Component statusMessage;
	private long statusMessageUntilMs = 0L;
	private int statusMessageColor = STATUS_COLOR_INFO;
	private IngredientsPanelEntry hoveredProximityEntry;
	@Nullable
	private ResourceLocation localScrollRecipeId;
	private long scrollPerfWindowStartMs = 0L;
	private int scrollPerfEvents = 0;
	private int scrollPerfHandledEvents = 0;
	private long scrollPerfTotalNs = 0L;
	private double scrollPerfMaxMs = 0.0D;
	private long panelPerfWindowStartMs = 0L;
	private int panelPerfSamples = 0;
	private long panelPerfCollectNs = 0L;
	private long panelPerfRenderNs = 0L;
	private double panelPerfMaxFrameMs = 0.0D;
	private int panelPerfLastEntryCount = 0;
	private int panelPerfLastSourceEntryCount = 0;
	private long lastPanelSlowFrameLogAtMs = 0L;
	private final IngredientsPanelPresenter ingredientsPanelPresenter = new IngredientsPanelPresenter();
	private long lastStaleHoveredSlotLogAtMs = 0L;
	private long lastScrollDebugLogAtMs = 0L;
	private int suppressedScrollDebugLogs = 0;
	private long lastActionQueueLogAtMs = 0L;
	private final ClientRecipeSessionState recipeSessionState = new ClientRecipeSessionState(
			RECIPE_BOOK_SOURCE_SYNC_MIN_INTERVAL_MS,
			RECIPE_ACTION_SEND_INTERVAL_MS,
			RECIPE_ACTION_IN_FLIGHT_TIMEOUT_MS,
			RECIPE_ACTION_MAX_ABS_STEPS
	);
	private final IngredientsPanelContext ingredientsPanelContext = new ScreenIngredientsPanelContext();

	public ProximityCraftingScreen(ProximityCraftingMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Override
	protected void init() {
		super.init();
		sendClientPreferencesUpdate();
		requestRecipeBookSourceSync();

		this.widthTooNarrow = this.width < 379;
		this.vanillaRecipeBookSuppressedByEmi = ProximityCraftingEmiCraftableFilterController.isRuntimeAvailable();
		if (this.vanillaRecipeBookSuppressedByEmi) {
			ProximityCraftingEmiCraftableFilterController.enforceIndexOnlyMode();
			this.leftPos = (this.width - this.imageWidth) / 2;
		} else {
			this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
			this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
			this.addRenderableWidget(new ImageButton(this.leftPos + 5, this.height / 2 - 49, 20, 18, 0, 0, 19, RECIPE_BUTTON_LOCATION, button -> {
				this.recipeBookComponent.toggleVisibility();
				this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
				button.setPosition(this.leftPos + 5, this.height / 2 - 49);
			}));
			this.addWidget(this.recipeBookComponent);
			this.setInitialFocus(this.recipeBookComponent);
		}
		this.titleLabelX = 29;

		applyRememberedUiState();
		ProximityCraftingJeiCraftableFilterController.prewarmSnapshot(this.menu, "screen_init");
	}

	@Override
	public void containerTick() {
		super.containerTick();
		processQueuedRecipeActions("tick");
		flushDeferredRecipeBookRefreshAfterAction("tick");
		if (!this.vanillaRecipeBookSuppressedByEmi) {
			this.recipeBookComponent.tick();
		} else {
			ProximityCraftingEmiCraftableFilterController.enforceIndexOnlyMode();
		}
		if (deferredRefreshTicks > 0) {
			deferredRefreshTicks--;
			if (deferredRefreshTicks == 0) {
				refreshRecipeBookFromSyncedSources();
			}
		}
		long nowMs = System.currentTimeMillis();
		if (!shouldDeferPeriodicSourceSync(nowMs)) {
			recipeBookSourceSyncTicker++;
			int syncIntervalTicks = getCurrentSourceSyncIntervalTicks();
			if (recipeBookSourceSyncTicker >= syncIntervalTicks) {
				recipeBookSourceSyncTicker = 0;
				requestRecipeBookSourceSync();
			}
		} else {
			recipeBookSourceSyncTicker = 0;
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		hoveredProximityEntry = null;
		this.renderBackground(guiGraphics);
		if (this.vanillaRecipeBookSuppressedByEmi) {
			super.render(guiGraphics, mouseX, mouseY, partialTick);
		} else {
			if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
				this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
				this.recipeBookComponent.render(guiGraphics, mouseX, mouseY, partialTick);
			} else {
				this.recipeBookComponent.render(guiGraphics, mouseX, mouseY, partialTick);
				super.render(guiGraphics, mouseX, mouseY, partialTick);
				this.recipeBookComponent.renderGhostRecipe(guiGraphics, this.leftPos, this.topPos, true, partialTick);
			}
		}
		renderProximityItemsToggle(guiGraphics, mouseX, mouseY);
		renderClearGridToggle(guiGraphics, mouseX, mouseY);
		renderAutoRefillToggle(guiGraphics, mouseX, mouseY);
		renderProximityItemsPanel(guiGraphics, mouseX, mouseY);

		this.renderTooltip(guiGraphics, mouseX, mouseY);
		renderProximityItemsTooltip(guiGraphics, mouseX, mouseY);
		renderClearGridTooltip(guiGraphics, mouseX, mouseY);
		renderAutoRefillTooltip(guiGraphics, mouseX, mouseY);
		if (!this.vanillaRecipeBookSuppressedByEmi) {
			this.recipeBookComponent.renderTooltip(guiGraphics, this.leftPos, this.topPos, mouseX, mouseY);
		}
		renderStatusMessage(guiGraphics);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = this.leftPos;
		int top = (this.height - this.imageHeight) / 2;
		guiGraphics.blit(CRAFTING_TABLE_LOCATION, left, top, 0, 0, this.imageWidth, this.imageHeight);
	}

	@Override
	protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
		if (this.vanillaRecipeBookSuppressedByEmi) {
			return super.isHovering(x, y, width, height, mouseX, mouseY);
		}
		return (!this.widthTooNarrow || !this.recipeBookComponent.isVisible()) && super.isHovering(x, y, width, height, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isMouseOverProximityItemsToggle(mouseX, mouseY)) {
			showProximityItemsPanel = !showProximityItemsPanel;
			saveProximityItemsPanelState(showProximityItemsPanel);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		if (button == 0 && isMouseOverClearGridToggle(mouseX, mouseY)) {
			sendClearGridPacket("clear_grid_button");
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		if (button == 0 && isMouseOverAutoRefillToggle(mouseX, mouseY)) {
			ClientPreferences nextPreferences = ProximityCraftingConfig.clientPreferences()
					.withAutoRefillAfterCraft(!ProximityCraftingConfig.clientPreferences().autoRefillAfterCraft());
			ProximityCraftingConfig.setClientPreferences(nextPreferences);
			sendClientPreferencesUpdate();
			showInfoStatusMessage(Component.translatable(
					nextPreferences.autoRefillAfterCraft()
							? "proximitycrafting.auto_refill.enabled"
							: "proximitycrafting.auto_refill.disabled"
			));
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}

		if (!this.vanillaRecipeBookSuppressedByEmi) {
			if (this.recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
				this.setFocused(this.recipeBookComponent);
				return true;
			}
			return this.widthTooNarrow && this.recipeBookComponent.isVisible() || super.mouseClicked(mouseX, mouseY, button);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
		if (!tryHandleRecipeScaleScroll(mouseX, mouseY, scrollDelta, "screen")) {
			return super.mouseScrolled(mouseX, mouseY, scrollDelta);
		}
		return true;
	}

	public boolean tryHandleRecipeScaleScroll(double mouseX, double mouseY, double scrollDelta, String source) {
		long scrollStartNs = System.nanoTime();
		if (scrollDelta == 0.0D) {
			return finishScrollPerf(source, scrollDelta, false, scrollStartNs);
		}

		if (showProximityItemsPanel && getProximityPanelBounds().contains((int) mouseX, (int) mouseY)) {
			return finishScrollPerf(source, scrollDelta, false, scrollStartNs);
		}

		ResourceLocation hoveredRecipeId = resolveHoveredOverlayRecipeId(mouseX, mouseY);
		ResourceLocation activeRecipeId = getActiveRecipeIdForScroll();
		boolean activeRecipeLoaded = hasActiveRecipeLoadedInGrid();
		logScrollDebug(
				"[PROXC-SCROLL] client source={} resolvedHoverRecipe={} activeRecipe={} activeLoaded={} delta={}",
				source,
				hoveredRecipeId,
				activeRecipeId,
				activeRecipeLoaded,
				scrollDelta
		);
		if (scrollDelta > 0.0D && hoveredRecipeId != null && !activeRecipeLoaded) {
			int steps = normalizeScrollSteps(scrollDelta, source);
			logScrollDebug(
					"[PROXC-SCROLL] client source={} priming empty grid with hovered recipe={} steps={}",
					source,
					hoveredRecipeId,
					steps
			);
			sendRecipeFillPacket(hoveredRecipeId, false, "scroll_prime_empty");
			rememberPendingScrollRecipe(hoveredRecipeId);
			if (steps > 1) {
				sendAdjustPacket(steps - 1, "scroll_prime_empty_adjust");
			}
			return finishScrollPerf(source, scrollDelta, true, scrollStartNs);
		}
		if (hoveredRecipeId != null
				&& !hoveredRecipeId.equals(activeRecipeId)
				&& !isSameRecipeOutput(hoveredRecipeId, activeRecipeId)) {
			int steps = normalizeScrollSteps(scrollDelta, source);
			if (scrollDelta > 0.0D) {
				logScrollDebug(
						"[PROXC-SCROLL] client source={} switching active recipe from {} to {} with steps={}",
						source,
						activeRecipeId,
						hoveredRecipeId,
						steps
				);
				sendRecipeFillPacket(hoveredRecipeId, false, "scroll_switch_recipe");
				rememberPendingScrollRecipe(hoveredRecipeId);
				if (steps > 1) {
					sendAdjustPacket(steps - 1, "scroll_switch_recipe_adjust");
				}
				return finishScrollPerf(source, scrollDelta, true, scrollStartNs);
			} else {
				logScrollDebug(
						"[PROXC-SCROLL] client source={} ignoring negative scroll while switching recipe {} -> {}",
						source,
						activeRecipeId,
						hoveredRecipeId
				);
			}
		}

		int hoveredMenuSlot = getHoveredMenuSlotIndex();
		boolean overScaleArea = isMouseOverRecipeScaleArea(mouseX, mouseY);
		boolean shouldHandle = overScaleArea && activeRecipeLoaded;
		logScrollDebug(
				"[PROXC-SCROLL] client source={} delta={} hoveredSlot={} overScaleArea={} activeRecipeLoaded={} shouldHandle={} mouse=({}, {})",
				source,
				scrollDelta,
				hoveredMenuSlot,
				overScaleArea,
				activeRecipeLoaded,
				shouldHandle,
				(int) mouseX,
				(int) mouseY
		);

		if (!shouldHandle) {
			boolean handled = tryHandleOverlayHoverRecipeScroll(hoveredRecipeId, scrollDelta, activeRecipeLoaded, activeRecipeId);
			return finishScrollPerf(source, scrollDelta, handled, scrollStartNs);
		}

		int steps = normalizeScrollSteps(scrollDelta, source);
		int signedSteps = scrollDelta > 0.0D ? steps : -steps;
		logScrollDebug("[PROXC-SCROLL] client sending adjust packet steps={} menu={}", signedSteps, this.menu.containerId);
		sendAdjustPacket(signedSteps, "scroll_adjust_loaded_grid");
		return finishScrollPerf(source, scrollDelta, true, scrollStartNs);
	}

	private boolean tryHandleOverlayHoverRecipeScroll(
			@Nullable ResourceLocation hoveredRecipeId,
			double scrollDelta,
			boolean activeRecipeLoaded,
			@Nullable ResourceLocation activeRecipeId
	) {
		if (hoveredRecipeId == null) {
			return false;
		}

		int steps = normalizeScrollSteps(scrollDelta, "overlay_hover");
		if (!activeRecipeLoaded) {
			if (scrollDelta <= 0.0D) {
				return false;
			}

			logScrollDebug(
					"[PROXC-SCROLL] client source=overlay_hover recipe={} steps={} mode=prime_empty_grid",
					hoveredRecipeId,
					steps
			);

			sendRecipeFillPacket(hoveredRecipeId, false, "overlay_hover_prime");
			rememberPendingScrollRecipe(hoveredRecipeId);
			if (steps > 1) {
				sendAdjustPacket(steps - 1, "overlay_hover_prime_adjust");
			}
			return true;
		}

		boolean matchesActiveRecipe = hoveredRecipeId.equals(activeRecipeId) || isSameRecipeOutput(hoveredRecipeId, activeRecipeId);
		if (!matchesActiveRecipe) {
			return false;
		}

		int signedSteps = scrollDelta > 0.0D ? steps : -steps;
		logScrollDebug(
				"[PROXC-SCROLL] client source=overlay_hover recipe={} activeRecipe={} steps={} mode=adjust_loaded_grid",
				hoveredRecipeId,
				activeRecipeId,
				signedSteps
		);
		sendAdjustPacket(signedSteps, "overlay_hover_adjust");
		return true;
	}

	private int normalizeScrollSteps(double scrollDelta, String source) {
		int rawSteps = Math.max(1, (int) Math.round(Math.abs(scrollDelta)));
		int normalizedSteps = Math.min(rawSteps, MAX_SCROLL_STEPS_PER_EVENT);
		if (rawSteps != normalizedSteps) {
			logScrollDebug(
					"[PROXC-SCROLL] client source={} clamped scroll steps raw={} clamped={} delta={}",
					source,
					rawSteps,
					normalizedSteps,
					scrollDelta
			);
		}
		return normalizedSteps;
	}

	@Nullable
	private ResourceLocation resolveHoveredOverlayRecipeId(double mouseX, double mouseY) {
		ResourceLocation hoveredRecipeId = null;
		if (ProximityCraftingEmiCraftableFilterController.isEnabledFor(this.menu.containerId)) {
			hoveredRecipeId = ProximityCraftingEmiCraftableFilterController.resolveHoveredRecipeId(this.menu, mouseX, mouseY);
			if (hoveredRecipeId != null) {
				logScrollDebug("[PROXC-SCROLL] hover recipe resolved from EMI: {}", hoveredRecipeId);
				return hoveredRecipeId;
			}
		}
		if (ProximityCraftingJeiCraftableFilterController.isEnabledFor(this.menu.containerId)) {
			hoveredRecipeId = ProximityCraftingJeiCraftableFilterController.resolveHoveredRecipeId(this.menu);
			if (hoveredRecipeId != null) {
				logScrollDebug("[PROXC-SCROLL] hover recipe resolved from JEI: {}", hoveredRecipeId);
				return hoveredRecipeId;
			}
		}
		hoveredRecipeId = resolveHoveredVanillaRecipeBookRecipeId();
		logScrollDebug("[PROXC-SCROLL] hover recipe resolved from VANILLA book: {}", hoveredRecipeId);
		return hoveredRecipeId;
	}

	@Nullable
	private ResourceLocation resolveHoveredVanillaRecipeBookRecipeId() {
		if (this.vanillaRecipeBookSuppressedByEmi) {
			return null;
		}
		if (!this.recipeBookComponent.isVisible()) {
			logScrollDebug("[PROXC-SCROLL] vanilla hover resolve skipped: recipe book not visible");
			return null;
		}

		try {
			Object recipeBookPage = getFieldValue(this.recipeBookComponent, "recipeBookPage");
			if (recipeBookPage == null) {
				Field recipeBookPageField = findFieldByTypeNameContains(this.recipeBookComponent.getClass(), "RecipeBookPage");
				if (recipeBookPageField != null) {
					recipeBookPage = recipeBookPageField.get(this.recipeBookComponent);
				}
			}
			if (recipeBookPage == null) {
				logScrollDebug("[PROXC-SCROLL] vanilla hover resolve failed: recipeBookPage field missing/null");
				return null;
			}

			Object hoveredButton = getFieldValue(recipeBookPage, "hoveredButton");
			if (hoveredButton == null) {
				Field hoveredButtonField = findFieldByTypeNameContains(recipeBookPage.getClass(), "RecipeButton");
				if (hoveredButtonField != null) {
					hoveredButton = hoveredButtonField.get(recipeBookPage);
				}
			}
			if (hoveredButton == null) {
				logScrollDebug("[PROXC-SCROLL] vanilla hover resolve: no hovered recipe button");
				return null;
			}

			Method getRecipeMethod = findMethod(hoveredButton.getClass(), "getRecipe", 0);
			if (getRecipeMethod == null) {
				getRecipeMethod = findNoArgMethodReturningTypeNameContains(hoveredButton.getClass(), "RecipeHolder");
			}
			if (getRecipeMethod == null) {
				ResourceLocation fieldExtractedId = tryResolveRecipeIdFromRecipeButtonFields(hoveredButton);
				if (fieldExtractedId != null) {
					logScrollDebug("[PROXC-SCROLL] vanilla hover resolve success via RecipeButton fields: {}", fieldExtractedId);
					return fieldExtractedId;
				}
				logScrollDebug("[PROXC-SCROLL] vanilla hover resolve failed: getRecipe method not found on {}", hoveredButton.getClass().getName());
				return null;
			}

			Object recipeHolder = getRecipeMethod.invoke(hoveredButton);
			if (recipeHolder == null) {
				logScrollDebug("[PROXC-SCROLL] vanilla hover resolve failed: getRecipe returned null");
				return null;
			}
			if (recipeHolder.getClass().getSimpleName().contains("RecipeCollection")
					|| recipeHolder.getClass().getName().contains("recipebook.RecipeCollection")) {
				ResourceLocation collectionId = tryResolveRecipeIdFromRecipeCollection(recipeHolder, hoveredButton);
				if (collectionId != null) {
					logScrollDebug("[PROXC-SCROLL] vanilla hover resolve success via RecipeCollection: {}", collectionId);
					return collectionId;
				}
			}

			Method idMethod = findMethod(recipeHolder.getClass(), "id", 0);
			if (idMethod == null) {
				idMethod = findNoArgMethodReturningTypeNameContains(recipeHolder.getClass(), "ResourceLocation");
			}
			if (idMethod != null) {
				Object recipeId = idMethod.invoke(recipeHolder);
				if (recipeId instanceof ResourceLocation resourceLocation) {
					logScrollDebug("[PROXC-SCROLL] vanilla hover resolve success via method id(): {}", resourceLocation);
					return resourceLocation;
				}
			}

			Object recipeIdFieldValue = getFieldValue(recipeHolder, "id");
			if (recipeIdFieldValue instanceof ResourceLocation resourceLocation) {
				logScrollDebug("[PROXC-SCROLL] vanilla hover resolve success via field id: {}", resourceLocation);
				return resourceLocation;
			}
			logScrollDebug("[PROXC-SCROLL] vanilla hover resolve failed: could not extract recipe id from {}", recipeHolder.getClass().getName());
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			logScrollDebug("[PROXC-SCROLL] vanilla hover resolve exception: {}", ignored.toString());
		}
		return null;
	}

	@Nullable
	private ResourceLocation tryResolveRecipeIdFromRecipeButtonFields(Object recipeButton) {
		Integer currentIndex = tryGetRecipeButtonCurrentIndex(recipeButton);
		for (Field field : getAllFields(recipeButton.getClass())) {
			field.setAccessible(true);
			Object value;
			try {
				value = field.get(recipeButton);
			} catch (IllegalAccessException ignored) {
				continue;
			}
			ResourceLocation directId = tryExtractRecipeId(value);
			if (directId != null) {
				return directId;
			}
			if (value != null && (value.getClass().getSimpleName().contains("RecipeCollection")
					|| value.getClass().getName().contains("recipebook.RecipeCollection"))) {
				ResourceLocation collectionId = tryResolveRecipeIdFromRecipeCollection(value, recipeButton);
				if (collectionId != null) {
					return collectionId;
				}
			}
			if (value instanceof List<?> listValue && !listValue.isEmpty()) {
				int index = currentIndex != null ? currentIndex : 0;
				if (index < 0) {
					index = 0;
				}
				if (index >= listValue.size()) {
					index = listValue.size() - 1;
				}
				ResourceLocation listId = tryExtractRecipeId(listValue.get(index));
				if (listId != null) {
					return listId;
				}
				for (Object listEntry : listValue) {
					ResourceLocation candidate = tryExtractRecipeId(listEntry);
					if (candidate != null) {
						return candidate;
					}
				}
			}
		}
		return null;
	}

	@Nullable
	private ResourceLocation tryResolveRecipeIdFromRecipeCollection(Object recipeCollection, @Nullable Object recipeButton) {
		Integer currentIndex = recipeButton == null ? null : tryGetRecipeButtonCurrentIndex(recipeButton);
		int preferredIndex = currentIndex == null ? 0 : Math.max(0, currentIndex);
		for (Method method : recipeCollection.getClass().getMethods()) {
			if (!List.class.isAssignableFrom(method.getReturnType())) {
				continue;
			}
			if (Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			Object rawList = null;
			try {
				if (method.getParameterCount() == 0) {
					rawList = method.invoke(recipeCollection);
				} else if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == boolean.class) {
					rawList = method.invoke(recipeCollection, true);
					if (!(rawList instanceof List<?> list && !list.isEmpty())) {
						rawList = method.invoke(recipeCollection, false);
					}
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				continue;
			}
			if (!(rawList instanceof List<?> listValue) || listValue.isEmpty()) {
				continue;
			}
			int index = Math.min(preferredIndex, listValue.size() - 1);
			ResourceLocation indexedId = tryExtractRecipeId(listValue.get(index));
			if (indexedId != null) {
				return indexedId;
			}
			for (Object entry : listValue) {
				ResourceLocation candidate = tryExtractRecipeId(entry);
				if (candidate != null) {
					return candidate;
				}
			}
		}
		return null;
	}

	@Nullable
	private Integer tryGetRecipeButtonCurrentIndex(Object recipeButton) {
		Object namedIndex = getFieldValue(recipeButton, "currentIndex");
		if (namedIndex instanceof Integer indexValue) {
			return indexValue;
		}
		for (Field field : getAllFields(recipeButton.getClass())) {
			if (field.getType() != int.class && field.getType() != Integer.class) {
				continue;
			}
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			String fieldName = field.getName();
			if (!fieldName.toLowerCase().contains("index")) {
				continue;
			}
			field.setAccessible(true);
			try {
				Object value = field.get(recipeButton);
				if (value instanceof Integer indexValue) {
					return indexValue;
				}
			} catch (IllegalAccessException ignored) {
			}
		}
		return null;
	}

	@Nullable
	private ResourceLocation tryExtractRecipeId(@Nullable Object maybeRecipeLike) {
		if (maybeRecipeLike == null) {
			return null;
		}
		if (maybeRecipeLike instanceof ResourceLocation directResourceLocation) {
			return isValidScrollRecipeId(directResourceLocation) ? directResourceLocation : null;
		}

		Class<?> valueClass = maybeRecipeLike.getClass();
		Method idMethod = findMethod(valueClass, "id", 0);
		if (idMethod == null) {
			idMethod = findNoArgMethodReturningTypeNameContains(valueClass, "ResourceLocation");
		}
		if (idMethod != null) {
			try {
				Object idValue = idMethod.invoke(maybeRecipeLike);
				if (idValue instanceof ResourceLocation recipeId) {
					return isValidScrollRecipeId(recipeId) ? recipeId : null;
				}
			} catch (ReflectiveOperationException ignored) {
			}
		}

		Object namedFieldId = getFieldValue(maybeRecipeLike, "id");
		if (namedFieldId instanceof ResourceLocation recipeId) {
			return isValidScrollRecipeId(recipeId) ? recipeId : null;
		}
		for (Field field : getAllFields(valueClass)) {
			if (field.getType() != ResourceLocation.class) {
				continue;
			}
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			field.setAccessible(true);
			try {
				Object fieldValue = field.get(maybeRecipeLike);
				if (fieldValue instanceof ResourceLocation recipeId) {
					if (isValidScrollRecipeId(recipeId)) {
						return recipeId;
					}
				}
			} catch (IllegalAccessException ignored) {
			}
		}
		return null;
	}

	private boolean isValidScrollRecipeId(@Nullable ResourceLocation recipeId) {
		if (recipeId == null || this.menu.getLevel() == null) {
			return false;
		}
		Optional<?> recipeOptional = this.menu.getLevel().getRecipeManager().byKey(recipeId);
		if (recipeOptional.isEmpty()) {
			return false;
		}
		Object recipe = recipeOptional.get();
		return recipe instanceof CraftingRecipe;
	}

	private boolean isSameRecipeOutput(@Nullable ResourceLocation leftRecipeId, @Nullable ResourceLocation rightRecipeId) {
		if (leftRecipeId == null || rightRecipeId == null || this.menu.getLevel() == null) {
			return false;
		}
		var manager = this.menu.getLevel().getRecipeManager();
		var left = manager.byKey(leftRecipeId);
		var right = manager.byKey(rightRecipeId);
		if (left.isEmpty() || right.isEmpty()) {
			return false;
		}
		if (!(left.get() instanceof CraftingRecipe leftCrafting) || !(right.get() instanceof CraftingRecipe rightCrafting)) {
			return false;
		}
		ItemStack leftResult = leftCrafting.getResultItem(this.menu.getLevel().registryAccess());
		ItemStack rightResult = rightCrafting.getResultItem(this.menu.getLevel().registryAccess());
		return !leftResult.isEmpty() && !rightResult.isEmpty() && ItemStack.isSameItemSameTags(leftResult, rightResult);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
		boolean outside = mouseX < (double) guiLeft || mouseY < (double) guiTop || mouseX >= (double) (guiLeft + this.imageWidth) || mouseY >= (double) (guiTop + this.imageHeight);
		if (this.vanillaRecipeBookSuppressedByEmi) {
			return outside;
		}
		return this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, mouseButton) && outside;
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType clickType) {
		super.slotClicked(slot, slotId, mouseButton, clickType);
		if (!this.vanillaRecipeBookSuppressedByEmi) {
			this.recipeBookComponent.slotClicked(slot);
		}
	}

	@Override
	public void recipesUpdated() {
		if (!this.vanillaRecipeBookSuppressedByEmi) {
			this.recipeBookComponent.recipesUpdated();
		}
	}

	@Override
	public RecipeBookComponent getRecipeBookComponent() {
		return this.recipeBookComponent;
	}

	public void refreshRecipeBookFromSyncedSources() {
		long startNs = System.nanoTime();
		long vanillaStartNs = System.nanoTime();
		if (!this.vanillaRecipeBookSuppressedByEmi) {
			if (!this.menu.slots.isEmpty()) {
				this.recipeBookComponent.slotClicked(this.menu.slots.get(0));
			} else {
				this.recipeBookComponent.recipesUpdated();
			}
		}
		long vanillaEndNs = System.nanoTime();
		long jeiStartNs = System.nanoTime();
		ProximityCraftingJeiCraftableFilterController.refreshIfEnabled(this.menu);
		long jeiEndNs = System.nanoTime();
		long emiStartNs = System.nanoTime();
		ProximityCraftingEmiCraftableFilterController.refreshIfEnabled(this.menu);
		long emiEndNs = System.nanoTime();
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.refreshRecipeBook menu={} vanillaMs={} jeiMs={} emiMs={} totalMs={} suppressedByEmi={}",
					this.menu.containerId,
					String.format("%.3f", (vanillaEndNs - vanillaStartNs) / 1_000_000.0D),
					String.format("%.3f", (jeiEndNs - jeiStartNs) / 1_000_000.0D),
					String.format("%.3f", (emiEndNs - emiStartNs) / 1_000_000.0D),
					String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D),
					this.vanillaRecipeBookSuppressedByEmi
			);
		}
	}

	private void requestRecipeBookSourceSync() {
		long nowMs = System.currentTimeMillis();
		ClientRecipeSessionState.SourceSyncRequestResult requestResult = this.recipeSessionState.requestSourceSync(
				ProximityClientServices.getClientRequestSender(),
				this.menu.containerId,
				nowMs
		);
		if (requestResult.disposition() == ClientRecipeSessionState.SourceSyncDisposition.SENT) {
			ProximityCraftingEmiCraftableFilterController.onSourceSyncStateUpdated(this.menu, true, false);
		}
		if (isDebugLoggingEnabled()) {
			switch (requestResult.disposition()) {
				case QUEUED_IN_FLIGHT -> ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] client.requestRecipeBookSourceSync queued menu={} reason=in_flight deferredRefreshTicks={}",
						this.menu.containerId,
						deferredRefreshTicks
				);
				case QUEUED_MIN_INTERVAL -> ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] client.requestRecipeBookSourceSync queued menu={} reason=min_interval elapsedMs={} deferredRefreshTicks={}",
						this.menu.containerId,
						requestResult.elapsedSinceLastSendMs(),
						deferredRefreshTicks
				);
				case SENT -> ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] client.requestRecipeBookSourceSync menu={} deferredRefreshTicks={}",
						this.menu.containerId,
						deferredRefreshTicks
				);
			}
		}
	}

	public void requestImmediateSourceSyncAndRefresh() {
		ProximityCraftingJeiCraftableFilterController.prewarmSnapshot(this.menu, "manual_sync_request");
		requestRecipeBookSourceSync();
		// EMI craftable-only uses its own idle refresh gate to avoid refresh thrash
		// while actions are still being processed.
		if (!ProximityCraftingEmiCraftableFilterController.isEnabledFor(this.menu.containerId)) {
			scheduleDeferredRecipeBookRefresh();
		}
	}

	public void showInfoStatusMessage(Component message) {
		showStatusMessage(message, STATUS_COLOR_INFO, 1400);
	}

	public void showSuccessStatusMessage(Component message) {
		showStatusMessage(message, STATUS_COLOR_SUCCESS, 1600);
	}

	public void showFailureStatusMessage(Component message) {
		showStatusMessage(message, STATUS_COLOR_FAILURE, 1700);
	}

	public ClientRecipeSessionState getRecipeSessionState() {
		return this.recipeSessionState;
	}

	private void showStatusMessage(Component message, int color, int durationMs) {
		this.statusMessage = message;
		this.statusMessageColor = color;
		this.statusMessageUntilMs = System.currentTimeMillis() + Math.max(250, durationMs);
	}

	private void renderStatusMessage(GuiGraphics guiGraphics) {
		if (statusMessage == null) {
			return;
		}
		if (System.currentTimeMillis() > statusMessageUntilMs) {
			statusMessage = null;
			return;
		}

		int centerX = this.width / 2;
		int y = this.topPos - 12;
		if (y < 6) {
			y = 6;
		}
		int textWidth = this.font.width(statusMessage);
		int x = centerX - (textWidth / 2);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0.0D, 0.0D, 500.0D);
		guiGraphics.drawString(this.font, statusMessage, x, y, this.statusMessageColor, true);
		guiGraphics.pose().popPose();
	}

	private void renderProximityItemsToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int x = leftPos + 4;
		int y = topPos + 4;
		boolean hovered = mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;

		int background = hovered ? 0xFFE2E2E2 : 0xFFD1D1D1;
		guiGraphics.fill(x, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, background);
		guiGraphics.fill(x, y, x + TOGGLE_SIZE, y + 1, PANEL_LINE_LIGHT);
		guiGraphics.fill(x, y + TOGGLE_SIZE - 1, x + TOGGLE_SIZE, y + TOGGLE_SIZE, PANEL_LINE_DARK);
		guiGraphics.fill(x, y, x + 1, y + TOGGLE_SIZE, PANEL_LINE_LIGHT);
		guiGraphics.fill(x + TOGGLE_SIZE - 1, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, PANEL_LINE_DARK);

		ResourceLocation icon = showProximityItemsPanel ? PROXIMITY_ITEMS_TOGGLE_ON_ICON : PROXIMITY_ITEMS_TOGGLE_OFF_ICON;
		int iconX = x + (TOGGLE_SIZE - TOGGLE_ICON_SIZE) / 2;
		int iconY = y + (TOGGLE_SIZE - TOGGLE_ICON_SIZE) / 2;
		guiGraphics.blit(icon, iconX, iconY, 0, 0, TOGGLE_ICON_SIZE, TOGGLE_ICON_SIZE, TOGGLE_ICON_TEX_WIDTH, TOGGLE_ICON_TEX_HEIGHT);
	}

	private void renderClearGridToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		Rect2i bounds = getClearGridToggleBounds();
		int x = bounds.getX();
		int y = bounds.getY();
		boolean hovered = bounds.contains(mouseX, mouseY);

		int size = bounds.getWidth();
		int background = hovered ? 0xFFE2E2E2 : 0xFFD1D1D1;
		guiGraphics.fill(x, y, x + size, y + size, background);
		guiGraphics.fill(x, y, x + size, y + 1, PANEL_LINE_LIGHT);
		guiGraphics.fill(x, y + size - 1, x + size, y + size, PANEL_LINE_DARK);
		guiGraphics.fill(x, y, x + 1, y + size, PANEL_LINE_LIGHT);
		guiGraphics.fill(x + size - 1, y, x + size, y + size, PANEL_LINE_DARK);

		int iconColor = 0xFF9D2929;
		for (int i = 0; i < 5; i++) {
			guiGraphics.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, iconColor);
			guiGraphics.fill(x + 6 - i, y + 2 + i, x + 7 - i, y + 3 + i, iconColor);
		}
	}

	private void renderAutoRefillToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		Rect2i bounds = getAutoRefillToggleBounds();
		int x = bounds.getX();
		int y = bounds.getY();
		boolean hovered = bounds.contains(mouseX, mouseY);
		boolean enabled = ProximityCraftingConfig.clientPreferences().autoRefillAfterCraft();

		int background = hovered ? 0xFFE2E2E2 : 0xFFD1D1D1;
		guiGraphics.fill(x, y, x + AUTO_REFILL_TOGGLE_SIZE, y + AUTO_REFILL_TOGGLE_SIZE, background);
		guiGraphics.fill(x, y, x + AUTO_REFILL_TOGGLE_SIZE, y + 1, PANEL_LINE_LIGHT);
		guiGraphics.fill(x, y + AUTO_REFILL_TOGGLE_SIZE - 1, x + AUTO_REFILL_TOGGLE_SIZE, y + AUTO_REFILL_TOGGLE_SIZE, PANEL_LINE_DARK);
		guiGraphics.fill(x, y, x + 1, y + AUTO_REFILL_TOGGLE_SIZE, PANEL_LINE_LIGHT);
		guiGraphics.fill(x + AUTO_REFILL_TOGGLE_SIZE - 1, y, x + AUTO_REFILL_TOGGLE_SIZE, y + AUTO_REFILL_TOGGLE_SIZE, PANEL_LINE_DARK);

		int indicatorInset = 2;
		int indicatorColor = enabled ? 0xFF4CAF50 : 0xFFB94A48;
		guiGraphics.fill(
				x + indicatorInset,
				y + indicatorInset,
				x + AUTO_REFILL_TOGGLE_SIZE - indicatorInset,
				y + AUTO_REFILL_TOGGLE_SIZE - indicatorInset,
				indicatorColor
		);
	}

	private void renderProximityItemsPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!showProximityItemsPanel) {
			return;
		}

		long frameStartNs = System.nanoTime();
		long collectStartNs = System.nanoTime();
		List<IngredientsPanelEntry> availabilityEntries = getCurrentRecipeAvailabilityEntries();
		long collectEndNs = System.nanoTime();
		Rect2i panelBounds = getProximityPanelBounds();
		int panelX = panelBounds.getX();
		int panelY = panelBounds.getY();
		int panelHeight = panelBounds.getHeight();

		guiGraphics.fill(panelX, panelY, panelX + PROXIMITY_PANEL_WIDTH, panelY + panelHeight, PANEL_OUTER_BG_COLOR);
		guiGraphics.fill(panelX + 1, panelY + 1, panelX + PROXIMITY_PANEL_WIDTH - 1, panelY + panelHeight - 1, PANEL_INNER_BG_COLOR);
		guiGraphics.fill(panelX, panelY, panelX + PROXIMITY_PANEL_WIDTH, panelY + 1, PANEL_LINE_LIGHT);
		guiGraphics.fill(panelX, panelY + panelHeight - 1, panelX + PROXIMITY_PANEL_WIDTH, panelY + panelHeight, PANEL_LINE_DARK);
		guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, PANEL_LINE_LIGHT);
		guiGraphics.fill(panelX + PROXIMITY_PANEL_WIDTH - 1, panelY, panelX + PROXIMITY_PANEL_WIDTH, panelY + panelHeight, PANEL_LINE_DARK);

		guiGraphics.drawString(font, Component.translatable("proximitycrafting.proximity_items.title"), panelX + 6, panelY + 6, PANEL_HEADER_COLOR, false);
		guiGraphics.fill(panelX + 5, panelY + 19, panelX + PROXIMITY_PANEL_WIDTH - 5, panelY + 20, 0x66444444);

		int rowY = panelY + 27;
		int maxRows = Math.max(0, (panelHeight - 34) / 20);
		for (int i = 0; i < availabilityEntries.size() && i < maxRows; i++) {
			IngredientsPanelEntry entry = availabilityEntries.get(i);
			int entryY = rowY + i * 20;
			int iconX = panelX + 7;
			boolean hovered = mouseX >= panelX + 4 && mouseX < panelX + PROXIMITY_PANEL_WIDTH - 4 && mouseY >= entryY && mouseY < entryY + 18;

			int rowBg = hovered ? 0x40FFFFFF : 0x20000000;
			guiGraphics.fill(panelX + 4, entryY - 1, panelX + PROXIMITY_PANEL_WIDTH - 4, entryY + 17, rowBg);

			guiGraphics.renderItem(entry.displayStack(), iconX, entryY);
			String countText = Integer.toString(entry.availableCount());
			int countX = panelX + PROXIMITY_PANEL_WIDTH - 8 - font.width(countText);
			guiGraphics.drawString(font, countText, countX, entryY + 4, PANEL_COUNT_COLOR, false);

			if (hovered) {
				hoveredProximityEntry = entry;
			}
		}

		long frameEndNs = System.nanoTime();
		recordPanelPerfSample(
				availabilityEntries.size(),
				menu.getClientRecipeBookSupplementalSources().size(),
				collectEndNs - collectStartNs,
				frameEndNs - collectEndNs,
				frameEndNs - frameStartNs
		);
	}

	private void renderProximityItemsTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!showProximityItemsPanel || hoveredProximityEntry == null) {
			return;
		}

		IngredientsPanelEntry entry = hoveredProximityEntry;
		List<Component> tooltip = List.of(
				entry.displayStack().getHoverName(),
				Component.translatable("proximitycrafting.proximity_items.available", entry.availableCount()),
				Component.translatable("proximitycrafting.proximity_items.required", entry.requiredCount())
		);
		guiGraphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
	}

	private boolean isMouseOverProximityItemsToggle(double mouseX, double mouseY) {
		int x = leftPos + 4;
		int y = topPos + 4;
		return mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;
	}

	private boolean isMouseOverClearGridToggle(double mouseX, double mouseY) {
		return getClearGridToggleBounds().contains((int) mouseX, (int) mouseY);
	}

	private Rect2i getClearGridToggleBounds() {
		Rect2i autoRefillBounds = getAutoRefillToggleBounds();
		int x = autoRefillBounds.getX() + autoRefillBounds.getWidth() + CLEAR_GRID_TOGGLE_SPACING;
		int y = autoRefillBounds.getY();
		return new Rect2i(x, y, CLEAR_GRID_TOGGLE_SIZE, CLEAR_GRID_TOGGLE_SIZE);
	}

	private boolean isMouseOverAutoRefillToggle(double mouseX, double mouseY) {
		return getAutoRefillToggleBounds().contains((int) mouseX, (int) mouseY);
	}

	private Rect2i getAutoRefillToggleBounds() {
		double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		if (guiScale <= 0.0D) {
			guiScale = 1.0D;
		}
		int scaledMoveX = (int) Math.round(AUTO_REFILL_TOGGLE_SCREEN_MOVE_X / guiScale);
		int scaledMoveY = (int) Math.round(AUTO_REFILL_TOGGLE_SCREEN_MOVE_Y / guiScale);
		int x = this.leftPos + RESULT_SLOT_X + AUTO_REFILL_TOGGLE_OFFSET_BASE_X + scaledMoveX;
		int y = this.topPos + RESULT_SLOT_Y + AUTO_REFILL_TOGGLE_OFFSET_BASE_Y + scaledMoveY;
		return new Rect2i(x, y, AUTO_REFILL_TOGGLE_SIZE, AUTO_REFILL_TOGGLE_SIZE);
	}

	private void renderAutoRefillTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		Rect2i bounds = getAutoRefillToggleBounds();
		if (!bounds.contains(mouseX, mouseY)) {
			return;
		}

		boolean enabled = ProximityCraftingConfig.clientPreferences().autoRefillAfterCraft();
		Component state = enabled
				? Component.translatable("options.on")
				: Component.translatable("options.off");
		List<Component> tooltip = List.of(
				Component.translatable("proximitycrafting.auto_refill.toggle"),
				Component.translatable("proximitycrafting.auto_refill.state", state)
		);
		guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
	}

	private void renderClearGridTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		Rect2i bounds = getClearGridToggleBounds();
		if (!bounds.contains(mouseX, mouseY)) {
			return;
		}
		guiGraphics.renderTooltip(
				this.font,
				List.of(Component.translatable("proximitycrafting.clear_grid.toggle")),
				Optional.empty(),
				mouseX,
				mouseY
		);
	}

	private boolean isMouseOverRecipeScaleArea(double mouseX, double mouseY) {
		int hoveredMenuSlot = getHoveredMenuSlotIndex();
		if (hoveredMenuSlot >= RESULT_SLOT_MENU_INDEX && hoveredMenuSlot < CRAFT_SLOT_MENU_END_EXCLUSIVE) {
			if (!isMouseWithinHoveredSlot(mouseX, mouseY) && isDebugLoggingEnabled()) {
				long nowMs = System.currentTimeMillis();
				if ((nowMs - lastStaleHoveredSlotLogAtMs) >= STALE_HOVERED_SLOT_LOG_INTERVAL_MS) {
					lastStaleHoveredSlotLogAtMs = nowMs;
					int slotX = this.hoveredSlot == null ? -1 : (this.leftPos + this.hoveredSlot.x);
					int slotY = this.hoveredSlot == null ? -1 : (this.topPos + this.hoveredSlot.y);
					ProximityCrafting.LOGGER.info(
							"[PROXC-SCROLL] staleHoveredSlot menu={} slotIndex={} slotAbs=({}, {}) mouse=({}, {})",
							this.menu.containerId,
							hoveredMenuSlot,
							slotX,
							slotY,
							(int) mouseX,
							(int) mouseY
					);
				}
			}
			return true;
		}

		if (showProximityItemsPanel && getProximityPanelBounds().contains((int) mouseX, (int) mouseY)) {
			return false;
		}
		int gridLeft = this.leftPos + CRAFT_GRID_X;
		int gridTop = this.topPos + CRAFT_GRID_Y;
		int resultLeft = this.leftPos + RESULT_SLOT_X;
		int resultTop = this.topPos + RESULT_SLOT_Y;
		boolean overGrid = mouseX >= gridLeft && mouseX < gridLeft + CRAFT_GRID_SIZE && mouseY >= gridTop && mouseY < gridTop + CRAFT_GRID_SIZE;
		boolean overResult = mouseX >= resultLeft && mouseX < resultLeft + RESULT_SLOT_SIZE && mouseY >= resultTop && mouseY < resultTop + RESULT_SLOT_SIZE;
		return overGrid || overResult;
	}

	private boolean isMouseWithinHoveredSlot(double mouseX, double mouseY) {
		if (this.hoveredSlot == null) {
			return false;
		}
		int slotLeft = this.leftPos + this.hoveredSlot.x;
		int slotTop = this.topPos + this.hoveredSlot.y;
		return mouseX >= slotLeft
				&& mouseX < slotLeft + 16
				&& mouseY >= slotTop
				&& mouseY < slotTop + 16;
	}

	private int getHoveredMenuSlotIndex() {
		if (this.hoveredSlot == null) {
			return -1;
		}
		return this.menu.slots.indexOf(this.hoveredSlot);
	}

	private boolean hasActiveRecipeLoadedInGrid() {
		return resolvePreferredClientRecipe().isPresent();
	}

	@Nullable
	private ResourceLocation getActiveRecipeIdForScroll() {
		Optional<CraftingRecipe> preferredRecipe = resolvePreferredClientRecipe();
		if (preferredRecipe.isPresent() && preferredRecipe.get().getId() != null) {
			return preferredRecipe.get().getId();
		}

		return localScrollRecipeId;
	}

	private Optional<CraftingRecipe> resolvePreferredClientRecipe() {
		if (this.menu.getLevel() == null) {
			return Optional.empty();
		}
		if (localScrollRecipeId != null) {
			Optional<?> selectedRecipe = this.menu.getLevel().getRecipeManager().byKey(localScrollRecipeId);
			if (selectedRecipe.isPresent() && selectedRecipe.get() instanceof CraftingRecipe craftingRecipe
					&& craftingRecipe.matches(this.menu.getCraftSlots(), this.menu.getLevel())) {
				return Optional.of(craftingRecipe);
			}
		}
		return this.menu.getLevel()
				.getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, this.menu.getCraftSlots(), this.menu.getLevel());
	}

	private void rememberPendingScrollRecipe(ResourceLocation recipeId) {
		this.localScrollRecipeId = recipeId;
	}

	@Override
	public void removed() {
		this.localScrollRecipeId = null;
		this.recipeSessionState.reset();
		ProximityCraftingEmiCraftableFilterController.handleMenuClosed(this.menu.containerId);
		ProximityCraftingJeiCraftableFilterController.handleMenuClosed(this.menu.containerId);
		super.removed();
	}

	private void applyRememberedUiState() {
		ClientUiState uiState = ProximityCraftingConfig.clientUiState();
		if (!uiState.rememberToggleStates()) {
			showProximityItemsPanel = true;
			return;
		}

		showProximityItemsPanel = uiState.ingredientsPanelOpen();

		if (uiState.jeiCraftableOnlyEnabled()) {
			ProximityCraftingJeiCraftableFilterController.setEnabled(menu, true);
		}
		if (uiState.emiCraftableOnlyEnabled()) {
			ProximityCraftingEmiCraftableFilterController.setEnabled(menu, true);
			ProximityCraftingEmiCraftableFilterController.applyStartupPendingViewIfEnabled(menu);
		}
	}

	private static void saveProximityItemsPanelState(boolean isOpen) {
		ClientUiState uiState = ProximityCraftingConfig.clientUiState();
		if (!uiState.rememberToggleStates()) {
			return;
		}
		ProximityCraftingConfig.setClientUiState(uiState.withIngredientsPanelOpen(isOpen));
	}

	private Rect2i getProximityPanelBounds() {
		int baseX = this.leftPos - PROXIMITY_PANEL_WIDTH - PROXIMITY_PANEL_PADDING;
		if (!this.vanillaRecipeBookSuppressedByEmi && this.recipeBookComponent.isVisible()) {
			baseX -= RecipeBookComponent.IMAGE_WIDTH + PROXIMITY_PANEL_RECIPE_BOOK_EXTRA_SHIFT;
		}
		int baseY = this.topPos;
		ClientUiState uiState = ProximityCraftingConfig.clientUiState();
		int panelX = baseX + uiState.ingredientsPanelOffsetX();
		int panelY = baseY + uiState.ingredientsPanelOffsetY();
		return new Rect2i(panelX, panelY, PROXIMITY_PANEL_WIDTH, this.imageHeight);
	}

	private boolean finishScrollPerf(String source, double scrollDelta, boolean handled, long startNs) {
		recordScrollPerf(source, scrollDelta, handled, startNs);
		return handled;
	}

	private void recordScrollPerf(String source, double scrollDelta, boolean handled, long startNs) {
		if (!isDebugLoggingEnabled()) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		if (scrollPerfWindowStartMs == 0L) {
			scrollPerfWindowStartMs = nowMs;
		}
		double elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0D;
		scrollPerfEvents++;
		if (handled) {
			scrollPerfHandledEvents++;
		}
		scrollPerfTotalNs += (long) (elapsedMs * 1_000_000.0D);
		if (elapsedMs > scrollPerfMaxMs) {
			scrollPerfMaxMs = elapsedMs;
		}

		if (elapsedMs >= SCROLL_SLOW_EVENT_MS) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-CLIENT] scroll.slow menu={} source={} handled={} delta={} elapsedMs={} inFlight={} pendingFill={} pendingAdjust={} sourceSyncInFlight={}",
					this.menu.containerId,
					source,
					handled,
					scrollDelta,
					String.format("%.3f", elapsedMs),
					recipeSessionState.isRecipeActionInFlight(),
					recipeSessionState.getPendingFillRecipeId() == null ? "null" : recipeSessionState.getPendingFillRecipeId(),
					recipeSessionState.getPendingAdjustSteps(),
					recipeSessionState.isSourceSyncInFlight()
			);
		}

		if ((nowMs - scrollPerfWindowStartMs) >= SCROLL_PERF_LOG_INTERVAL_MS) {
			double avgMs = scrollPerfEvents == 0 ? 0.0D : (scrollPerfTotalNs / 1_000_000.0D) / scrollPerfEvents;
			ProximityCrafting.LOGGER.info(
					"[PROXC-CLIENT] scroll.window menu={} events={} handled={} avgMs={} maxMs={} inFlight={} pendingFill={} pendingAdjust={} sourceSyncInFlight={} sourceSyncQueued={} windowMs={}",
					this.menu.containerId,
					scrollPerfEvents,
					scrollPerfHandledEvents,
					String.format("%.3f", avgMs),
					String.format("%.3f", scrollPerfMaxMs),
					recipeSessionState.isRecipeActionInFlight(),
					recipeSessionState.getPendingFillRecipeId() == null ? "null" : recipeSessionState.getPendingFillRecipeId(),
					recipeSessionState.getPendingAdjustSteps(),
					recipeSessionState.isSourceSyncInFlight(),
					recipeSessionState.isSourceSyncQueued(),
					nowMs - scrollPerfWindowStartMs
			);
			scrollPerfWindowStartMs = nowMs;
			scrollPerfEvents = 0;
			scrollPerfHandledEvents = 0;
			scrollPerfTotalNs = 0L;
			scrollPerfMaxMs = 0.0D;
		}
	}

	private void logActionQueueState(String phase, long nowMs) {
		if (!isDebugLoggingEnabled()) {
			return;
		}
		if ((nowMs - lastActionQueueLogAtMs) < ACTION_QUEUE_LOG_INTERVAL_MS) {
			return;
		}
		lastActionQueueLogAtMs = nowMs;
		ProximityCrafting.LOGGER.info(
				"[PROXC-CLIENT] action.queue phase={} menu={} inFlight={} inFlightFill={} inFlightAdjust={} pendingFill={} pendingAdjust={} sourceSyncInFlight={} sourceSyncQueued={}",
				phase,
				this.menu.containerId,
				recipeSessionState.isRecipeActionInFlight(),
				recipeSessionState.getInFlightFillRecipeId() == null ? "null" : recipeSessionState.getInFlightFillRecipeId(),
				recipeSessionState.getInFlightAdjustSteps(),
				recipeSessionState.getPendingFillRecipeId() == null ? "null" : recipeSessionState.getPendingFillRecipeId(),
				recipeSessionState.getPendingAdjustSteps(),
				recipeSessionState.isSourceSyncInFlight(),
				recipeSessionState.isSourceSyncQueued()
		);
	}

	private void logScrollDebug(String pattern, Object... args) {
		if (!isDebugLoggingEnabled()) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		if ((nowMs - lastScrollDebugLogAtMs) < SCROLL_DEBUG_LOG_THROTTLE_MS) {
			suppressedScrollDebugLogs++;
			return;
		}
		if (suppressedScrollDebugLogs > 0) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] throttled={} menu={}",
					suppressedScrollDebugLogs,
					this.menu.containerId
			);
			suppressedScrollDebugLogs = 0;
		}
		lastScrollDebugLogAtMs = nowMs;
		ProximityCrafting.LOGGER.info(pattern, args);
	}

	private static boolean isDebugLoggingEnabled() {
		return ProximityCraftingConfig.isClientDebugLoggingEnabled();
	}

	@Nullable
	private static Method findMethod(Class<?> ownerClass, String methodName, int parameterCount) {
		for (Method method : ownerClass.getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
				return method;
			}
		}
		return null;
	}

	@Nullable
	private static Field findField(Class<?> ownerClass, String fieldName) {
		Class<?> currentClass = ownerClass;
		while (currentClass != null) {
			try {
				Field field = currentClass.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
				currentClass = currentClass.getSuperclass();
			}
		}
		return null;
	}

	@Nullable
	private static Field findFieldByTypeNameContains(Class<?> ownerClass, String typeNameFragment) {
		Class<?> currentClass = ownerClass;
		while (currentClass != null) {
			for (Field field : currentClass.getDeclaredFields()) {
				String fieldTypeName = field.getType().getName();
				String fieldTypeSimpleName = field.getType().getSimpleName();
				if (fieldTypeName.contains(typeNameFragment) || fieldTypeSimpleName.contains(typeNameFragment)) {
					field.setAccessible(true);
					return field;
				}
			}
			currentClass = currentClass.getSuperclass();
		}
		return null;
	}

	private static List<Field> getAllFields(Class<?> ownerClass) {
		List<Field> fields = new ArrayList<>();
		Class<?> currentClass = ownerClass;
		while (currentClass != null) {
			for (Field field : currentClass.getDeclaredFields()) {
				fields.add(field);
			}
			currentClass = currentClass.getSuperclass();
		}
		return fields;
	}

	@Nullable
	private static Method findNoArgMethodReturningTypeNameContains(Class<?> ownerClass, String typeNameFragment) {
		for (Method method : ownerClass.getMethods()) {
			if (method.getParameterCount() != 0) {
				continue;
			}
			Class<?> returnType = method.getReturnType();
			if (returnType == null || returnType == Void.TYPE) {
				continue;
			}
			String returnTypeName = returnType.getName();
			String returnTypeSimpleName = returnType.getSimpleName();
			if (returnTypeName.contains(typeNameFragment) || returnTypeSimpleName.contains(typeNameFragment)) {
				return method;
			}
		}
		return null;
	}

	@Nullable
	private static Object getFieldValue(Object owner, String fieldName) {
		Field field = findField(owner.getClass(), fieldName);
		if (field == null) {
			return null;
		}
		try {
			return field.get(owner);
		} catch (IllegalAccessException ignored) {
			return null;
		}
	}

	private List<IngredientsPanelEntry> getCurrentRecipeAvailabilityEntries() {
		long nowMs = System.currentTimeMillis();
		long collectStartNs = System.nanoTime();
		IngredientsPanelUpdateResult updateResult = ingredientsPanelPresenter.refresh(ingredientsPanelContext, nowMs);
		long collectEndNs = System.nanoTime();

		if (isDebugLoggingEnabled() && updateResult.cacheUpdated() && updateResult.recipeId() != null) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] panel.cache.rebuild menu={} recipe={} reason=dirty:{} recipeChanged:{} gridChanged:{} sourcesChanged:{} entries={} ageMs={}",
					this.menu.containerId,
					updateResult.recipeId(),
					updateResult.dirtyBefore(),
					updateResult.recipeChanged(),
					updateResult.gridChanged(),
					updateResult.sourcesChanged(),
					updateResult.entries().size(),
					updateResult.previousBuiltAgeMs()
			);

			double totalMs = (collectEndNs - collectStartNs) / 1_000_000.0D;
			if (totalMs >= 2.0D) {
				Optional<CraftingRecipe> recipeOptional = resolvePreferredClientRecipe();
				int ingredientCount = recipeOptional.map(recipe -> recipe.getIngredients().size()).orElse(0);
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] panel.collect menu={} recipe={} ingredients={} sourceEntries={} outEntries={} lookupMs={} trackerBuildMs={} aggregateMs={} totalMs={}",
						this.menu.containerId,
						updateResult.recipeId(),
						ingredientCount,
						updateResult.sourceEntriesProcessed(),
						updateResult.entries().size(),
						String.format("%.3f", updateResult.recipeLookupDurationNs() / 1_000_000.0D),
						String.format("%.3f", updateResult.trackerBuildDurationNs() / 1_000_000.0D),
						String.format("%.3f", updateResult.aggregateDurationNs() / 1_000_000.0D),
						String.format("%.3f", totalMs)
				);
			}
		}

		return updateResult.entries();
	}

	public void sendRecipeFillPacket(ResourceLocation recipeId, boolean craftAll, String source) {
		queueRecipeFillPacket(recipeId, craftAll, source);
	}

	public void sendAdjustPacket(int steps, String source) {
		queueAdjustPacket(steps, source);
	}

	private void sendClearGridPacket(String source) {
		long nowMs = System.currentTimeMillis();
		this.recipeSessionState.clearQueuedActionsForImmediateRequest(nowMs);
		ProximityCraftingEmiCraftableFilterController.onRecipeActionQueued(this.menu);
		ProximityClientServices.getClientRequestSender().clearCraftGrid(this.menu.containerId);
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.sendClearGrid source={} menu={}",
					source,
					this.menu.containerId
			);
		}
	}

	private void queueRecipeFillPacket(ResourceLocation recipeId, boolean craftAll, String source) {
		long nowMs = System.currentTimeMillis();
		ProximityCraftingEmiCraftableFilterController.onRecipeActionQueued(this.menu);
		ClientRecipeSessionState.QueueRecipeFillResult result = this.recipeSessionState.queueRecipeFill(recipeId, craftAll, nowMs);
		if (isDebugLoggingEnabled()) {
			switch (result.disposition()) {
				case DEDUPED_IN_FLIGHT -> ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] client.queueRecipeFill dedupe=in_flight source={} menu={} recipe={} craftAll={}",
						source,
						this.menu.containerId,
						recipeId,
						craftAll
				);
				case DEDUPED_PENDING -> ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] client.queueRecipeFill dedupe=pending source={} menu={} recipe={} craftAll={}",
						source,
						this.menu.containerId,
						recipeId,
						craftAll
				);
				default -> {
				}
			}
		}
		if (result.disposition() == ClientRecipeSessionState.QueueRecipeFillDisposition.IGNORED_NULL) {
			return;
		}
		processQueuedRecipeActions("queue_fill");
	}

	private void queueAdjustPacket(int steps, String source) {
		if (steps == 0) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		ProximityCraftingEmiCraftableFilterController.onRecipeActionQueued(this.menu);
		ClientRecipeSessionState.QueueAdjustResult result = this.recipeSessionState.queueAdjust(steps, nowMs);

		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.queueAdjust source={} menu={} incomingSteps={} pendingBefore={} pendingAfter={}",
					source,
					this.menu.containerId,
					steps,
					result.pendingBefore(),
					result.pendingAfter()
			);
		}
		processQueuedRecipeActions("queue_adjust");
	}

	private void processQueuedRecipeActions(String reason) {
		long nowMs = System.currentTimeMillis();
		logActionQueueState("enter:" + reason, nowMs);
		ClientRecipeSessionState.ActionDispatchResult dispatchResult = this.recipeSessionState.dispatchNextAction(
				ProximityClientServices.getClientRequestSender(),
				nowMs,
				MAX_ABS_ADJUST_STEPS_PER_PACKET
		);
		if (dispatchResult.timedOutCleared()) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.warn(
						"[PROXC-PERF] client.recipeAction timeout clearing in-flight menu={} inFlightFill={} inFlightAdjust={} ageMs={} reason={}",
						this.menu.containerId,
						recipeSessionState.getInFlightFillRecipeId() == null ? "null" : recipeSessionState.getInFlightFillRecipeId(),
						recipeSessionState.getInFlightAdjustSteps(),
						RECIPE_ACTION_IN_FLIGHT_TIMEOUT_MS,
						reason
				);
			}
			logActionQueueState("timeout_clear:" + reason, nowMs);
		}
		switch (dispatchResult.disposition()) {
			case BLOCKED_IN_FLIGHT -> logActionQueueState("blocked_in_flight:" + reason, nowMs);
			case SENT_FILL -> {
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[PROXC-PERF] client.sendRecipeFill menu={} recipe={} craftAll={} reason={} pendingAdjustAfterSend={}",
							this.menu.containerId,
							dispatchResult.recipeId(),
							dispatchResult.craftAll(),
							reason,
							dispatchResult.pendingAdjustRemaining()
					);
				}
				logActionQueueState("sent_fill:" + reason, nowMs);
			}
			case SENT_ADJUST -> {
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[PROXC-PERF] client.sendAdjust menu={} steps={} reason={} pendingRemaining={}",
							this.menu.containerId,
							dispatchResult.stepsSent(),
							reason,
							dispatchResult.pendingAdjustRemaining()
					);
				}
				logActionQueueState("sent_adjust:" + reason, nowMs);
			}
			default -> {
			}
		}
	}

	private boolean shouldDeferPeriodicSourceSync(long nowMs) {
		return this.recipeSessionState.shouldDeferPeriodicSourceSync(nowMs, RECIPE_BOOK_SOURCE_SYNC_ACTION_COOLDOWN_MS);
	}

	private int getCurrentSourceSyncIntervalTicks() {
		if (ProximityCraftingEmiCraftableFilterController.isEnabledFor(this.menu.containerId)) {
			return RECIPE_BOOK_SOURCE_SYNC_INTERVAL_TICKS_EMI_CRAFTABLE;
		}
		return RECIPE_BOOK_SOURCE_SYNC_INTERVAL_TICKS;
	}

	private void recordPanelPerfSample(int entryCount, int sourceEntryCount, long collectNs, long renderNs, long frameNs) {
		if (!isDebugLoggingEnabled()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (panelPerfWindowStartMs == 0L) {
			panelPerfWindowStartMs = now;
		}

		panelPerfSamples++;
		panelPerfCollectNs += collectNs;
		panelPerfRenderNs += renderNs;
		panelPerfLastEntryCount = entryCount;
		panelPerfLastSourceEntryCount = sourceEntryCount;
		double frameMs = frameNs / 1_000_000.0D;
		if (frameMs > panelPerfMaxFrameMs) {
			panelPerfMaxFrameMs = frameMs;
		}

		if (frameMs >= PANEL_PERF_SLOW_FRAME_MS) {
			if ((now - lastPanelSlowFrameLogAtMs) >= PANEL_SLOW_FRAME_LOG_INTERVAL_MS) {
				lastPanelSlowFrameLogAtMs = now;
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] panel.frame.slow menu={} frameMs={} collectMs={} renderMs={} entries={} sourceEntries={}",
						this.menu.containerId,
						String.format("%.3f", frameMs),
						String.format("%.3f", collectNs / 1_000_000.0D),
						String.format("%.3f", renderNs / 1_000_000.0D),
						entryCount,
						sourceEntryCount
				);
			}
		}

		if (now - panelPerfWindowStartMs >= PANEL_PERF_LOG_INTERVAL_MS) {
			double avgCollectMs = panelPerfSamples == 0 ? 0.0D : (panelPerfCollectNs / 1_000_000.0D) / panelPerfSamples;
			double avgRenderMs = panelPerfSamples == 0 ? 0.0D : (panelPerfRenderNs / 1_000_000.0D) / panelPerfSamples;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] panel.window menu={} samples={} avgCollectMs={} avgRenderMs={} maxFrameMs={} lastEntries={} lastSourceEntries={} windowMs={}",
					this.menu.containerId,
					panelPerfSamples,
					String.format("%.3f", avgCollectMs),
					String.format("%.3f", avgRenderMs),
					String.format("%.3f", panelPerfMaxFrameMs),
					panelPerfLastEntryCount,
					panelPerfLastSourceEntryCount,
					now - panelPerfWindowStartMs
			);
			panelPerfWindowStartMs = now;
			panelPerfSamples = 0;
			panelPerfCollectNs = 0L;
			panelPerfRenderNs = 0L;
			panelPerfMaxFrameMs = 0.0D;
		}
	}

	public void handleSourceSnapshotAppliedFromRuntime(int entryCount, boolean sourcesChanged, SourceSnapshotApplyResult result) {
		ProximityCraftingEmiCraftableFilterController.onSourceSyncStateUpdated(this.menu, false, sourcesChanged);
		if (sourcesChanged) {
			handleProximityPanelSourceChange();
		}
		if (sourcesChanged && ProximityCraftingEmiCraftableFilterController.isEnabledFor(this.menu.containerId)) {
			// EMI refresh is intentionally idle-first and controlled by the EMI controller;
			// avoid bouncing full refresh requests on every action snapshot.
			this.recipeSessionState.clearDeferredRecipeBookRefreshAfterAction();
			deferredRefreshTicks = 0;
		}
		if (result.shouldRequestQueuedSyncNow()) {
			requestRecipeBookSourceSync();
		}
		if (ProximityCraftingJeiCraftableFilterController.isEnabledFor(this.menu.containerId)) {
			if (result.actionBusy()) {
				this.recipeSessionState.markDeferredRecipeBookRefreshAfterAction();
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[PROXC-PERF] client.deferJeiPrewarm menu={} reason=action_busy inFlight={} pendingFill={} pendingAdjust={}",
							this.menu.containerId,
							recipeSessionState.isRecipeActionInFlight(),
							recipeSessionState.getPendingFillRecipeId() == null ? "null" : recipeSessionState.getPendingFillRecipeId(),
							recipeSessionState.getPendingAdjustSteps()
					);
				}
			} else {
				ProximityCraftingJeiCraftableFilterController.prewarmSnapshot(this.menu, "source_snapshot_applied");
			}
		}
		if (!isDebugLoggingEnabled()) {
			return;
		}
		ProximityCrafting.LOGGER.info(
				"[PROXC-PERF] client.sourceSnapshotApplied menu={} entries={} source_sync_rtt_ms={} action_snapshot_apply_delay_ms={} hadSourceSyncInFlight={} actionBusy={}",
				this.menu.containerId,
				entryCount,
				result.sourceSyncRttMs(),
				result.actionSnapshotApplyDelayMs(),
				result.hadSourceSyncInFlight(),
				result.actionBusy()
		);
	}

	public void handleRecipeActionFeedbackFromRuntime(RecipeFillFeedbackPayload payload, RecipeActionFeedbackApplyResult result) {
		Component feedback = payload.craftedAmount() > 0
				? Component.translatable(payload.messageKey(), payload.craftedAmount())
				: Component.translatable(payload.messageKey());
		if (payload.success()) {
			showSuccessStatusMessage(feedback);
		} else {
			showFailureStatusMessage(feedback);
		}
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.recipeActionFeedback menu={} success={} key={} amount={} inFlightFill={} inFlightAdjust={} pendingFill={} pendingAdjust={}",
					this.menu.containerId,
					payload.success(),
					payload.messageKey(),
					payload.craftedAmount(),
					result.clearedInFlightFillRecipeId() == null ? "null" : result.clearedInFlightFillRecipeId(),
					result.clearedInFlightAdjustSteps(),
					result.pendingFillRecipeId() == null ? "null" : result.pendingFillRecipeId(),
					result.pendingAdjustSteps()
			);
		}
		processQueuedRecipeActions("feedback");
		flushDeferredRecipeBookRefreshAfterAction("feedback");
	}

	private void handleProximityPanelSourceChange() {
		ingredientsPanelPresenter.onSourcesChanged(this.menu.getClientRecipeBookSupplementalSources());
	}

	private void markProximityPanelCacheDirty() {
		ingredientsPanelPresenter.markDirty();
	}

	public static boolean enqueueRecipeFillIfScreenOpen(
			ProximityCraftingMenu menu,
			ResourceLocation recipeId,
			boolean craftAll,
			String source
	) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof ProximityCraftingScreen screen)) {
			return false;
		}
		if (screen.getMenu().containerId != menu.containerId) {
			return false;
		}
		screen.sendRecipeFillPacket(recipeId, craftAll, source);
		return true;
	}

	public void scheduleDeferredRecipeBookRefresh() {
		deferredRefreshTicks = Math.max(deferredRefreshTicks, 2);
	}

	private void flushDeferredRecipeBookRefreshAfterAction(String reason) {
		if (!this.recipeSessionState.shouldFlushDeferredRecipeBookRefresh()) {
			return;
		}
		this.recipeSessionState.consumeDeferredRecipeBookRefresh();
		scheduleDeferredRecipeBookRefresh();
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.flushDeferredRecipeBookRefresh menu={} reason={}",
					this.menu.containerId,
					reason
			);
		}
	}

	private void sendClientPreferencesUpdate() {
		ClientPreferences preferences = ProximityCraftingConfig.clientPreferences();
		ProximityClientServices.getClientRequestSender().updateClientPreferences(
				this.menu.containerId,
				preferences.autoRefillAfterCraft(),
				preferences.includePlayerInventory(),
				preferences.sourcePriorityValue()
		);
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.sendPreferences menu={} autoRefill={} includePlayer={} sourcePriority={}",
					this.menu.containerId,
					preferences.autoRefillAfterCraft(),
					preferences.includePlayerInventory(),
					preferences.sourcePriorityValue()
			);
		}
	}

	private final class ScreenIngredientsPanelContext implements IngredientsPanelContext {
		@Override
		public List<RecipeBookSourceEntry> getCurrentSources() {
			return menu.getClientRecipeBookSupplementalSources();
		}

		@Override
		public Optional<CraftingRecipe> resolvePreferredRecipe() {
			return resolvePreferredClientRecipe();
		}

		@Override
		public int getCraftGridSize() {
			return menu.getCraftSlots().getContainerSize();
		}

		@Override
		public ItemStack getCraftGridItem(int slot) {
			return menu.getCraftSlots().getItem(slot);
		}
	}
}



