package dev.maeiro.nearbycrafting.client.compat.emi;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
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
 * EMI craftable-only filter controller using reflection so Nearby Crafting remains runtime-safe without EMI.
 */
public final class NearbyCraftingEmiCraftableFilterController {
	private static final String EMI_SCREEN_MANAGER_CLASS = "dev.emi.emi.screen.EmiScreenManager";
	private static final String EMI_SIDEBARS_CLASS = "dev.emi.emi.runtime.EmiSidebars";
	private static final String EMI_SEARCH_CLASS = "dev.emi.emi.search.EmiSearch";
	private static final String EMI_SIDEBAR_TYPE_CLASS = "dev.emi.emi.config.SidebarType";
	private static final long REFRESH_DEBOUNCE_MS = 75L;

	private static boolean enabled;
	private static boolean transitionActive;
	private static int activeContainerId = -1;
	private static long lastRefreshAtMs = 0L;

	@Nullable
	private static Object previousSearchSidebarType;
	@Nullable
	private static List<?> previousCraftables;

	private NearbyCraftingEmiCraftableFilterController() {
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

	public static void setEnabled(NearbyCraftingMenu menu, boolean shouldEnable) {
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
			previousCraftables = null;
		}

		enabled = true;
		activeContainerId = menu.containerId;
		refresh(menu);
	}

	public static void refreshIfEnabled(NearbyCraftingMenu menu) {
		if (!isEnabledFor(menu.containerId) || !isRuntimeAvailable()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastRefreshAtMs < REFRESH_DEBOUNCE_MS) {
			return;
		}
		lastRefreshAtMs = now;
		refresh(menu);
	}

	public static void handleMenuClosed(int containerId) {
		if (isEnabledFor(containerId)) {
			disableAndRestore();
		}
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

	private static void refresh(NearbyCraftingMenu menu) {
		if (!isRuntimeAvailable()) {
			return;
		}

		transitionActive = true;
		try {
			Object indexType = resolveSidebarType("INDEX");
			Object craftablesType = resolveSidebarType("CRAFTABLES");
			if (indexType == null || craftablesType == null) {
				return;
			}

			if (previousSearchSidebarType == null) {
				previousSearchSidebarType = getCurrentSearchSidebarType();
			}
			if (previousCraftables == null) {
				List<?> currentCraftables = getCurrentCraftables();
				previousCraftables = currentCraftables == null ? List.of() : List.copyOf(currentCraftables);
			}

			Set<String> craftableOutputIds = computeCraftableOutputItemIds(menu);
			List<?> indexIngredients = getSidebarStacks(indexType);
			List<Object> filteredCraftables = filterIngredientsByItemId(indexIngredients, craftableOutputIds);

			setCraftables(filteredCraftables);
			focusSearchSidebarType(craftablesType);
			requestSearchRefresh(indexType, craftablesType);

			if (isDebugLoggingEnabled()) {
				NearbyCrafting.LOGGER.info(
						"[NC-EMI] refresh menu={} indexCandidates={} craftableOutputs={} filteredIngredients={}",
						menu.containerId,
						indexIngredients.size(),
						craftableOutputIds.size(),
						filteredCraftables.size()
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
			Object craftablesType = resolveSidebarType("CRAFTABLES");

			if (previousCraftables != null) {
				setCraftables(previousCraftables);
			}
			if (previousSearchSidebarType != null) {
				focusSearchSidebarType(previousSearchSidebarType);
			}
			requestSearchRefresh(indexType, craftablesType);

			if (isDebugLoggingEnabled()) {
				NearbyCrafting.LOGGER.info("[NC-EMI] disableAndRestore restoredCraftables={}", previousCraftables == null ? 0 : previousCraftables.size());
			}
		} finally {
			enabled = false;
			activeContainerId = -1;
			previousSearchSidebarType = null;
			previousCraftables = null;
			transitionActive = false;
		}
	}

	private static void requestSearchRefresh(@Nullable Object indexType, @Nullable Object craftablesType) {
		Class<?> screenManagerClass = findClass(EMI_SCREEN_MANAGER_CLASS);
		if (screenManagerClass != null) {
			if (indexType != null) {
				invokeStatic(screenManagerClass, "repopulatePanels", 1, indexType);
			}
			if (craftablesType != null) {
				invokeStatic(screenManagerClass, "repopulatePanels", 1, craftablesType);
			}
			invokeStatic(screenManagerClass, "recalculate", 0);
		}

		Class<?> searchClass = findClass(EMI_SEARCH_CLASS);
		if (searchClass != null) {
			invokeStatic(searchClass, "update", 0);
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

	private static Set<String> computeCraftableOutputItemIds(NearbyCraftingMenu menu) {
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

	private static List<CraftingRecipe> computeCraftableRecipes(NearbyCraftingMenu menu) {
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

	private static List<AvailableIngredientStack> buildAvailableItemPool(NearbyCraftingMenu menu) {
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

		for (NearbyCraftingMenu.RecipeBookSourceEntry sourceEntry : menu.getClientRecipeBookSupplementalSources()) {
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
	private static List<?> getCurrentCraftables() {
		Class<?> sidebarsClass = findClass(EMI_SIDEBARS_CLASS);
		if (sidebarsClass == null) {
			return null;
		}

		Object value = getStaticFieldValue(sidebarsClass, "craftables");
		if (value instanceof List<?> list) {
			return list;
		}
		return null;
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

	@SuppressWarnings("unchecked")
	@Nullable
	private static Object resolveSidebarType(String name) {
		Class<?> sidebarTypeClass = findClass(EMI_SIDEBAR_TYPE_CLASS);
		if (sidebarTypeClass == null || !sidebarTypeClass.isEnum()) {
			return null;
		}

		try {
			return Enum.valueOf((Class<? extends Enum>) sidebarTypeClass.asSubclass(Enum.class), name);
		} catch (IllegalArgumentException exception) {
			return null;
		}
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
			return NearbyCraftingConfig.SERVER.debugLogging.get();
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
