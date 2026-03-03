package dev.maeiro.proximitycrafting.client.screen;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.compat.emi.ProximityCraftingEmiCraftableFilterController;
import dev.maeiro.proximitycrafting.client.compat.jei.ProximityCraftingJeiCraftableFilterController;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.C2SAdjustRecipeLoad;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeBookSources;
import dev.maeiro.proximitycrafting.networking.C2SUpdateClientPreferences;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class ProximityCraftingScreen extends AbstractContainerScreen<ProximityCraftingMenu> implements RecipeUpdateListener {
	private static final ResourceLocation CRAFTING_TABLE_LOCATION = new ResourceLocation("textures/gui/container/crafting_table.png");
	private static final ResourceLocation RECIPE_BUTTON_LOCATION = new ResourceLocation("textures/gui/recipe_button.png");
	private static final ResourceLocation PROXIMITY_ITEMS_TOGGLE_ON_ICON = new ResourceLocation("proximitycrafting", "icon/toggle_on.png");
	private static final ResourceLocation PROXIMITY_ITEMS_TOGGLE_OFF_ICON = new ResourceLocation("proximitycrafting", "icon/toggle_off.png");
	private static final int RECIPE_BOOK_SOURCE_SYNC_INTERVAL_TICKS = 20;
	private static final int STATUS_COLOR_SUCCESS = 0x55FF55;
	private static final int STATUS_COLOR_FAILURE = 0xFF5555;
	private static final int STATUS_COLOR_INFO = 0xFFFFFF;
	private static final int PROXIMITY_PANEL_WIDTH = 74;
	private static final int PROXIMITY_PANEL_PADDING = 8;
	private static final int PROXIMITY_PANEL_RECIPE_BOOK_EXTRA_SHIFT = 26;
	private static final int TOGGLE_SIZE = 18;
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
	private IngredientAvailabilityEntry hoveredProximityEntry;
	@Nullable
	private ResourceLocation localScrollRecipeId;

	public ProximityCraftingScreen(ProximityCraftingMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Override
	protected void init() {
		super.init();
		ProximityCraftingNetwork.CHANNEL.sendToServer(
				new C2SUpdateClientPreferences(
						this.menu.containerId,
						ProximityCraftingConfig.CLIENT.autoRefillAfterCraft.get(),
						ProximityCraftingConfig.CLIENT.includePlayerInventory.get(),
						ProximityCraftingConfig.CLIENT.sourcePriority.get()
				)
		);
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
	}

	@Override
	public void containerTick() {
		super.containerTick();
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
		recipeBookSourceSyncTicker++;
		if (recipeBookSourceSyncTicker >= RECIPE_BOOK_SOURCE_SYNC_INTERVAL_TICKS) {
			recipeBookSourceSyncTicker = 0;
			requestRecipeBookSourceSync();
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
		renderAutoRefillToggle(guiGraphics, mouseX, mouseY);
		renderProximityItemsPanel(guiGraphics, mouseX, mouseY);

		this.renderTooltip(guiGraphics, mouseX, mouseY);
		renderProximityItemsTooltip(guiGraphics, mouseX, mouseY);
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
		if (button == 0 && isMouseOverAutoRefillToggle(mouseX, mouseY)) {
			boolean nextAutoRefill = !ProximityCraftingConfig.CLIENT.autoRefillAfterCraft.get();
			ProximityCraftingConfig.CLIENT.autoRefillAfterCraft.set(nextAutoRefill);
			sendClientPreferencesUpdate();
			showInfoStatusMessage(Component.translatable(
					nextAutoRefill
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
		if (scrollDelta == 0.0D) {
			return false;
		}

		if (showProximityItemsPanel && getProximityPanelBounds().contains((int) mouseX, (int) mouseY)) {
			return false;
		}

		ResourceLocation hoveredRecipeId = resolveHoveredOverlayRecipeId(mouseX, mouseY);
		ResourceLocation activeRecipeId = getActiveRecipeIdForScroll();
		boolean activeRecipeLoaded = hasActiveRecipeLoadedInGrid();
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[NC-SCROLL] client source={} resolvedHoverRecipe={} activeRecipe={} activeLoaded={} delta={}",
					source,
					hoveredRecipeId,
					activeRecipeId,
					activeRecipeLoaded,
					scrollDelta
			);
		}
		if (scrollDelta > 0.0D && hoveredRecipeId != null && !activeRecipeLoaded) {
			int steps = Math.max(1, (int) Math.round(Math.abs(scrollDelta)));
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[NC-SCROLL] client source={} priming empty grid with hovered recipe={} steps={}",
						source,
						hoveredRecipeId,
						steps
				);
			}
			ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(hoveredRecipeId, false));
			rememberPendingScrollRecipe(hoveredRecipeId);
			if (steps > 1) {
				ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SAdjustRecipeLoad(steps - 1));
			}
			return true;
		}
		if (hoveredRecipeId != null
				&& !hoveredRecipeId.equals(activeRecipeId)
				&& !isSameRecipeOutput(hoveredRecipeId, activeRecipeId)) {
			int steps = Math.max(1, (int) Math.round(Math.abs(scrollDelta)));
			if (scrollDelta > 0.0D) {
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[NC-SCROLL] client source={} switching active recipe from {} to {} with steps={}",
							source,
							activeRecipeId,
							hoveredRecipeId,
							steps
					);
				}
				ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(hoveredRecipeId, false));
				rememberPendingScrollRecipe(hoveredRecipeId);
				if (steps > 1) {
					ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SAdjustRecipeLoad(steps - 1));
				}
				return true;
			} else {
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[NC-SCROLL] client source={} ignoring negative scroll while switching recipe {} -> {}",
							source,
							activeRecipeId,
							hoveredRecipeId
					);
				}
			}
		}

		int hoveredMenuSlot = getHoveredMenuSlotIndex();
		boolean overScaleArea = isMouseOverRecipeScaleArea(mouseX, mouseY);
		boolean shouldHandle = overScaleArea && activeRecipeLoaded;
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[NC-SCROLL] client source={} delta={} hoveredSlot={} overScaleArea={} activeRecipeLoaded={} shouldHandle={} mouse=({}, {})",
					source,
					scrollDelta,
					hoveredMenuSlot,
					overScaleArea,
					activeRecipeLoaded,
					shouldHandle,
					(int) mouseX,
					(int) mouseY
			);
		}

		if (!shouldHandle) {
			return tryHandleOverlayHoverRecipeScroll(mouseX, mouseY, scrollDelta, activeRecipeLoaded, activeRecipeId);
		}

		int steps = Math.max(1, (int) Math.round(Math.abs(scrollDelta)));
		int signedSteps = scrollDelta > 0.0D ? steps : -steps;
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info("[NC-SCROLL] client sending adjust packet steps={} menu={}", signedSteps, this.menu.containerId);
		}
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SAdjustRecipeLoad(signedSteps));
		return true;
	}

	private boolean tryHandleOverlayHoverRecipeScroll(
			double mouseX,
			double mouseY,
			double scrollDelta,
			boolean activeRecipeLoaded,
			@Nullable ResourceLocation activeRecipeId
	) {
		ResourceLocation hoveredRecipeId = resolveHoveredOverlayRecipeId(mouseX, mouseY);
		if (hoveredRecipeId == null) {
			return false;
		}

		int steps = Math.max(1, (int) Math.round(Math.abs(scrollDelta)));
		if (!activeRecipeLoaded) {
			if (scrollDelta <= 0.0D) {
				return false;
			}

			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[NC-SCROLL] client source=overlay_hover recipe={} steps={} mode=prime_empty_grid",
						hoveredRecipeId,
						steps
				);
			}

			ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(hoveredRecipeId, false));
			rememberPendingScrollRecipe(hoveredRecipeId);
			if (steps > 1) {
				ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SAdjustRecipeLoad(steps - 1));
			}
			return true;
		}

		boolean matchesActiveRecipe = hoveredRecipeId.equals(activeRecipeId) || isSameRecipeOutput(hoveredRecipeId, activeRecipeId);
		if (!matchesActiveRecipe) {
			return false;
		}

		int signedSteps = scrollDelta > 0.0D ? steps : -steps;
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[NC-SCROLL] client source=overlay_hover recipe={} activeRecipe={} steps={} mode=adjust_loaded_grid",
					hoveredRecipeId,
					activeRecipeId,
					signedSteps
			);
		}
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SAdjustRecipeLoad(signedSteps));
		return true;
	}

	@Nullable
	private ResourceLocation resolveHoveredOverlayRecipeId(double mouseX, double mouseY) {
		ResourceLocation hoveredRecipeId = ProximityCraftingEmiCraftableFilterController.resolveHoveredRecipeId(this.menu, mouseX, mouseY);
		if (hoveredRecipeId != null) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-SCROLL] hover recipe resolved from EMI: {}", hoveredRecipeId);
			}
			return hoveredRecipeId;
		}
		hoveredRecipeId = ProximityCraftingJeiCraftableFilterController.resolveHoveredRecipeId(this.menu);
		if (hoveredRecipeId != null) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-SCROLL] hover recipe resolved from JEI: {}", hoveredRecipeId);
			}
			return hoveredRecipeId;
		}
		hoveredRecipeId = resolveHoveredVanillaRecipeBookRecipeId();
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info("[NC-SCROLL] hover recipe resolved from VANILLA book: {}", hoveredRecipeId);
		}
		return hoveredRecipeId;
	}

	@Nullable
	private ResourceLocation resolveHoveredVanillaRecipeBookRecipeId() {
		if (this.vanillaRecipeBookSuppressedByEmi) {
			return null;
		}
		if (!this.recipeBookComponent.isVisible()) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve skipped: recipe book not visible");
			}
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
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve failed: recipeBookPage field missing/null");
				}
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
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve: no hovered recipe button");
				}
				return null;
			}

			Method getRecipeMethod = findMethod(hoveredButton.getClass(), "getRecipe", 0);
			if (getRecipeMethod == null) {
				getRecipeMethod = findNoArgMethodReturningTypeNameContains(hoveredButton.getClass(), "RecipeHolder");
			}
			if (getRecipeMethod == null) {
				ResourceLocation fieldExtractedId = tryResolveRecipeIdFromRecipeButtonFields(hoveredButton);
				if (fieldExtractedId != null) {
					if (isDebugLoggingEnabled()) {
						ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve success via RecipeButton fields: {}", fieldExtractedId);
					}
					return fieldExtractedId;
				}
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve failed: getRecipe method not found on {}", hoveredButton.getClass().getName());
				}
				return null;
			}

			Object recipeHolder = getRecipeMethod.invoke(hoveredButton);
			if (recipeHolder == null) {
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve failed: getRecipe returned null");
				}
				return null;
			}
			if (recipeHolder.getClass().getSimpleName().contains("RecipeCollection")
					|| recipeHolder.getClass().getName().contains("recipebook.RecipeCollection")) {
				ResourceLocation collectionId = tryResolveRecipeIdFromRecipeCollection(recipeHolder, hoveredButton);
				if (collectionId != null) {
					if (isDebugLoggingEnabled()) {
						ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve success via RecipeCollection: {}", collectionId);
					}
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
					if (isDebugLoggingEnabled()) {
						ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve success via method id(): {}", resourceLocation);
					}
					return resourceLocation;
				}
			}

			Object recipeIdFieldValue = getFieldValue(recipeHolder, "id");
			if (recipeIdFieldValue instanceof ResourceLocation resourceLocation) {
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve success via field id: {}", resourceLocation);
				}
				return resourceLocation;
			}
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve failed: could not extract recipe id from {}", recipeHolder.getClass().getName());
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-SCROLL] vanilla hover resolve exception: {}", ignored.toString());
			}
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
		if (!this.vanillaRecipeBookSuppressedByEmi) {
			if (!this.menu.slots.isEmpty()) {
				this.recipeBookComponent.slotClicked(this.menu.slots.get(0));
			} else {
				this.recipeBookComponent.recipesUpdated();
			}
		}
		ProximityCraftingJeiCraftableFilterController.refreshIfEnabled(this.menu);
		ProximityCraftingEmiCraftableFilterController.refreshIfEnabled(this.menu);
	}

	private void requestRecipeBookSourceSync() {
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeBookSources(this.menu.containerId));
	}

	public void requestImmediateSourceSyncAndRefresh() {
		requestRecipeBookSourceSync();
		scheduleDeferredRecipeBookRefresh();
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

	private void renderAutoRefillToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		Rect2i bounds = getAutoRefillToggleBounds();
		int x = bounds.getX();
		int y = bounds.getY();
		boolean hovered = bounds.contains(mouseX, mouseY);
		boolean enabled = ProximityCraftingConfig.CLIENT.autoRefillAfterCraft.get();

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

		List<IngredientAvailabilityEntry> availabilityEntries = collectCurrentRecipeAvailabilityEntries();
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
			IngredientAvailabilityEntry entry = availabilityEntries.get(i);
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
	}

	private void renderProximityItemsTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!showProximityItemsPanel || hoveredProximityEntry == null) {
			return;
		}

		IngredientAvailabilityEntry entry = hoveredProximityEntry;
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

		boolean enabled = ProximityCraftingConfig.CLIENT.autoRefillAfterCraft.get();
		Component state = enabled
				? Component.translatable("options.on")
				: Component.translatable("options.off");
		List<Component> tooltip = List.of(
				Component.translatable("proximitycrafting.auto_refill.toggle"),
				Component.translatable("proximitycrafting.auto_refill.state", state)
		);
		guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
	}

	private boolean isMouseOverRecipeScaleArea(double mouseX, double mouseY) {
		int hoveredMenuSlot = getHoveredMenuSlotIndex();
		if (hoveredMenuSlot >= RESULT_SLOT_MENU_INDEX && hoveredMenuSlot < CRAFT_SLOT_MENU_END_EXCLUSIVE) {
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

	private int getHoveredMenuSlotIndex() {
		if (this.hoveredSlot == null) {
			return -1;
		}
		return this.menu.slots.indexOf(this.hoveredSlot);
	}

	private boolean hasActiveRecipeLoadedInGrid() {
		if (this.menu.getLevel() == null) {
			return false;
		}
		return this.menu.getLevel()
				.getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, this.menu.getCraftSlots(), this.menu.getLevel())
				.isPresent();
	}

	@Nullable
	private ResourceLocation getActiveRecipeIdForScroll() {
		if (this.menu.getLevel() != null) {
			Optional<CraftingRecipe> currentRecipe = this.menu.getLevel()
					.getRecipeManager()
					.getRecipeFor(RecipeType.CRAFTING, this.menu.getCraftSlots(), this.menu.getLevel());
			if (currentRecipe.isPresent() && currentRecipe.get().getId() != null) {
				return currentRecipe.get().getId();
			}
		}

		return localScrollRecipeId;
	}

	private void rememberPendingScrollRecipe(ResourceLocation recipeId) {
		this.localScrollRecipeId = recipeId;
	}

	@Override
	public void removed() {
		this.localScrollRecipeId = null;
		ProximityCraftingEmiCraftableFilterController.handleMenuClosed(this.menu.containerId);
		ProximityCraftingJeiCraftableFilterController.handleMenuClosed(this.menu.containerId);
		super.removed();
	}

	private void applyRememberedUiState() {
		if (!ProximityCraftingConfig.CLIENT.rememberToggleStates.get()) {
			showProximityItemsPanel = true;
			return;
		}

		showProximityItemsPanel = ProximityCraftingConfig.CLIENT.proximityItemsPanelOpen.get();

		if (ProximityCraftingConfig.CLIENT.jeiCraftableOnlyEnabled.get()) {
			ProximityCraftingJeiCraftableFilterController.setEnabled(menu, true);
		}
		if (ProximityCraftingConfig.CLIENT.emiCraftableOnlyEnabled.get()) {
			ProximityCraftingEmiCraftableFilterController.setEnabled(menu, true);
		}
	}

	private static void saveProximityItemsPanelState(boolean isOpen) {
		if (!ProximityCraftingConfig.CLIENT.rememberToggleStates.get()) {
			return;
		}
		ProximityCraftingConfig.CLIENT.proximityItemsPanelOpen.set(isOpen);
	}

	private Rect2i getProximityPanelBounds() {
		int baseX = this.leftPos - PROXIMITY_PANEL_WIDTH - PROXIMITY_PANEL_PADDING;
		if (!this.vanillaRecipeBookSuppressedByEmi && this.recipeBookComponent.isVisible()) {
			baseX -= RecipeBookComponent.IMAGE_WIDTH + PROXIMITY_PANEL_RECIPE_BOOK_EXTRA_SHIFT;
		}
		int baseY = this.topPos;
		int panelX = baseX + ProximityCraftingConfig.CLIENT.proximityItemsPanelOffsetX.get();
		int panelY = baseY + ProximityCraftingConfig.CLIENT.proximityItemsPanelOffsetY.get();
		return new Rect2i(panelX, panelY, PROXIMITY_PANEL_WIDTH, this.imageHeight);
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
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

	private List<IngredientAvailabilityEntry> collectCurrentRecipeAvailabilityEntries() {
		if (menu.getLevel() == null) {
			return List.of();
		}

		Optional<CraftingRecipe> recipeOptional = menu.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, menu.getCraftSlots(), menu.getLevel());
		if (recipeOptional.isEmpty()) {
			return List.of();
		}

		Map<String, IngredientTracker> ingredientTrackers = new LinkedHashMap<>();
		for (Ingredient ingredient : recipeOptional.get().getIngredients()) {
			if (ingredient == null || ingredient.isEmpty()) {
				continue;
			}

			ItemStack displayStack = resolveDisplayStack(ingredient);
			if (displayStack.isEmpty()) {
				continue;
			}

			String ingredientKey = buildIngredientKey(ingredient);
			IngredientTracker tracker = ingredientTrackers.computeIfAbsent(
					ingredientKey,
					key -> new IngredientTracker(displayStack, ingredient, 0)
			);
			tracker.requiredCount++;
		}

		if (ingredientTrackers.isEmpty()) {
			return List.of();
		}

		for (ProximityCraftingMenu.RecipeBookSourceEntry sourceEntry : menu.getClientRecipeBookSupplementalSources()) {
			ItemStack sourceStack = sourceEntry.stack();
			if (sourceStack.isEmpty() || sourceEntry.count() <= 0) {
				continue;
			}

			for (IngredientTracker tracker : ingredientTrackers.values()) {
				if (tracker.ingredient.test(sourceStack)) {
					tracker.availableCount += sourceEntry.count();
				}
			}
		}

		List<IngredientAvailabilityEntry> entries = new ArrayList<>(ingredientTrackers.size());
		for (IngredientTracker tracker : ingredientTrackers.values()) {
			entries.add(new IngredientAvailabilityEntry(tracker.displayStack, tracker.availableCount, tracker.requiredCount));
		}
		entries.sort(Comparator.comparingInt(IngredientAvailabilityEntry::requiredCount).reversed());
		return entries;
	}

	private static ItemStack resolveDisplayStack(Ingredient ingredient) {
		ItemStack[] options = ingredient.getItems();
		if (options.length == 0) {
			return ItemStack.EMPTY;
		}

		ItemStack display = options[0].copy();
		display.setCount(1);
		return display;
	}

	private static String buildIngredientKey(Ingredient ingredient) {
		List<String> optionKeys = new ArrayList<>();
		for (ItemStack option : ingredient.getItems()) {
			if (option.isEmpty()) {
				continue;
			}
			ItemStack normalized = option.copy();
			normalized.setCount(1);
			optionKeys.add(buildStackKey(normalized));
		}
		optionKeys.sort(String::compareTo);
		return String.join("|", optionKeys);
	}

	private static String buildStackKey(ItemStack stack) {
		CompoundTag serialized = new CompoundTag();
		stack.save(serialized);
		serialized.remove("Count");
		return serialized.toString();
	}

	public void scheduleDeferredRecipeBookRefresh() {
		deferredRefreshTicks = Math.max(deferredRefreshTicks, 2);
	}

	private void sendClientPreferencesUpdate() {
		ProximityCraftingNetwork.CHANNEL.sendToServer(
				new C2SUpdateClientPreferences(
						this.menu.containerId,
						ProximityCraftingConfig.CLIENT.autoRefillAfterCraft.get(),
						ProximityCraftingConfig.CLIENT.includePlayerInventory.get(),
						ProximityCraftingConfig.CLIENT.sourcePriority.get()
				)
		);
	}

	private static final class IngredientTracker {
		private final ItemStack displayStack;
		private final Ingredient ingredient;
		private int availableCount;
		private int requiredCount;

		private IngredientTracker(ItemStack displayStack, Ingredient ingredient, int availableCount) {
			this.displayStack = displayStack;
			this.ingredient = ingredient;
			this.availableCount = availableCount;
		}
	}

	private record IngredientAvailabilityEntry(ItemStack displayStack, int availableCount, int requiredCount) {
	}
}



