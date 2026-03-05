package dev.maeiro.proximitycrafting.client.compat.emi;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import net.minecraft.client.Minecraft;
import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EMI craftable-only filter controller using reflection so Proximity Crafting remains runtime-safe without EMI.
 */
public final class ProximityCraftingEmiCraftableFilterController {
	private static final String EMI_SCREEN_MANAGER_CLASS = "dev.emi.emi.screen.EmiScreenManager";
	private static final String EMI_SIDEBARS_CLASS = "dev.emi.emi.runtime.EmiSidebars";
	private static final String EMI_STACK_LIST_CLASS = "dev.emi.emi.registry.EmiStackList";
	private static final String EMI_SEARCH_CLASS = "dev.emi.emi.search.EmiSearch";
	private static final String EMI_CONFIG_CLASS = "dev.emi.emi.config.EmiConfig";
	private static final String EMI_SIDEBAR_TYPE_CLASS = "dev.emi.emi.config.SidebarType";
	private static final String EMI_RECIPE_BOOK_ACTION_CLASS = "dev.emi.emi.config.RecipeBookAction";
	private static final long REFRESH_DEBOUNCE_MS = 150L;
	private static final long ENABLE_REFRESH_DELAY_MS = 120L;
	private static final long ACTION_IDLE_REFRESH_DELAY_MS = 300L;
	private static final long CRAFTABLE_COMPUTE_BUDGET_NS = 2_000_000L;
	private static final long CRAFTABLE_COMPUTE_SLICE_WARN_NS = 8_000_000L;
	private static final long CRAFTABLE_COMPUTE_SLICE_WARN_LOG_INTERVAL_MS = 250L;
	private static final long CRAFTABLE_COMPUTE_RESTART_LOG_INTERVAL_MS = 250L;
	private static final int CRAFTABLE_COMPUTE_MIN_RECIPES_PER_SLICE = 32;
	private static final String SEARCH_SIDEBAR_FOCUS_FIELD = "searchSidebarFocus";
	private static final String EMPTY_SEARCH_SIDEBAR_FOCUS_FIELD = "emptySearchSidebarFocus";

	private static boolean enabled;
	private static boolean transitionActive;
	private static int activeContainerId = -1;
	private static long lastRefreshAtMs = 0L;
	private static long nextRefreshAllowedAtMs = 0L;
	private static boolean hasAppliedCraftables = false;
	private static boolean pendingRefresh = false;
	private static boolean sourceSnapshotDirty = false;
	private static boolean sourceSyncInFlight = false;
	private static long lastInputSignature = Long.MIN_VALUE;
	private static long cachedAvailabilitySignature = Long.MIN_VALUE;
	private static int cachedAvailabilityContainerId = -1;
	private static Set<String> cachedCraftableOutputItemIds = Set.of();
	private static final Map<String, ResourceLocation> cachedCraftableRecipeIdsByOutputKey = new LinkedHashMap<>();
	@Nullable
	private static CraftableComputationState craftableComputation;
	private static final Set<String> lastAppliedCraftableOutputIds = new LinkedHashSet<>();
	private static final Set<String> stickyCraftableOutputItemIds = new LinkedHashSet<>();
	private static List<?> pinnedIndexStacks = List.of();
	private static long lastRuntimeLogAtMs = 0L;
	private static long lastComputeSliceWarnLogAtMs = 0L;
	private static long lastComputeRestartLogAtMs = 0L;
	private static boolean nativeCraftablesSuppressed = false;

	@Nullable
	private static Object previousSearchSidebarType;
	@Nullable
	private static List<?> previousIndexFilteredStacks;
	@Nullable
	private static Object previousSearchSidebarFocusSetting;
	@Nullable
	private static Object previousEmptySearchSidebarFocusSetting;
	private static boolean searchSidebarFocusOverridden;

	private ProximityCraftingEmiCraftableFilterController() {
	}

	public static boolean isRuntimeAvailable() {
		return ModList.get().isLoaded("emi");
	}

	public static boolean isEnabledFor(int containerId) {
		return enabled && activeContainerId == containerId;
	}

	public static boolean isTransitionBlockingInput() {
		return transitionActive
				|| sourceSyncInFlight
				|| pendingRefresh
				|| craftableComputation != null;
	}

	public static boolean isNativeCraftablesSuppressed() {
		return nativeCraftablesSuppressed;
	}

	public static void enforceIndexOnlyMode() {
		if (!isRuntimeAvailable()) {
			return;
		}
		nativeCraftablesSuppressed = true;

		Object indexType = resolveSidebarType("INDEX");
		if (indexType == null) {
			return;
		}

		Class<?> emiConfigClass = findClass(EMI_CONFIG_CLASS);
		if (emiConfigClass == null) {
			return;
		}

		Object toggleVisibilityAction = resolveRecipeBookAction("TOGGLE_VISIBILITY");
		if (toggleVisibilityAction != null) {
			setStaticFieldValue(emiConfigClass, "recipeBookAction", toggleVisibilityAction);
		}
		sanitizeSidebarPages(emiConfigClass, "leftSidebarPages", indexType);
		sanitizeSidebarPages(emiConfigClass, "rightSidebarPages", indexType);
		sanitizeSidebarPages(emiConfigClass, "topSidebarPages", indexType);
		sanitizeSidebarPages(emiConfigClass, "bottomSidebarPages", indexType);
		sanitizeSidebarSubpanels(emiConfigClass, "leftSidebarSubpanels");
		sanitizeSidebarSubpanels(emiConfigClass, "rightSidebarSubpanels");
		sanitizeSidebarSubpanels(emiConfigClass, "topSidebarSubpanels");
		sanitizeSidebarSubpanels(emiConfigClass, "bottomSidebarSubpanels");
		// Keep Proximity Crafting's own toggle operational.
		if (!enabled) {
			applySearchSidebarConfig(indexType);
			setCraftables(List.of());
			pinnedIndexStacks = List.of();
			focusSearchSidebarType(indexType);
			requestIndexRefresh(indexType);
		}
	}

	public static void setEnabled(ProximityCraftingMenu menu, boolean shouldEnable) {
		if (!isRuntimeAvailable()) {
			return;
		}
		if (!shouldEnable) {
			disableAndRestore();
			return;
		}

		if (!isEnabledFor(menu.containerId)) {
			if (enabled) {
				disableAndRestore();
			}
			previousSearchSidebarType = null;
			previousIndexFilteredStacks = null;
			previousSearchSidebarFocusSetting = null;
			previousEmptySearchSidebarFocusSetting = null;
			searchSidebarFocusOverridden = false;
			hasAppliedCraftables = false;
			lastAppliedCraftableOutputIds.clear();
			List<?> currentFiltered = getCurrentIndexFilteredStacks();
			if (currentFiltered == null || currentFiltered.isEmpty()) {
				currentFiltered = getAllIndexStacks();
			}
			pinnedIndexStacks = currentFiltered == null ? List.of() : List.copyOf(currentFiltered);
			pendingRefresh = false;
			sourceSnapshotDirty = false;
			sourceSyncInFlight = false;
			lastInputSignature = Long.MIN_VALUE;
			cachedAvailabilitySignature = Long.MIN_VALUE;
			cachedAvailabilityContainerId = -1;
			cachedCraftableOutputItemIds = Set.of();
			cachedCraftableRecipeIdsByOutputKey.clear();
			craftableComputation = null;
			nextRefreshAllowedAtMs = 0L;
		}

		enabled = true;
		activeContainerId = menu.containerId;
		applyStartupCraftableView(menu);
		lastRefreshAtMs = 0L;
		nextRefreshAllowedAtMs = System.currentTimeMillis() + ENABLE_REFRESH_DELAY_MS;
		pendingRefresh = true;
		sourceSnapshotDirty = true;
		sourceSyncInFlight = false;
		logRuntimeState(menu, "setEnabled:pending_refresh");
	}

	public static void refreshIfEnabled(ProximityCraftingMenu menu) {
		if (!isEnabledFor(menu.containerId) || !isRuntimeAvailable()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (transitionActive) {
			logRuntimeState(menu, "refreshIfEnabled:skip_transition");
			return;
		}
		if (sourceSyncInFlight) {
			pendingRefresh = true;
			logRuntimeState(menu, "refreshIfEnabled:skip_sync_in_flight");
			return;
		}
		if (craftableComputation != null) {
			pendingRefresh = true;
			logRuntimeState(menu, "refreshIfEnabled:skip_compute_in_progress");
			return;
		}
		if (now < nextRefreshAllowedAtMs) {
			pendingRefresh = true;
			logRuntimeState(menu, "refreshIfEnabled:skip_enable_delay");
			return;
		}
		if (now - lastRefreshAtMs < REFRESH_DEBOUNCE_MS) {
			pendingRefresh = true;
			logRuntimeState(menu, "refreshIfEnabled:skip_debounce");
			return;
		}
		if (!pendingRefresh && !sourceSnapshotDirty) {
			logRuntimeState(menu, "refreshIfEnabled:skip_no_pending");
			return;
		}
		lastRefreshAtMs = now;
		logRuntimeState(menu, "refreshIfEnabled:run");
		refresh(menu);
	}

	public static void processDeferred() {
		processCraftableComputationSlice();
		if (!enabled || craftableComputation != null || sourceSyncInFlight || transitionActive || !pendingRefresh) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextRefreshAllowedAtMs || (now - lastRefreshAtMs) < REFRESH_DEBOUNCE_MS) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof ProximityCraftingScreen screen)) {
			return;
		}
		ProximityCraftingMenu menu = screen.getMenu();
		if (!isEnabledFor(menu.containerId)) {
			return;
		}
		lastRefreshAtMs = now;
		refresh(menu);
	}

	public static void onSourceSyncStateUpdated(ProximityCraftingMenu menu, boolean inFlight, boolean sourcesChanged) {
		if (!isEnabledFor(menu.containerId) || !isRuntimeAvailable()) {
			return;
		}
		sourceSyncInFlight = inFlight;
		long nowMs = System.currentTimeMillis();
		if (!inFlight) {
			if (sourcesChanged) {
				pendingRefresh = true;
				sourceSnapshotDirty = true;
				nextRefreshAllowedAtMs = Math.max(nextRefreshAllowedAtMs, nowMs + ACTION_IDLE_REFRESH_DELAY_MS);
			} else if (!pendingRefresh) {
				nextRefreshAllowedAtMs = Math.min(nextRefreshAllowedAtMs, nowMs);
			}
		}
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-EMI] syncState menu={} inFlight={} sourcesChanged={} pendingRefresh={} dirty={}",
					menu.containerId,
					inFlight,
					sourcesChanged,
					pendingRefresh,
					sourceSnapshotDirty
			);
		}
	}

	public static void handleMenuClosed(int containerId) {
		if (isEnabledFor(containerId)) {
			disableAndRestore();
		}
	}

	public static void onRecipeActionQueued(ProximityCraftingMenu menu) {
		if (!isEnabledFor(menu.containerId) || !isRuntimeAvailable()) {
			return;
		}
		long nextAllowedAt = System.currentTimeMillis() + ACTION_IDLE_REFRESH_DELAY_MS;
		if (nextAllowedAt > nextRefreshAllowedAtMs) {
			nextRefreshAllowedAtMs = nextAllowedAt;
		}
		pendingRefresh = true;
		logRuntimeState(menu, "action_queued:set_idle_window");
	}

	public static void enforceCraftableSidebarIfEnabled(ProximityCraftingMenu menu) {
		if (!isEnabledFor(menu.containerId) || !isRuntimeAvailable()) {
			return;
		}
		if (transitionActive) {
			logRuntimeState(menu, "enforce:skip_transition");
			return;
		}
		Object indexType = resolveSidebarType("INDEX");
		if (indexType != null) {
			applySearchSidebarConfig(indexType);
			setIndexFilteredStacks(pinnedIndexStacks);
			Object current = getCurrentSearchSidebarType();
			if (current == null || !current.equals(indexType)) {
				focusSearchSidebarType(indexType);
				logRuntimeState(menu, "enforce:forced_focus_index");
			} else {
				logRuntimeState(menu, "enforce:focus_ok_index");
			}
		}
	}

	public static boolean handleIngredientClick(ProximityCraftingMenu menu, double mouseX, double mouseY, int mouseButton) {
		if (!isEnabledFor(menu.containerId) || !isRuntimeAvailable()) {
			return false;
		}
		if (mouseButton != 0) {
			return false;
		}
		// Keep direct-fill intentionally behind Alt to avoid conflicting with EMI's normal recipe UI flow.
		if (!Screen.hasAltDown()) {
			return false;
		}

		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass == null) {
			return false;
		}

		Object hovered = invokeStatic(screenManagerClass, "getHoveredStack", 3, (int) mouseX, (int) mouseY, false);
		if (hovered == null) {
			return false;
		}

		ResourceLocation recipeId = resolveRecipeIdFromInteraction(hovered);
		if (recipeId == null) {
			ItemStack outputStack = resolveOutputStackFromInteraction(hovered);
			recipeId = resolveCraftableRecipeIdForOutput(menu, outputStack);
		}
		if (recipeId == null) {
			return false;
		}

		boolean craftAll = Screen.hasShiftDown();
		boolean queued = ProximityCraftingScreen.enqueueRecipeFillIfScreenOpen(
				menu,
				recipeId,
				craftAll,
				"emi_direct_alt_click"
		);
		if (!queued) {
			ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(recipeId, craftAll));
		}
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-EMI] DirectFill menu={} recipeId={} craftAll={} queued={} mouse=({}, {})",
					menu.containerId,
					recipeId,
					craftAll,
					queued,
					(int) mouseX,
					(int) mouseY
			);
		}
		return true;
	}

	@Nullable
	public static ResourceLocation resolveHoveredRecipeId(ProximityCraftingMenu menu, double mouseX, double mouseY) {
		if (!isRuntimeAvailable()) {
			return null;
		}

		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass == null) {
			return null;
		}

		Object hovered = invokeStatic(screenManagerClass, "getHoveredStack", 3, (int) mouseX, (int) mouseY, false);
		if (hovered == null) {
			return null;
		}

		ResourceLocation recipeId = resolveRecipeIdFromInteraction(hovered);
		if (recipeId != null) {
			return recipeId;
		}

		ItemStack outputStack = resolveOutputStackFromInteraction(hovered);
		if (outputStack.isEmpty()) {
			return null;
		}
		return resolveCraftableRecipeIdForOutput(menu, outputStack);
	}

	@Nullable
	public static Rect2i getEmiSearchFieldBounds() {
		if (!isRuntimeAvailable()) {
			return null;
		}

		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass == null) {
			return null;
		}

		Object searchWidget = getStaticFieldValue(screenManagerClass, "search");
		if (searchWidget == null) {
			return null;
		}

		Integer x = readIntProperty(searchWidget, "getX", "x");
		Integer y = readIntProperty(searchWidget, "getY", "y");
		Integer width = readIntProperty(searchWidget, "getWidth", "width");
		Integer height = readIntProperty(searchWidget, "getHeight", "height");
		if (x == null || y == null || width == null || height == null) {
			return null;
		}
		return new Rect2i(x, y, width, height);
	}

	private static void refresh(ProximityCraftingMenu menu) {
		if (!isRuntimeAvailable()) {
			return;
		}
		long inputSignature = computeInputSignature(menu);
		if (hasAppliedCraftables && inputSignature == lastInputSignature && !sourceSnapshotDirty && !pendingRefresh) {
			logRuntimeState(menu, "refresh:skip_signature_unchanged");
			return;
		}

		transitionActive = true;
		try {
			Object indexType = resolveSidebarType("INDEX");
			if (indexType == null) {
				return;
			}

			if (previousSearchSidebarType == null) {
				previousSearchSidebarType = getCurrentSearchSidebarType();
			}
			if (previousIndexFilteredStacks == null) {
				List<?> currentFiltered = getCurrentIndexFilteredStacks();
				previousIndexFilteredStacks = currentFiltered == null ? List.of() : List.copyOf(currentFiltered);
			}
			applySearchSidebarConfig(indexType);

			Set<String> craftableOutputIds = getCraftableOutputItemIdsIfReady(menu, inputSignature, "refresh", true);
			if (craftableOutputIds == null) {
				pendingRefresh = true;
				logRuntimeState(menu, "refresh:compute_pending");
				return;
			}
			List<?> indexIngredients = getAllIndexStacks();
			if (indexIngredients.isEmpty()) {
				indexIngredients = getSidebarStacks(indexType);
			}
			List<Object> filteredIndexStacks = filterIngredientsByItemId(indexIngredients, craftableOutputIds);
			boolean changed = !hasAppliedCraftables || !craftableOutputIds.equals(lastAppliedCraftableOutputIds);
			setIndexFilteredStacks(filteredIndexStacks);
			pinnedIndexStacks = List.copyOf(filteredIndexStacks);
			focusSearchSidebarType(indexType);
			logRuntimeState(
					menu,
					"refresh:applied changed=" + changed
							+ " outputs=" + craftableOutputIds.size()
							+ " filtered=" + filteredIndexStacks.size()
							+ " index=" + indexIngredients.size()
							+ " dirty=" + sourceSnapshotDirty
			);
			if (!changed) {
				requestSearchRefreshOnly();
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[PROXC-EMI] refresh lightweight (unchanged index-filter set) menu={} craftableOutputs={} filteredIngredients={}",
							menu.containerId,
							craftableOutputIds.size(),
							filteredIndexStacks.size()
					);
				}
				lastInputSignature = inputSignature;
				sourceSnapshotDirty = false;
				pendingRefresh = false;
				return;
			}
			requestSearchRefreshOnly();
			focusSearchSidebarType(indexType);
			hasAppliedCraftables = true;
			lastAppliedCraftableOutputIds.clear();
			lastAppliedCraftableOutputIds.addAll(craftableOutputIds);
			stickyCraftableOutputItemIds.clear();
			stickyCraftableOutputItemIds.addAll(craftableOutputIds);
			lastInputSignature = inputSignature;
			sourceSnapshotDirty = false;
			pendingRefresh = false;

			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-EMI] refresh menu={} indexCandidates={} craftableOutputs={} filteredIndex={}",
						menu.containerId,
						indexIngredients.size(),
						craftableOutputIds.size(),
						filteredIndexStacks.size()
				);
			}
		} finally {
			transitionActive = false;
		}
	}

	private static void disableAndRestore() {
		transitionActive = true;
		try {
			List<?> restoreStacks = previousIndexFilteredStacks;
			if (restoreStacks == null || restoreStacks.isEmpty()) {
				restoreStacks = getAllIndexStacks();
			}
			if (restoreStacks != null && !restoreStacks.isEmpty()) {
				setIndexFilteredStacks(restoreStacks);
			}
			if (previousSearchSidebarType != null) {
				focusSearchSidebarType(previousSearchSidebarType);
			}
			restoreSearchSidebarConfig();
			requestSearchRefreshOnly();

			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[PROXC-EMI] disableAndRestore restoredIndexFiltered={}", restoreStacks == null ? 0 : restoreStacks.size());
			}
		} finally {
			enabled = false;
			activeContainerId = -1;
			previousSearchSidebarType = null;
			previousIndexFilteredStacks = null;
			previousSearchSidebarFocusSetting = null;
			previousEmptySearchSidebarFocusSetting = null;
			searchSidebarFocusOverridden = false;
			hasAppliedCraftables = false;
			lastAppliedCraftableOutputIds.clear();
			pinnedIndexStacks = List.of();
			pendingRefresh = false;
			sourceSnapshotDirty = false;
			sourceSyncInFlight = false;
			lastInputSignature = Long.MIN_VALUE;
			cachedAvailabilitySignature = Long.MIN_VALUE;
			cachedAvailabilityContainerId = -1;
			cachedCraftableOutputItemIds = Set.of();
			cachedCraftableRecipeIdsByOutputKey.clear();
			craftableComputation = null;
			nextRefreshAllowedAtMs = 0L;
			transitionActive = false;
		}
	}

	private static void updateSearchSidebarOnly() {
		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass != null) {
			invokeStatic(screenManagerClass, "updateSearchSidebar", 0);
		}
	}

	private static void requestSearchRefreshOnly() {
		updateSearchSidebarOnly();
		Class<?> searchClass = findClass(EMI_SEARCH_CLASS);
		if (searchClass != null) {
			invokeStatic(searchClass, "update", 0);
		}
	}

	private static void requestIndexRefresh(@Nullable Object indexType) {
		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass != null) {
			if (indexType != null) {
				invokeStatic(screenManagerClass, "repopulatePanels", 1, indexType);
			}
			invokeStatic(screenManagerClass, "updateSearchSidebar", 0);
		}

		Class<?> searchClass = findClass(EMI_SEARCH_CLASS);
		if (searchClass != null) {
			invokeStatic(searchClass, "update", 0);
		}
	}

	private static void logRuntimeState(ProximityCraftingMenu menu, String stage) {
		if (!isDebugLoggingEnabled()) {
			return;
		}
		if ("enforce:focus_ok_index".equals(stage)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastRuntimeLogAtMs < 1500L) {
			return;
		}
		lastRuntimeLogAtMs = now;

		Object currentType = getCurrentSearchSidebarType();
		String currentTypeName = currentType instanceof Enum<?> e ? e.name() : String.valueOf(currentType);
		ProximityCrafting.LOGGER.info(
				"[PROXC-EMI-RUNTIME] stage={} menu={} enabled={} transition={} currentSidebar={} pinned={} applied={} refreshDebounceMs={}",
				stage,
				menu.containerId,
				enabled,
				transitionActive,
				currentTypeName,
				pinnedIndexStacks.size(),
				hasAppliedCraftables,
				REFRESH_DEBOUNCE_MS
		);
	}

	private static long computeInputSignature(ProximityCraftingMenu menu) {
		long signature = 1469598103934665603L;
		signature = mix(signature, menu.isIncludePlayerInventory() ? 1 : 0);
		signature = mix(signature, menu.getSourcePriority().ordinal());
		if (menu.isIncludePlayerInventory()) {
			Inventory inventory = menu.getPlayer().getInventory();
			for (ItemStack stack : inventory.items) {
				signature = mixStack(signature, stack);
			}
			for (ItemStack stack : inventory.armor) {
				signature = mixStack(signature, stack);
			}
			for (ItemStack stack : inventory.offhand) {
				signature = mixStack(signature, stack);
			}
		}
		for (ProximityCraftingMenu.RecipeBookSourceEntry sourceEntry : menu.getClientRecipeBookSupplementalSources()) {
			signature = mixStack(signature, sourceEntry.stack());
			signature = mix(signature, sourceEntry.count());
		}
		return signature;
	}

	private static long mixStack(long current, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return mix(current, 0);
		}
		ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
		current = mix(current, itemId == null ? 0 : itemId.hashCode());
		current = mix(current, stack.getDamageValue());
		current = mix(current, stack.getCount());
		if (stack.hasTag()) {
			current = mix(current, stack.getTag().hashCode());
		}
		return current;
	}

	private static long mix(long current, int value) {
		return (current ^ value) * 1099511628211L;
	}

	private static void applySearchSidebarConfig(Object sidebarType) {
		if (sidebarType == null) {
			return;
		}

		if (!searchSidebarFocusOverridden) {
			previousSearchSidebarFocusSetting = getEmiConfigSidebarFocus(SEARCH_SIDEBAR_FOCUS_FIELD);
			previousEmptySearchSidebarFocusSetting = getEmiConfigSidebarFocus(EMPTY_SEARCH_SIDEBAR_FOCUS_FIELD);
			searchSidebarFocusOverridden = true;
		}

		setEmiConfigSidebarFocus(SEARCH_SIDEBAR_FOCUS_FIELD, sidebarType);
		setEmiConfigSidebarFocus(EMPTY_SEARCH_SIDEBAR_FOCUS_FIELD, sidebarType);
	}

	private static void restoreSearchSidebarConfig() {
		if (!searchSidebarFocusOverridden) {
			return;
		}

		if (previousSearchSidebarFocusSetting != null) {
			setEmiConfigSidebarFocus(SEARCH_SIDEBAR_FOCUS_FIELD, previousSearchSidebarFocusSetting);
		}
		if (previousEmptySearchSidebarFocusSetting != null) {
			setEmiConfigSidebarFocus(EMPTY_SEARCH_SIDEBAR_FOCUS_FIELD, previousEmptySearchSidebarFocusSetting);
		}
	}

	@Nullable
	private static Object getEmiConfigSidebarFocus(String fieldName) {
		Class<?> emiConfigClass = findClass(EMI_CONFIG_CLASS);
		if (emiConfigClass == null) {
			return null;
		}
		return getStaticFieldValue(emiConfigClass, fieldName);
	}

	private static void setEmiConfigSidebarFocus(String fieldName, Object value) {
		Class<?> emiConfigClass = findClass(EMI_CONFIG_CLASS);
		if (emiConfigClass == null || value == null) {
			return;
		}
		setStaticFieldValue(emiConfigClass, fieldName, value);
	}

	@Nullable
	private static Object resolveRecipeBookAction(String name) {
		Class<?> recipeBookActionClass = findClass(EMI_RECIPE_BOOK_ACTION_CLASS);
		if (recipeBookActionClass == null || !recipeBookActionClass.isEnum()) {
			return null;
		}

		Object[] constants = recipeBookActionClass.getEnumConstants();
		if (constants == null) {
			return null;
		}
		for (Object constant : constants) {
			if (constant instanceof Enum<?> enumConstant && enumConstant.name().equals(name)) {
				return constant;
			}
		}
		return null;
	}

	private static void sanitizeSidebarPages(Class<?> emiConfigClass, String fieldName, Object fallbackType) {
		Object sidebarPages = getStaticFieldValue(emiConfigClass, fieldName);
		if (sidebarPages == null) {
			return;
		}
		Field pagesField = findField(sidebarPages.getClass(), "pages");
		if (pagesField == null) {
			return;
		}
		try {
			Object pagesObject = pagesField.get(sidebarPages);
			if (!(pagesObject instanceof List<?> pages)) {
				return;
			}
			Class<?> pageEntryClass = null;
			if (!pages.isEmpty() && pages.get(0) != null) {
				pageEntryClass = pages.get(0).getClass();
			}
			@SuppressWarnings("unchecked")
			List<Object> mutablePages = (List<Object>) pages;
			mutablePages.removeIf(ProximityCraftingEmiCraftableFilterController::isCraftablesTypeEntry);
			if (mutablePages.isEmpty()) {
				Object indexPage = newSidebarPageEntry(pageEntryClass, fallbackType);
				if (indexPage != null) {
					mutablePages.add(indexPage);
				}
			}
		} catch (IllegalAccessException ignored) {
		}
	}

	private static void sanitizeSidebarSubpanels(Class<?> emiConfigClass, String fieldName) {
		Object sidebarSubpanels = getStaticFieldValue(emiConfigClass, fieldName);
		if (sidebarSubpanels == null) {
			return;
		}
		Field subpanelsField = findField(sidebarSubpanels.getClass(), "subpanels");
		if (subpanelsField == null) {
			return;
		}
		try {
			Object subpanelsObject = subpanelsField.get(sidebarSubpanels);
			if (!(subpanelsObject instanceof List<?> subpanels)) {
				return;
			}
			@SuppressWarnings("unchecked")
			List<Object> mutableSubpanels = (List<Object>) subpanels;
			mutableSubpanels.removeIf(ProximityCraftingEmiCraftableFilterController::isCraftablesTypeEntry);
		} catch (IllegalAccessException ignored) {
		}
	}

	private static boolean isCraftablesTypeEntry(@Nullable Object entry) {
		if (entry == null) {
			return false;
		}
		Field typeField = findField(entry.getClass(), "type");
		if (typeField == null) {
			return false;
		}
		try {
			Object typeValue = typeField.get(entry);
			return typeValue instanceof Enum<?> enumValue && enumValue.name().equals("CRAFTABLES");
		} catch (IllegalAccessException ignored) {
			return false;
		}
	}

	@Nullable
	private static Object newSidebarPageEntry(@Nullable Class<?> entryClass, Object sidebarType) {
		if (entryClass == null || sidebarType == null) {
			return null;
		}
		try {
			return entryClass.getConstructor(sidebarType.getClass()).newInstance(sidebarType);
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}

	private static List<Object> filterIngredientsByItemId(List<?> ingredients, Set<String> allowedItemIds) {
		if (ingredients.isEmpty() || allowedItemIds.isEmpty()) {
			return List.of();
		}

		Set<String> seenItemIds = new LinkedHashSet<>();
		List<Object> filtered = new ArrayList<>();
		for (Object ingredient : ingredients) {
			String itemId = resolveItemIdFromIngredient(ingredient);
			if (itemId == null || !allowedItemIds.contains(itemId) || !seenItemIds.add(itemId)) {
				continue;
			}
			filtered.add(ingredient);
		}
		return filtered;
	}

	@Nullable
	private static String resolveItemIdFromIngredient(@Nullable Object ingredient) {
		if (ingredient == null) {
			return null;
		}

		Object emiStacksObject = invoke(ingredient, "getEmiStacks", 0);
		if (!(emiStacksObject instanceof List<?> emiStacks) || emiStacks.isEmpty()) {
			return null;
		}

		Object firstStack = emiStacks.get(0);
		if (firstStack == null) {
			return null;
		}

		Object itemStackObject = invoke(firstStack, "getItemStack", 0);
		if (!(itemStackObject instanceof ItemStack itemStack) || itemStack.isEmpty()) {
			return null;
		}

		ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
		return itemId == null ? null : itemId.toString();
	}

	@Nullable
	private static Set<String> getCraftableOutputItemIdsIfReady(
			ProximityCraftingMenu menu,
			long availabilitySignature,
			String reason,
			boolean startComputationIfStale
	) {
		if (menu.getLevel() == null) {
			return Set.of();
		}
		if (availabilitySignature == cachedAvailabilitySignature && cachedAvailabilityContainerId == menu.containerId) {
			return cachedCraftableOutputItemIds;
		}

		if (!startComputationIfStale) {
			return null;
		}

		List<AvailableIngredientStack> availableStacks = buildAvailableItemPool(menu);
		if (availableStacks.isEmpty()) {
			cachedAvailabilitySignature = availabilitySignature;
			cachedAvailabilityContainerId = menu.containerId;
			cachedCraftableOutputItemIds = Set.of();
			cachedCraftableRecipeIdsByOutputKey.clear();
			return cachedCraftableOutputItemIds;
		}

		startCraftableComputation(menu, availableStacks, availabilitySignature, reason);
		return null;
	}

	private static void startCraftableComputation(
			ProximityCraftingMenu menu,
			List<AvailableIngredientStack> availableStacks,
			long availabilitySignature,
			String reason
	) {
		CraftableComputationState existing = craftableComputation;
		if (existing != null
				&& existing.containerId == menu.containerId
				&& existing.availabilitySignature == availabilitySignature) {
			return;
		}
		if (existing != null && existing.containerId == menu.containerId && isDebugLoggingEnabled()) {
			long nowMs = System.currentTimeMillis();
			if ((nowMs - lastComputeRestartLogAtMs) >= CRAFTABLE_COMPUTE_RESTART_LOG_INTERVAL_MS) {
				lastComputeRestartLogAtMs = nowMs;
				int total = existing.recipeCandidates.isEmpty() ? 1 : existing.recipeCandidates.size();
				int processed = Math.min(existing.nextRecipeIndex, total);
				int progressPercent = Math.min(100, (processed * 100) / total);
				ProximityCrafting.LOGGER.info(
						"[PROXC-EMI] compute.restart menu={} reason={} oldSignature={} newSignature={} progress={}/{} ({}%) ageMs={}",
						menu.containerId,
						reason,
						existing.availabilitySignature,
						availabilitySignature,
						processed,
						total,
						progressPercent,
						nowMs - existing.startedAtMs
				);
			}
		}
		Level level = menu.getLevel();
		if (level == null) {
			return;
		}
		List<CraftingRecipe> recipeCandidates = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
		craftableComputation = new CraftableComputationState(
				menu.containerId,
				availabilitySignature,
				level,
				buildStackedContents(availableStacks),
				recipeCandidates
		);
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-EMI] compute.start menu={} reason={} signature={} poolEntries={} recipeCandidates={}",
					menu.containerId,
					reason,
					availabilitySignature,
					availableStacks.size(),
					recipeCandidates.size()
			);
		}
	}

	private static void processCraftableComputationSlice() {
		CraftableComputationState state = craftableComputation;
		if (state == null) {
			return;
		}

		long sliceStartNs = System.nanoTime();
		int processed = 0;
		while (state.nextRecipeIndex < state.recipeCandidates.size()) {
			CraftingRecipe recipe = state.recipeCandidates.get(state.nextRecipeIndex++);
			if (!isEligibleCraftingRecipe(recipe)) {
				continue;
			}
			if (canCraftWithStackedContents(recipe, state.stackedContents)) {
				state.craftableRecipes.add(recipe);
			}
			processed++;
			if (processed >= CRAFTABLE_COMPUTE_MIN_RECIPES_PER_SLICE
					&& (System.nanoTime() - sliceStartNs) >= CRAFTABLE_COMPUTE_BUDGET_NS) {
				break;
			}
		}
		long sliceElapsedNs = System.nanoTime() - sliceStartNs;
		if (isDebugLoggingEnabled() && sliceElapsedNs >= CRAFTABLE_COMPUTE_SLICE_WARN_NS) {
			long nowMs = System.currentTimeMillis();
			if ((nowMs - lastComputeSliceWarnLogAtMs) >= CRAFTABLE_COMPUTE_SLICE_WARN_LOG_INTERVAL_MS) {
				lastComputeSliceWarnLogAtMs = nowMs;
				int total = state.recipeCandidates.isEmpty() ? 1 : state.recipeCandidates.size();
				int progressPercent = Math.min(100, (state.nextRecipeIndex * 100) / total);
				ProximityCrafting.LOGGER.info(
						"[PROXC-EMI] compute.slice.slow menu={} elapsedMs={} processed={} nextIndex={}/{} ({}%) craftableSoFar={} signature={}",
						state.containerId,
						String.format("%.3f", sliceElapsedNs / 1_000_000.0D),
						processed,
						state.nextRecipeIndex,
						total,
						progressPercent,
						state.craftableRecipes.size(),
						state.availabilitySignature
				);
			}
		}

		if (state.nextRecipeIndex < state.recipeCandidates.size()) {
			return;
		}

		Set<String> craftableOutputIds = new LinkedHashSet<>();
		Map<String, ResourceLocation> recipeIdsByOutputKey = new LinkedHashMap<>();
		for (CraftingRecipe recipe : state.craftableRecipes) {
			ItemStack result = recipe.getResultItem(state.level.registryAccess());
			if (result.isEmpty() || !result.isItemEnabled(state.level.enabledFeatures())) {
				continue;
			}
			ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(result.getItem());
			if (itemId != null) {
				craftableOutputIds.add(itemId.toString());
			}
			ResourceLocation recipeId = recipe.getId();
			if (recipeId != null) {
				String outputKey = getStackKey(normalizeForMatching(result));
				recipeIdsByOutputKey.putIfAbsent(outputKey, recipeId);
			}
		}

		cachedAvailabilitySignature = state.availabilitySignature;
		cachedAvailabilityContainerId = state.containerId;
		cachedCraftableOutputItemIds = Set.copyOf(craftableOutputIds);
		stickyCraftableOutputItemIds.clear();
		stickyCraftableOutputItemIds.addAll(craftableOutputIds);
		cachedCraftableRecipeIdsByOutputKey.clear();
		cachedCraftableRecipeIdsByOutputKey.putAll(recipeIdsByOutputKey);
		craftableComputation = null;
		if (enabled && activeContainerId == state.containerId) {
			pendingRefresh = true;
			sourceSnapshotDirty = true;
			nextRefreshAllowedAtMs = 0L;
		}

		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-EMI] compute.done menu={} signature={} craftableRecipes={} craftableItems={}",
					state.containerId,
					state.availabilitySignature,
					state.craftableRecipes.size(),
					craftableOutputIds.size()
			);
		}
	}

	private static void applyStartupCraftableView(ProximityCraftingMenu menu) {
		Object indexType = resolveSidebarType("INDEX");
		if (indexType == null) {
			return;
		}

		applySearchSidebarConfig(indexType);
		List<?> indexIngredients = getAllIndexStacks();
		if (indexIngredients.isEmpty()) {
			indexIngredients = getSidebarStacks(indexType);
		}

		List<Object> filteredIndexStacks = stickyCraftableOutputItemIds.isEmpty()
				? List.of()
				: filterIngredientsByItemId(indexIngredients, stickyCraftableOutputItemIds);
		setIndexFilteredStacks(filteredIndexStacks);
		pinnedIndexStacks = List.copyOf(filteredIndexStacks);
		focusSearchSidebarType(indexType);
		requestSearchRefreshOnly();

		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-EMI] startupView menu={} stickyOutputs={} filteredIndex={} indexCandidates={}",
					menu.containerId,
					stickyCraftableOutputItemIds.size(),
					filteredIndexStacks.size(),
					indexIngredients.size()
			);
		}
	}

	private static boolean canCraftWithStackedContents(CraftingRecipe recipe, StackedContents stackedContents) {
		try {
			return stackedContents.canCraft(recipe, null);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private static StackedContents buildStackedContents(List<AvailableIngredientStack> availableStacks) {
		StackedContents stackedContents = new StackedContents();
		for (AvailableIngredientStack entry : availableStacks) {
			if (entry.count <= 0 || entry.stack.isEmpty()) {
				continue;
			}
			int maxStackSize = Math.max(1, entry.stack.getMaxStackSize());
			int remaining = entry.count;
			while (remaining > 0) {
				int chunkSize = Math.min(remaining, maxStackSize);
				ItemStack stackChunk = entry.stack.copy();
				stackChunk.setCount(chunkSize);
				stackedContents.accountStack(stackChunk);
				remaining -= chunkSize;
			}
		}
		return stackedContents;
	}

	@Nullable
	private static ResourceLocation resolveCraftableRecipeIdForOutput(ProximityCraftingMenu menu, ItemStack outputStack) {
		if (outputStack.isEmpty() || menu.getLevel() == null) {
			return null;
		}

		if (isEnabledFor(menu.containerId)) {
			String outputKey = getStackKey(normalizeForMatching(outputStack));
			return cachedCraftableRecipeIdsByOutputKey.get(outputKey);
		}

		for (CraftingRecipe recipe : computeCraftableRecipes(menu)) {
			ItemStack result = recipe.getResultItem(menu.getLevel().registryAccess());
			if (result.isEmpty() || !result.isItemEnabled(menu.getLevel().enabledFeatures())) {
				continue;
			}
			if (!ItemStack.isSameItemSameTags(result, outputStack)) {
				continue;
			}
			ResourceLocation recipeId = recipe.getId();
			if (recipeId != null) {
				return recipeId;
			}
		}
		return null;
	}

	private static List<CraftingRecipe> computeCraftableRecipes(ProximityCraftingMenu menu) {
		List<AvailableIngredientStack> availableStacks = buildAvailableItemPool(menu);
		if (availableStacks.isEmpty()) {
			return List.of();
		}

		List<CraftingRecipe> craftableRecipes = new ArrayList<>();
		for (CraftingRecipe recipe : menu.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
			if (!isEligibleCraftingRecipe(recipe)) {
				continue;
			}
			if (canCraftWithAvailableStacks(recipe, availableStacks)) {
				craftableRecipes.add(recipe);
			}
		}
		return craftableRecipes;
	}

	private static List<AvailableIngredientStack> buildAvailableItemPool(ProximityCraftingMenu menu) {
		Map<String, AvailableIngredientStack> pooledStacks = new LinkedHashMap<>();
		for (int slot = 0; slot < menu.getCraftSlots().getContainerSize(); slot++) {
			ItemStack stack = menu.getCraftSlots().getItem(slot);
			addAvailableStack(pooledStacks, stack, stack.getCount());
		}

		if (menu.isIncludePlayerInventory()) {
			Inventory inventory = menu.getPlayer().getInventory();
			for (ItemStack stack : inventory.items) {
				addAvailableStack(pooledStacks, stack, stack.getCount());
			}
			for (ItemStack stack : inventory.armor) {
				addAvailableStack(pooledStacks, stack, stack.getCount());
			}
			for (ItemStack stack : inventory.offhand) {
				addAvailableStack(pooledStacks, stack, stack.getCount());
			}
		}

		for (ProximityCraftingMenu.RecipeBookSourceEntry sourceEntry : menu.getClientRecipeBookSupplementalSources()) {
			addAvailableStack(pooledStacks, sourceEntry.stack(), sourceEntry.count());
		}
		return new ArrayList<>(pooledStacks.values());
	}

	private static void addAvailableStack(Map<String, AvailableIngredientStack> pooledStacks, ItemStack stack, int count) {
		if (stack == null || stack.isEmpty() || count <= 0) {
			return;
		}

		ItemStack normalized = normalizeForMatching(stack);
		String key = getStackKey(normalized);
		pooledStacks.compute(key, (ignored, existing) -> {
			if (existing == null) {
				return new AvailableIngredientStack(normalized, count);
			}
			existing.count += count;
			return existing;
		});
	}

	private static boolean canCraftWithAvailableStacks(CraftingRecipe recipe, List<AvailableIngredientStack> availableStacks) {
		List<Ingredient> ingredients = recipe.getIngredients();
		if (ingredients == null || ingredients.isEmpty()) {
			return false;
		}

		int[] remaining = new int[availableStacks.size()];
		for (int i = 0; i < availableStacks.size(); i++) {
			remaining[i] = availableStacks.get(i).count;
		}
		return matchIngredientRecursive(ingredients, 0, availableStacks, remaining);
	}

	private static boolean matchIngredientRecursive(
			List<Ingredient> ingredients,
			int ingredientIndex,
			List<AvailableIngredientStack> availableStacks,
			int[] remaining
	) {
		if (ingredientIndex >= ingredients.size()) {
			return true;
		}

		Ingredient ingredient = ingredients.get(ingredientIndex);
		if (ingredient == null || ingredient.isEmpty()) {
			return matchIngredientRecursive(ingredients, ingredientIndex + 1, availableStacks, remaining);
		}

		for (int i = 0; i < availableStacks.size(); i++) {
			if (remaining[i] <= 0) {
				continue;
			}
			ItemStack candidate = availableStacks.get(i).stack;
			if (!ingredient.test(candidate)) {
				continue;
			}
			remaining[i]--;
			if (matchIngredientRecursive(ingredients, ingredientIndex + 1, availableStacks, remaining)) {
				return true;
			}
			remaining[i]++;
		}
		return false;
	}

	private static boolean isEligibleCraftingRecipe(CraftingRecipe recipe) {
		if (recipe.isSpecial() || recipe.isIncomplete()) {
			return false;
		}

		List<Ingredient> ingredients = recipe.getIngredients();
		if (ingredients == null || ingredients.isEmpty()) {
			return false;
		}

		for (Ingredient ingredient : ingredients) {
			if (ingredient != null && !ingredient.isEmpty() && ingredient.getItems().length > 0) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static ResourceLocation resolveRecipeIdFromInteraction(Object interaction) {
		Object recipeContext = invoke(interaction, "getRecipeContext", 0);
		if (recipeContext == null) {
			return null;
		}
		Object recipeIdObject = invoke(recipeContext, "getId", 0);
		if (recipeIdObject == null) {
			return null;
		}
		return ResourceLocation.tryParse(recipeIdObject.toString());
	}

	private static ItemStack resolveOutputStackFromInteraction(Object interaction) {
		Object stackIngredient = invoke(interaction, "getStack", 0);
		if (stackIngredient == null) {
			return ItemStack.EMPTY;
		}
		Object emiStacksObject = invoke(stackIngredient, "getEmiStacks", 0);
		if (!(emiStacksObject instanceof List<?> emiStacks) || emiStacks.isEmpty()) {
			return ItemStack.EMPTY;
		}

		Object firstStack = emiStacks.get(0);
		if (firstStack == null) {
			return ItemStack.EMPTY;
		}

		Object itemStackObject = invoke(firstStack, "getItemStack", 0);
		if (itemStackObject instanceof ItemStack itemStack && !itemStack.isEmpty()) {
			return normalizeForMatching(itemStack);
		}
		return ItemStack.EMPTY;
	}

	private static ItemStack normalizeForMatching(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.setCount(1);
		return copy;
	}

	private static String getStackKey(ItemStack stack) {
		CompoundTag serialized = new CompoundTag();
		stack.save(serialized);
		serialized.remove("Count");
		return serialized.toString();
	}

	@Nullable
	private static Object getCurrentSearchSidebarType() {
		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass == null) {
			return null;
		}

		Object searchPanel = invokeStatic(screenManagerClass, "getSearchPanel", 0);
		if (searchPanel == null) {
			return null;
		}

		return invoke(searchPanel, "getType", 0);
	}

	@Nullable
	private static List<?> getCurrentIndexFilteredStacks() {
		Class<?> stackListClass = findClass(EMI_STACK_LIST_CLASS);
		if (stackListClass == null) {
			return null;
		}

		Object value = getStaticFieldValue(stackListClass, "filteredStacks");
		if (value instanceof List<?> list) {
			return list;
		}
		return null;
	}

	private static List<?> getAllIndexStacks() {
		Class<?> stackListClass = findClass(EMI_STACK_LIST_CLASS);
		if (stackListClass == null) {
			return List.of();
		}
		Object value = getStaticFieldValue(stackListClass, "stacks");
		if (value instanceof List<?> list) {
			return list;
		}
		return List.of();
	}

	private static void setIndexFilteredStacks(List<?> stacks) {
		Class<?> stackListClass = findClass(EMI_STACK_LIST_CLASS);
		if (stackListClass == null) {
			return;
		}
		setStaticFieldValue(stackListClass, "filteredStacks", List.copyOf(stacks));
	}

	private static void setCraftables(List<?> craftables) {
		Class<?> sidebarsClass = findClass(EMI_SIDEBARS_CLASS);
		if (sidebarsClass == null) {
			return;
		}
		setStaticFieldValue(sidebarsClass, "craftables", List.copyOf(craftables));
	}

	private static void focusSearchSidebarType(Object sidebarType) {
		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass == null || sidebarType == null) {
			return;
		}
		invokeStatic(screenManagerClass, "focusSearchSidebarType", 1, sidebarType);
	}

	@Nullable
	private static Object resolveSidebarType(String name) {
		Class<?> sidebarTypeClass = findClass(EMI_SIDEBAR_TYPE_CLASS);
		if (sidebarTypeClass == null || !sidebarTypeClass.isEnum()) {
			return null;
		}

		Object[] constants = sidebarTypeClass.getEnumConstants();
		if (constants == null) {
			return null;
		}
		for (Object constant : constants) {
			if (constant instanceof Enum<?> enumConstant && enumConstant.name().equals(name)) {
				return constant;
			}
		}
		return null;
	}

	private static List<?> getSidebarStacks(Object sidebarType) {
		Class<?> sidebarsClass = findClass(EMI_SIDEBARS_CLASS);
		if (sidebarsClass == null || sidebarType == null) {
			return List.of();
		}

		Object value = invokeStatic(sidebarsClass, "getStacks", 1, sidebarType);
		if (value instanceof List<?> list) {
			return list;
		}
		return List.of();
	}

	@Nullable
	private static Class<?> findClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException ignored) {
			return null;
		}
	}

	@Nullable
	private static Object invokeStatic(Class<?> ownerClass, String methodName, int parameterCount, Object... args) {
		Method method = findMethod(ownerClass, methodName, parameterCount);
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(null, args);
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}

	@Nullable
	private static Object invoke(Object target, String methodName, int parameterCount, Object... args) {
		Method method = findMethod(target.getClass(), methodName, parameterCount);
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(target, args);
		} catch (ReflectiveOperationException ignored) {
			return null;
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
	private static Object getStaticFieldValue(Class<?> ownerClass, String fieldName) {
		Field field = findField(ownerClass, fieldName);
		if (field == null) {
			return null;
		}
		try {
			return field.get(null);
		} catch (IllegalAccessException ignored) {
			return null;
		}
	}

	private static void setStaticFieldValue(Class<?> ownerClass, String fieldName, Object value) {
		Field field = findField(ownerClass, fieldName);
		if (field == null) {
			return;
		}
		try {
			field.set(null, value);
		} catch (IllegalAccessException ignored) {
		}
	}

	@Nullable
	private static Integer readIntProperty(Object owner, String getterName, String fieldName) {
		Object getterValue = invoke(owner, getterName, 0);
		if (getterValue instanceof Integer integer) {
			return integer;
		}

		Field field = findField(owner.getClass(), fieldName);
		if (field == null) {
			return null;
		}
		try {
			Object fieldValue = field.get(owner);
			return fieldValue instanceof Integer integer ? integer : null;
		} catch (IllegalAccessException ignored) {
			return null;
		}
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static final class AvailableIngredientStack {
		private final ItemStack stack;
		private int count;

		private AvailableIngredientStack(ItemStack stack, int count) {
			this.stack = stack;
			this.count = count;
		}
	}

	private static final class CraftableComputationState {
		private final int containerId;
		private final long availabilitySignature;
		private final long startedAtMs;
		private final Level level;
		private final StackedContents stackedContents;
		private final List<CraftingRecipe> recipeCandidates;
		private final List<CraftingRecipe> craftableRecipes = new ArrayList<>();
		private int nextRecipeIndex = 0;

		private CraftableComputationState(
				int containerId,
				long availabilitySignature,
				Level level,
				StackedContents stackedContents,
				List<CraftingRecipe> recipeCandidates
		) {
			this.containerId = containerId;
			this.availabilitySignature = availabilitySignature;
			this.startedAtMs = System.currentTimeMillis();
			this.level = level;
			this.stackedContents = stackedContents;
			this.recipeCandidates = recipeCandidates;
		}
	}
}


