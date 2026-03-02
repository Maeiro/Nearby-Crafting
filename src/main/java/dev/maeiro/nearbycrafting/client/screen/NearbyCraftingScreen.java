package dev.maeiro.nearbycrafting.client.screen;

import dev.maeiro.nearbycrafting.client.compat.emi.NearbyCraftingEmiCraftableFilterController;
import dev.maeiro.nearbycrafting.client.compat.jei.NearbyCraftingJeiCraftableFilterController;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.networking.C2SRequestRecipeBookSources;
import dev.maeiro.nearbycrafting.networking.C2SUpdateClientPreferences;
import dev.maeiro.nearbycrafting.networking.NearbyCraftingNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class NearbyCraftingScreen extends AbstractContainerScreen<NearbyCraftingMenu> implements RecipeUpdateListener {
	private static final ResourceLocation CRAFTING_TABLE_LOCATION = new ResourceLocation("textures/gui/container/crafting_table.png");
	private static final ResourceLocation RECIPE_BUTTON_LOCATION = new ResourceLocation("textures/gui/recipe_button.png");
	private static final ResourceLocation NEARBY_ITEMS_TOGGLE_ON_ICON = new ResourceLocation("nearbycrafting", "icon/ok-icon.png");
	private static final ResourceLocation NEARBY_ITEMS_TOGGLE_OFF_ICON = new ResourceLocation("nearbycrafting", "icon/x-icon.png");
	private static final int RECIPE_BOOK_SOURCE_SYNC_INTERVAL_TICKS = 20;
	private static final int STATUS_COLOR_SUCCESS = 0x55FF55;
	private static final int STATUS_COLOR_FAILURE = 0xFF5555;
	private static final int STATUS_COLOR_INFO = 0xFFFFFF;
	private static final int NEARBY_PANEL_WIDTH = 74;
	private static final int NEARBY_PANEL_PADDING = 8;
	private static final int TOGGLE_SIZE = 18;
	private static final int TOGGLE_ICON_SIZE = 12;
	private static final int TOGGLE_ICON_TEX_WIDTH = 479;
	private static final int TOGGLE_ICON_TEX_HEIGHT = 440;
	private static final int PANEL_HEADER_COLOR = 0xFFE5E5E5;
	private static final int PANEL_COUNT_COLOR = 0xFFFFFFFF;
	private final RecipeBookComponent recipeBookComponent = new RecipeBookComponent();
	private boolean widthTooNarrow;
	private int recipeBookSourceSyncTicker = 0;
	private int deferredRefreshTicks = 0;
	private boolean showNearbyItemsPanel = true;
	private Component statusMessage;
	private long statusMessageUntilMs = 0L;
	private int statusMessageColor = STATUS_COLOR_INFO;
	private IngredientAvailabilityEntry hoveredNearbyEntry;

	public NearbyCraftingScreen(NearbyCraftingMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Override
	protected void init() {
		super.init();
		NearbyCraftingNetwork.CHANNEL.sendToServer(
				new C2SUpdateClientPreferences(
						this.menu.containerId,
						NearbyCraftingConfig.CLIENT.autoRefillAfterCraft.get(),
						NearbyCraftingConfig.CLIENT.includePlayerInventory.get(),
						NearbyCraftingConfig.CLIENT.sourcePriority.get()
				)
		);
		requestRecipeBookSourceSync();

		this.widthTooNarrow = this.width < 379;
		this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
		this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
		this.addRenderableWidget(new ImageButton(this.leftPos + 5, this.height / 2 - 49, 20, 18, 0, 0, 19, RECIPE_BUTTON_LOCATION, button -> {
			this.recipeBookComponent.toggleVisibility();
			this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
			button.setPosition(this.leftPos + 5, this.height / 2 - 49);
		}));
		this.addWidget(this.recipeBookComponent);
		this.setInitialFocus(this.recipeBookComponent);
		this.titleLabelX = 29;
	}

	@Override
	public void containerTick() {
		super.containerTick();
		this.recipeBookComponent.tick();
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
		hoveredNearbyEntry = null;
		this.renderBackground(guiGraphics);
		if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
			this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
			this.recipeBookComponent.render(guiGraphics, mouseX, mouseY, partialTick);
		} else {
			this.recipeBookComponent.render(guiGraphics, mouseX, mouseY, partialTick);
			super.render(guiGraphics, mouseX, mouseY, partialTick);
			this.recipeBookComponent.renderGhostRecipe(guiGraphics, this.leftPos, this.topPos, true, partialTick);
		}
		renderNearbyItemsToggle(guiGraphics, mouseX, mouseY);
		renderNearbyItemsPanel(guiGraphics, mouseX, mouseY);

		this.renderTooltip(guiGraphics, mouseX, mouseY);
		renderNearbyItemsTooltip(guiGraphics, mouseX, mouseY);
		this.recipeBookComponent.renderTooltip(guiGraphics, this.leftPos, this.topPos, mouseX, mouseY);
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
		return (!this.widthTooNarrow || !this.recipeBookComponent.isVisible()) && super.isHovering(x, y, width, height, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isMouseOverNearbyItemsToggle(mouseX, mouseY)) {
			showNearbyItemsPanel = !showNearbyItemsPanel;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}

		if (this.recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
			this.setFocused(this.recipeBookComponent);
			return true;
		}
		return this.widthTooNarrow && this.recipeBookComponent.isVisible() || super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
		boolean outside = mouseX < (double) guiLeft || mouseY < (double) guiTop || mouseX >= (double) (guiLeft + this.imageWidth) || mouseY >= (double) (guiTop + this.imageHeight);
		return this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, mouseButton) && outside;
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType clickType) {
		super.slotClicked(slot, slotId, mouseButton, clickType);
		this.recipeBookComponent.slotClicked(slot);
	}

	@Override
	public void recipesUpdated() {
		this.recipeBookComponent.recipesUpdated();
	}

	@Override
	public RecipeBookComponent getRecipeBookComponent() {
		return this.recipeBookComponent;
	}

	public void refreshRecipeBookFromSyncedSources() {
		if (!this.menu.slots.isEmpty()) {
			this.recipeBookComponent.slotClicked(this.menu.slots.get(0));
		} else {
			this.recipeBookComponent.recipesUpdated();
		}
		NearbyCraftingJeiCraftableFilterController.refreshIfEnabled(this.menu);
		NearbyCraftingEmiCraftableFilterController.refreshIfEnabled(this.menu);
	}

	private void requestRecipeBookSourceSync() {
		NearbyCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeBookSources(this.menu.containerId));
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

	private void renderNearbyItemsToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int x = leftPos + 4;
		int y = topPos + 4;
		boolean hovered = mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;

		int background = hovered ? 0xFF5E5E5E : 0xFF4A4A4A;
		guiGraphics.fill(x, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, background);
		guiGraphics.fill(x, y, x + TOGGLE_SIZE, y + 1, 0xFFFFFFFF);
		guiGraphics.fill(x, y + TOGGLE_SIZE - 1, x + TOGGLE_SIZE, y + TOGGLE_SIZE, 0xFF1A1A1A);
		guiGraphics.fill(x, y, x + 1, y + TOGGLE_SIZE, 0xFFFFFFFF);
		guiGraphics.fill(x + TOGGLE_SIZE - 1, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, 0xFF1A1A1A);

		ResourceLocation icon = showNearbyItemsPanel ? NEARBY_ITEMS_TOGGLE_ON_ICON : NEARBY_ITEMS_TOGGLE_OFF_ICON;
		int iconX = x + (TOGGLE_SIZE - TOGGLE_ICON_SIZE) / 2;
		int iconY = y + (TOGGLE_SIZE - TOGGLE_ICON_SIZE) / 2;
		guiGraphics.blit(icon, iconX, iconY, 0, 0, TOGGLE_ICON_SIZE, TOGGLE_ICON_SIZE, TOGGLE_ICON_TEX_WIDTH, TOGGLE_ICON_TEX_HEIGHT);
	}

	private void renderNearbyItemsPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!showNearbyItemsPanel) {
			return;
		}

		List<IngredientAvailabilityEntry> availabilityEntries = collectCurrentRecipeAvailabilityEntries();
		int panelX = leftPos - NEARBY_PANEL_WIDTH - NEARBY_PANEL_PADDING;
		int panelY = topPos;
		int panelHeight = imageHeight;

		guiGraphics.fill(panelX, panelY, panelX + NEARBY_PANEL_WIDTH, panelY + panelHeight, 0xCC323232);
		guiGraphics.fill(panelX, panelY, panelX + NEARBY_PANEL_WIDTH, panelY + 1, 0xFFFFFFFF);
		guiGraphics.fill(panelX, panelY + panelHeight - 1, panelX + NEARBY_PANEL_WIDTH, panelY + panelHeight, 0xFF1A1A1A);
		guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFFFFFFFF);
		guiGraphics.fill(panelX + NEARBY_PANEL_WIDTH - 1, panelY, panelX + NEARBY_PANEL_WIDTH, panelY + panelHeight, 0xFF1A1A1A);

		guiGraphics.drawCenteredString(font, Component.translatable("nearbycrafting.nearby_items.title"), panelX + NEARBY_PANEL_WIDTH / 2, panelY + 6, PANEL_HEADER_COLOR);
		guiGraphics.fill(panelX + 5, panelY + 20, panelX + NEARBY_PANEL_WIDTH - 5, panelY + 21, 0x66FFFFFF);

		int rowY = panelY + 27;
		int maxRows = Math.max(0, (panelHeight - 34) / 20);
		for (int i = 0; i < availabilityEntries.size() && i < maxRows; i++) {
			IngredientAvailabilityEntry entry = availabilityEntries.get(i);
			int entryY = rowY + i * 20;
			int iconX = panelX + 7;

			guiGraphics.renderItem(entry.displayStack(), iconX, entryY);
			String countText = Integer.toString(entry.availableCount());
			int countX = panelX + NEARBY_PANEL_WIDTH - 8 - font.width(countText);
			guiGraphics.drawString(font, countText, countX, entryY + 4, PANEL_COUNT_COLOR, false);

			if (mouseX >= panelX + 4 && mouseX < panelX + NEARBY_PANEL_WIDTH - 4 && mouseY >= entryY && mouseY < entryY + 18) {
				hoveredNearbyEntry = entry;
			}
		}
	}

	private void renderNearbyItemsTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!showNearbyItemsPanel || hoveredNearbyEntry == null) {
			return;
		}

		IngredientAvailabilityEntry entry = hoveredNearbyEntry;
		List<Component> tooltip = List.of(
				entry.displayStack().getHoverName(),
				Component.translatable("nearbycrafting.nearby_items.available", entry.availableCount()),
				Component.translatable("nearbycrafting.nearby_items.required", entry.requiredCount())
		);
		guiGraphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
	}

	private boolean isMouseOverNearbyItemsToggle(double mouseX, double mouseY) {
		int x = leftPos + 4;
		int y = topPos + 4;
		return mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;
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

		for (NearbyCraftingMenu.RecipeBookSourceEntry sourceEntry : menu.getClientRecipeBookSupplementalSources()) {
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

	@Override
	public void removed() {
		NearbyCraftingEmiCraftableFilterController.handleMenuClosed(this.menu.containerId);
		NearbyCraftingJeiCraftableFilterController.handleMenuClosed(this.menu.containerId);
		super.removed();
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
