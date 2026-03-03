package dev.maeiro.proximitycrafting.client.compat.emi;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
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
	private static final String SEARCH_SIDEBAR_FOCUS_FIELD = "searchSidebarFocus";
	private static final String EMPTY_SEARCH_SIDEBAR_FOCUS_FIELD = "emptySearchSidebarFocus";

	private static boolean enabled;
	private static boolean transitionActive;
	private static int activeContainerId = -1;
	private static long lastRefreshAtMs = 0L;
	private static boolean hasAppliedCraftables = false;
	private static final Set<String> lastAppliedCraftableOutputIds = new LinkedHashSet<>();
	private static List<?> pinnedIndexStacks = List.of();
	private static long lastRuntimeLogAtMs = 0L;
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
		return transitionActive;
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
			pinnedIndexStacks = List.of();
		}

		enabled = true;
		activeContainerId = menu.containerId;
		lastRefreshAtMs = 0L;
		refresh(menu);
	}

	public static void refreshIfEnabled(ProximityCraftingMenu menu) {
		if (!isEnabledFor(menu.containerId) || !isRuntimeAvailable()) {
			return;
		}
		if (transitionActive) {
			logRuntimeState(menu, "refreshIfEnabled:skip_transition");
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastRefreshAtMs < REFRESH_DEBOUNCE_MS) {
			logRuntimeState(menu, "refreshIfEnabled:skip_debounce");
			return;
		}
		lastRefreshAtMs = now;
		logRuntimeState(menu, "refreshIfEnabled:run");
		refresh(menu);
	}

	public static void handleMenuClosed(int containerId) {
		if (isEnabledFor(containerId)) {
			disableAndRestore();
		}
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
		ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(recipeId, craftAll));
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[NC-EMI] DirectFill menu={} recipeId={} craftAll={} mouse=({}, {})",
					menu.containerId,
					recipeId,
					craftAll,
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

			Set<String> craftableOutputIds = computeCraftableOutputItemIds(menu);
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
			);
			if (!changed) {
				updateSearchSidebarOnly();
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[NC-EMI] refresh lightweight (unchanged index-filter set) menu={} craftableOutputs={} filteredIngredients={}",
							menu.containerId,
							craftableOutputIds.size(),
							filteredIndexStacks.size()
					);
				}
				return;
			}
			requestIndexRefresh(indexType);
			focusSearchSidebarType(indexType);
			hasAppliedCraftables = true;
			lastAppliedCraftableOutputIds.clear();
			lastAppliedCraftableOutputIds.addAll(craftableOutputIds);

			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[NC-EMI] refresh menu={} indexCandidates={} craftableOutputs={} filteredIndex={}",
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
			Object indexType = resolveSidebarType("INDEX");
			if (previousIndexFilteredStacks != null) {
				setIndexFilteredStacks(previousIndexFilteredStacks);
			}
			if (previousSearchSidebarType != null) {
				focusSearchSidebarType(previousSearchSidebarType);
			}
			restoreSearchSidebarConfig();
			requestIndexRefresh(indexType);

			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-EMI] disableAndRestore restoredIndexFiltered={}", previousIndexFilteredStacks == null ? 0 : previousIndexFilteredStacks.size());
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
			transitionActive = false;
		}
	}

	private static void updateSearchSidebarOnly() {
		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass != null) {
			invokeStatic(screenManagerClass, "updateSearchSidebar", 0);
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
		long now = System.currentTimeMillis();
		if (now - lastRuntimeLogAtMs < 300L) {
			return;
		}
		lastRuntimeLogAtMs = now;

		Object currentType = getCurrentSearchSidebarType();
		String currentTypeName = currentType instanceof Enum<?> e ? e.name() : String.valueOf(currentType);
		ProximityCrafting.LOGGER.info(
				"[NC-EMI-RUNTIME] stage={} menu={} enabled={} transition={} currentSidebar={} pinned={} applied={} refreshDebounceMs={}",
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

	private static Set<String> computeCraftableOutputItemIds(ProximityCraftingMenu menu) {
		Set<String> itemIds = new LinkedHashSet<>();
		for (CraftingRecipe recipe : computeCraftableRecipes(menu)) {
			ItemStack result = recipe.getResultItem(menu.getLevel().registryAccess());
			if (result.isEmpty() || !result.isItemEnabled(menu.getLevel().enabledFeatures())) {
				continue;
			}

			ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(result.getItem());
			if (itemId != null) {
				itemIds.add(itemId.toString());
			}
		}
		return itemIds;
	}

	@Nullable
	private static ResourceLocation resolveCraftableRecipeIdForOutput(ProximityCraftingMenu menu, ItemStack outputStack) {
		if (outputStack.isEmpty() || menu.getLevel() == null) {
			return null;
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
}


