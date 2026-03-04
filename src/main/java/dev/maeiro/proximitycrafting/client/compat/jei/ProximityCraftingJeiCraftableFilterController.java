package dev.maeiro.proximitycrafting.client.compat.jei;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JEI integration controller that avoids hard JEI references so the mod remains safe when JEI is absent.
 */
public final class ProximityCraftingJeiCraftableFilterController {
	private static final String VANILLA_TYPES_CLASS = "mezz.jei.api.constants.VanillaTypes";
	private static final String VANILLA_ITEM_STACK_FIELD = "ITEM_STACK";

	@Nullable
	private static Object jeiRuntime;
	@Nullable
	private static Object itemStackIngredientType;

	private static final Map<String, ItemStack> universeStacksByKey = new LinkedHashMap<>();
	private static final Map<String, String> universeItemIdByKey = new LinkedHashMap<>();
	private static final Set<String> removedKeys = new LinkedHashSet<>();
	private static final Deque<String> pendingAddKeys = new ArrayDeque<>();
	private static final Deque<String> pendingRemoveKeys = new ArrayDeque<>();
	private static final Map<Object, List<Object>> removedNonItemIngredientsByType = new IdentityHashMap<>();
	private static final long CLICK_PROBE_TIMEOUT_MS = 4000L;
	private static final long REFRESH_DEBOUNCE_MS = 50L;  // Debounce threshold for rapid refresh calls
	private static final int REMOVE_MUTATION_CHUNK_SIZE = 180;
	private static final int ADD_MUTATION_CHUNK_SIZE = 64;
	private static final long EMPTY_SIGNATURE = Long.MIN_VALUE;
	@Nullable
	private static ClickProbe lastClickProbe;
	private static long lastRefreshTime = 0L;
	private static boolean pendingIngredientListRebuild = false;  // Flag to defer overlay rebuild to next tick
	private static boolean pendingStateReset = false;
	private static long cachedAvailabilitySignature = EMPTY_SIGNATURE;
	private static int cachedAvailabilityContainerId = -1;
	private static List<CraftingRecipe> cachedCraftableRecipes = List.of();
	private static Set<String> cachedCraftableOutputItemIds = Set.of();

	private static boolean enabled;
	private static int activeContainerId = -1;

	private ProximityCraftingJeiCraftableFilterController() {
	}

	/** Called every frame to process deferred operations */
	public static void processDeferred() {
		boolean mutated = false;
		mutated |= processMutationChunk("addIngredientsAtRuntime", pendingAddKeys, true, ADD_MUTATION_CHUNK_SIZE);
		mutated |= processMutationChunk("removeIngredientsAtRuntime", pendingRemoveKeys, false, REMOVE_MUTATION_CHUNK_SIZE);

		if (mutated && isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[NC-JEI] Deferred mutation progress addPending={} removePending={}",
					pendingAddKeys.size(),
					pendingRemoveKeys.size()
			);
		}

		if (pendingIngredientListRebuild && pendingAddKeys.isEmpty() && pendingRemoveKeys.isEmpty()) {
			pendingIngredientListRebuild = false;
			forceIngredientListOverlayRebuild();
		}

		if (pendingStateReset && pendingAddKeys.isEmpty() && pendingRemoveKeys.isEmpty()) {
			pendingStateReset = false;
			removedKeys.clear();
			universeStacksByKey.clear();
			universeItemIdByKey.clear();
			removedNonItemIngredientsByType.clear();
		}
	}

	public static void onRuntimeAvailable(Object runtime) {
		jeiRuntime = runtime;
		itemStackIngredientType = resolveItemStackIngredientType();
		disableAndRestore();
		invalidateCraftableCache();
	}

	public static void onRuntimeUnavailable() {
		disableAndRestore();
		jeiRuntime = null;
		itemStackIngredientType = null;
		invalidateCraftableCache();
	}

	public static boolean isRuntimeAvailable() {
		return jeiRuntime != null && itemStackIngredientType != null;
	}

	public static boolean isEnabledFor(int containerId) {
		return enabled && activeContainerId == containerId;
	}

	public static boolean isTransitionBlockingInput() {
		return !pendingAddKeys.isEmpty()
				|| !pendingRemoveKeys.isEmpty()
				|| pendingIngredientListRebuild
				|| pendingStateReset;
	}

	public static void setEnabled(ProximityCraftingMenu menu, boolean shouldEnable) {
		if (!isRuntimeAvailable()) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-JEI] Ignoring setEnabled(menu={}, shouldEnable={}) because runtime is unavailable", menu.containerId, shouldEnable);
			}
			return;
		}

		if (!shouldEnable) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-JEI] Disabling craftable toggle for menu={}", menu.containerId);
			}
			disableAndRestore();
			return;
		}

		if (!isEnabledFor(menu.containerId)) {
			// Only perform restore/reset when switching away from an actively filtered menu.
			if (enabled) {
				disableAndRestore();
			} else {
				// Fresh enable path: keep queues/state clean without scheduling a reset cycle.
				// Non-item tracking must also reset here, otherwise fast close/reopen can leave
				// stale "already hidden" markers and liquids may show up in craftable-only mode.
				pendingAddKeys.clear();
				pendingRemoveKeys.clear();
				pendingStateReset = false;
				removedNonItemIngredientsByType.clear();
			}
			invalidateCraftableCache();
		}

		activeContainerId = menu.containerId;
		enabled = true;
		pendingStateReset = false;
		ensureUniverseLoaded();
		refresh(menu);
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info("[NC-JEI] Enabled craftable toggle for menu={}", menu.containerId);
		}
	}

	public static void refreshIfEnabled(ProximityCraftingMenu menu) {
		if (!isEnabledFor(menu.containerId)) {
			return;
		}
		if (isTransitionBlockingInput()) {
			return;
		}
		
		// Debounce: skip rapid consecutive refresh calls
		long now = System.currentTimeMillis();
		if (now - lastRefreshTime < REFRESH_DEBOUNCE_MS) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-JEI] Refresh debounced ({}ms since last)", now - lastRefreshTime);
			}
			return;
		}
		lastRefreshTime = now;
		
		refresh(menu);
	}

	public static void handleMenuClosed(int containerId) {
		if (isEnabledFor(containerId)) {
			disableAndRestore();
		}
	}

	public static boolean handleIngredientClick(ProximityCraftingMenu menu, int mouseButton) {
		if (!isEnabledFor(menu.containerId) || !isRuntimeAvailable()) {
			return false;
		}
		if (mouseButton != 0 && mouseButton != 1) {
			return false;
		}

		Object runtime = jeiRuntime;
		if (runtime == null) {
			return false;
		}

		try {
			HoveredTypedIngredient hoveredTypedIngredient = getHoveredTypedIngredient(runtime);
			if (hoveredTypedIngredient == null) {
				return false;
			}
			Object typedIngredient = hoveredTypedIngredient.typedIngredient;

			if (mouseButton == 0) {
				ItemStack clickedItem = getItemStackFromTypedIngredient(typedIngredient);
				ResourceLocation matchedRecipeId = resolveCraftableRecipeIdForOutput(menu, clickedItem);
				if (matchedRecipeId != null) {
					boolean craftAll = Screen.hasShiftDown();
					ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(matchedRecipeId, craftAll));
					if (isDebugLoggingEnabled()) {
						ProximityCrafting.LOGGER.info(
								"[NC-JEI] DirectFill menu={} recipeId={} craftAll={} ingredientSource={} item={}",
								menu.containerId,
								matchedRecipeId,
								craftAll,
								hoveredTypedIngredient.source,
								clickedItem.isEmpty() ? "empty" : clickedItem.getDescriptionId()
						);
					}
					return true;
				}
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[NC-JEI] DirectFill skipped menu={} reason=no_matching_craftable_recipe ingredientSource={} ingredient={}",
							menu.containerId,
							hoveredTypedIngredient.source,
							readIngredientInfoForLog(typedIngredient)
					);
				}
			}

			return openRecipesGuiFallback(menu, mouseButton, hoveredTypedIngredient, typedIngredient, runtime);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Failed to handle JEI ingredient click fallback", exception);
			return false;
		}
	}

	@Nullable
	public static ResourceLocation resolveHoveredRecipeId(ProximityCraftingMenu menu) {
		if (!isRuntimeAvailable()) {
			return null;
		}
		Object runtime = jeiRuntime;
		if (runtime == null) {
			return null;
		}

		try {
			HoveredTypedIngredient hoveredTypedIngredient = getHoveredTypedIngredient(runtime);
			if (hoveredTypedIngredient == null) {
				return null;
			}
			ItemStack hoveredItem = getItemStackFromTypedIngredient(hoveredTypedIngredient.typedIngredient);
			if (hoveredItem.isEmpty()) {
				return null;
			}
			return resolveCraftableRecipeIdForOutput(menu, hoveredItem);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static boolean openRecipesGuiFallback(
			ProximityCraftingMenu menu,
			int mouseButton,
			HoveredTypedIngredient hoveredTypedIngredient,
			Object typedIngredient,
			Object runtime
	) throws ReflectiveOperationException {
		Object roleEnum = resolveRecipeIngredientRole(mouseButton == 0 ? "OUTPUT" : "INPUT");
		if (roleEnum == null) {
			return false;
		}

		Object jeiHelpers = invokeNoArg(runtime, "getJeiHelpers");
		if (jeiHelpers == null) {
			return false;
		}

		Object focusFactory = invokeNoArg(jeiHelpers, "getFocusFactory");
		if (focusFactory == null) {
			return false;
		}

		Object focus = createFocusForTypedIngredient(focusFactory, roleEnum, typedIngredient);
		if (focus == null) {
			return false;
		}

		Object recipesGui = invokeNoArg(runtime, "getRecipesGui");
		if (recipesGui == null) {
			return false;
		}

		Method showMethod = findMethod(recipesGui.getClass(), "show", 1);
		if (showMethod == null) {
			return false;
		}
		showMethod.invoke(recipesGui, List.of(focus));
		String currentScreen = Minecraft.getInstance().screen == null
				? "null"
				: Minecraft.getInstance().screen.getClass().getName();
		boolean recipesScreenOpened = currentScreen.contains("mezz.jei.gui.recipes.RecipesGui");

		if (isDebugLoggingEnabled()) {
			String ingredientInfo = readIngredientInfoForLog(typedIngredient);
			ProximityCrafting.LOGGER.info(
					"[NC-JEI] FallbackOpen menu={} mouseButton={} ingredientSource={} ingredient={} currentScreenAfterCall={} opened={}",
					menu.containerId,
					mouseButton,
					hoveredTypedIngredient.source,
					ingredientInfo,
					currentScreen,
					recipesScreenOpened
			);
		}
		return recipesScreenOpened;
	}

	public static void debugProbeIngredientClick(ProximityCraftingMenu menu, int mouseButton, double mouseX, double mouseY, String phase) {
		if (!isDebugLoggingEnabled()) {
			return;
		}
		Object runtime = jeiRuntime;
		HoveredTypedIngredient hoveredTypedIngredient = null;
		if (runtime != null) {
			try {
				hoveredTypedIngredient = getHoveredTypedIngredient(runtime);
			} catch (RuntimeException exception) {
				ProximityCrafting.LOGGER.warn("Failed to probe JEI ingredient under mouse", exception);
			}
		}

		String ingredientSource = hoveredTypedIngredient == null ? "none" : hoveredTypedIngredient.source;
		String ingredientInfo = hoveredTypedIngredient == null ? "none" : readIngredientInfoForLog(hoveredTypedIngredient.typedIngredient);
		lastClickProbe = new ClickProbe(
				System.currentTimeMillis(),
				menu.containerId,
				mouseButton,
				isEnabledFor(menu.containerId),
				isRuntimeAvailable(),
				ingredientSource,
				ingredientInfo,
				phase
		);

		ProximityCrafting.LOGGER.info(
				"[NC-JEI] ClickProbe phase={} menu={} enabled={} runtimeAvailable={} mouseButton={} mouse=({}, {}) ingredientSource={} ingredient={}",
				phase,
				menu.containerId,
				isEnabledFor(menu.containerId),
				isRuntimeAvailable(),
				mouseButton,
				(int) mouseX,
				(int) mouseY,
				ingredientSource,
				ingredientInfo
		);
	}

	public static void debugProbeScreenInit(Object screen) {
		if (!isDebugLoggingEnabled() || screen == null) {
			return;
		}

		String screenClassName = screen.getClass().getName();
		long now = System.currentTimeMillis();
		ClickProbe clickProbe = lastClickProbe;
		if (clickProbe != null && now - clickProbe.timestampMs > CLICK_PROBE_TIMEOUT_MS) {
			lastClickProbe = null;
			clickProbe = null;
		}

		boolean isRecipesScreen = screenClassName.contains("mezz.jei.gui.recipes");
		if (!isRecipesScreen && clickProbe == null) {
			return;
		}

		if (clickProbe == null) {
			ProximityCrafting.LOGGER.info("[NC-JEI] ScreenInit newScreen={} without recent click probe", screenClassName);
			return;
		}

		ProximityCrafting.LOGGER.info(
				"[NC-JEI] ScreenInit newScreen={} afterClick={}ms probePhase={} probeMenu={} probeEnabled={} probeRuntime={} probeMouseButton={} probeIngredientSource={} probeIngredient={}",
				screenClassName,
				now - clickProbe.timestampMs,
				clickProbe.phase,
				clickProbe.menuId,
				clickProbe.enabledForMenu,
				clickProbe.runtimeAvailable,
				clickProbe.mouseButton,
				clickProbe.ingredientSource,
				clickProbe.ingredientInfo
		);
		if (isRecipesScreen) {
			lastClickProbe = null;
		}
	}

	@Nullable
	public static Rect2i getJeiSearchFieldBounds() {
		if (!isRuntimeAvailable()) {
			return null;
		}

		Object runtime = jeiRuntime;
		if (runtime == null) {
			return null;
		}

		try {
			Object ingredientListOverlay = invokeNoArg(runtime, "getIngredientListOverlay");
			if (ingredientListOverlay == null) {
				return null;
			}

			Object listDisplayed = invokeNoArg(ingredientListOverlay, "isListDisplayed");
			if (!(listDisplayed instanceof Boolean displayed) || !displayed) {
				return null;
			}

			Object searchFieldObject = getFieldValue(ingredientListOverlay, "searchField");
			if (searchFieldObject == null) {
				return null;
			}

			Rect2i widgetBounds = tryExtractRect(searchFieldObject, "backgroundBounds");
			if (widgetBounds != null) {
				return widgetBounds;
			}

			widgetBounds = tryExtractRect(searchFieldObject, "area");
			if (widgetBounds != null) {
				return widgetBounds;
			}

			if (searchFieldObject instanceof EditBox searchField) {
				return new Rect2i(searchField.getX(), searchField.getY(), searchField.getWidth(), searchField.getHeight());
			}

			return null;
		} catch (RuntimeException exception) {
			ProximityCrafting.LOGGER.debug("Unable to read JEI search field bounds reflectively", exception);
			return null;
		}
	}

	private static void refresh(ProximityCraftingMenu menu) {
		if (!isRuntimeAvailable()) {
			return;
		}

		long startTime = System.nanoTime();
		Object ingredientManager = getIngredientManager();
		if (ingredientManager == null) {
			return;
		}

		boolean nonItemMutated = hideNonItemIngredients(ingredientManager);
		if (nonItemMutated) {
			// Hide non-item entries (fluids, etc.) visually as early as possible
			// so the initial craftable-only open does not show a transient flash.
			forceIngredientListOverlayVisualRefresh();
		}

		ensureUniverseLoaded();
		long afterUniverse = System.nanoTime();
		
		Set<String> craftableOutputItemIds = computeCraftableOutputItemIds(menu);
		long afterCraftable = System.nanoTime();

		Set<String> desiredRemovedKeys = new LinkedHashSet<>();
		for (Map.Entry<String, String> entry : universeItemIdByKey.entrySet()) {
			if (!craftableOutputItemIds.contains(entry.getValue())) {
				desiredRemovedKeys.add(entry.getKey());
			}
		}
		
		if (desiredRemovedKeys.equals(removedKeys) && pendingAddKeys.isEmpty() && pendingRemoveKeys.isEmpty()) {
			long noChangeTime = System.nanoTime() - startTime;
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-JEI] Refresh SKIPPED (no changes) in {}ms", noChangeTime / 1_000_000.0);
			}
			if (nonItemMutated) {
				pendingIngredientListRebuild = true;
			}
			return;
		}

		Set<String> toRestore = new LinkedHashSet<>(removedKeys);
		toRestore.removeAll(desiredRemovedKeys);

		Set<String> toHide = new LinkedHashSet<>(desiredRemovedKeys);
		toHide.removeAll(removedKeys);

		pendingAddKeys.clear();
		pendingRemoveKeys.clear();
		pendingAddKeys.addAll(toRestore);
		pendingRemoveKeys.addAll(toHide);
		
		long beforeHide = System.nanoTime();
		long afterHide = System.nanoTime();
		
		long beforeRebuild = System.nanoTime();
		// Rebuild after queued mutations are applied in chunks.
		pendingIngredientListRebuild = true;
		long afterRebuild = System.nanoTime();

		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[NC-JEI] Refresh QUEUED menu={} universe={} craftableItems={} queueHide={} queueRestore={} nonItemMutated={} | Time: universeLoadMs={} craftableMs={} hideMs={} enqueueMs={} totalMs={}",
					menu.containerId,
					universeStacksByKey.size(),
					craftableOutputItemIds.size(),
					toHide.size(),
					toRestore.size(),
					nonItemMutated,
					(afterUniverse - startTime) / 1_000_000.0,
					(afterCraftable - afterUniverse) / 1_000_000.0,
					(afterHide - beforeHide) / 1_000_000.0,
					(afterRebuild - beforeRebuild) / 1_000_000.0,
					(afterRebuild - startTime) / 1_000_000.0
			);
		}
	}

	private static void ensureUniverseLoaded() {
		if (!universeStacksByKey.isEmpty() && !universeItemIdByKey.isEmpty()) {
			return;
		}

		Object ingredientManager = getIngredientManager();
		if (ingredientManager == null) {
			return;
		}

		try {
			Object allItemStacks = invokeNoArg(ingredientManager, "getAllItemStacks");
			if (!(allItemStacks instanceof Collection<?> stacks)) {
				return;
			}

			for (Object stackObject : stacks) {
				if (!(stackObject instanceof ItemStack stack) || stack.isEmpty()) {
					continue;
				}
				String key = getStackKey(stack);
				if (universeStacksByKey.containsKey(key)) {
					continue;
				}

				universeStacksByKey.put(key, normalizeForIngredientList(stack));
				ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
				universeItemIdByKey.put(key, itemId != null ? itemId.toString() : "");
			}

			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[NC-JEI] Loaded JEI item universe with {} stacks", universeStacksByKey.size());
			}
		} catch (RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Failed to read JEI ingredient universe for craftable filtering", exception);
		}
	}

	private static Set<String> computeCraftableOutputItemIds(ProximityCraftingMenu menu) {
		CraftableSnapshot craftableSnapshot = getCraftableSnapshot(menu);
		Set<String> itemIds = new LinkedHashSet<>(craftableSnapshot.outputItemIds());
		List<CraftingRecipe> craftableRecipes = craftableSnapshot.recipes();
		List<String> sample = new ArrayList<>();
		for (CraftingRecipe craftingRecipe : craftableRecipes) {
			if (sample.size() >= 12) {
				break;
			}
			ItemStack result = craftingRecipe.getResultItem(menu.getLevel().registryAccess());
			if (result.isEmpty() || !result.isItemEnabled(menu.getLevel().enabledFeatures())) {
				continue;
			}

			ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(result.getItem());
			if (itemId != null) {
				sample.add(craftingRecipe.getId() + " -> " + itemId);
			}
		}

		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[NC-JEI] Craftable snapshot menu={} includePlayer={} recipeCandidates={} craftableRecipes={} craftableItems={} sample={}",
					menu.containerId,
					menu.isIncludePlayerInventory(),
					menu.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).size(),
					craftableRecipes.size(),
					itemIds.size(),
					sample
			);
		}
		return itemIds;
	}

	@Nullable
	private static ResourceLocation resolveCraftableRecipeIdForOutput(ProximityCraftingMenu menu, ItemStack outputStack) {
		if (outputStack.isEmpty() || menu.getLevel() == null) {
			return null;
		}
		if (!isEnabledFor(menu.containerId)) {
			return resolveAnyRecipeIdForOutput(menu, outputStack);
		}

		for (CraftingRecipe craftingRecipe : getCraftableSnapshot(menu).recipes()) {
			ItemStack result = craftingRecipe.getResultItem(menu.getLevel().registryAccess());
			if (result.isEmpty() || !result.isItemEnabled(menu.getLevel().enabledFeatures())) {
				continue;
			}
			if (!ItemStack.isSameItemSameTags(result, outputStack)) {
				continue;
			}

			ResourceLocation recipeId = craftingRecipe.getId();
			if (recipeId != null) {
				return recipeId;
			}
		}

		// Fallback: allow recipe switching from current grid-loaded ingredients.
		return resolveAnyRecipeIdForOutput(menu, outputStack);
	}

	@Nullable
	private static ResourceLocation resolveAnyRecipeIdForOutput(ProximityCraftingMenu menu, ItemStack outputStack) {
		for (CraftingRecipe craftingRecipe : menu.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
			if (!isEligibleCraftingRecipe(craftingRecipe)) {
				continue;
			}
			ItemStack result = craftingRecipe.getResultItem(menu.getLevel().registryAccess());
			if (result.isEmpty() || !result.isItemEnabled(menu.getLevel().enabledFeatures())) {
				continue;
			}
			if (!ItemStack.isSameItemSameTags(result, outputStack)) {
				continue;
			}
			ResourceLocation recipeId = craftingRecipe.getId();
			if (recipeId != null) {
				return recipeId;
			}
		}

		return null;
	}

	private static CraftableSnapshot getCraftableSnapshot(ProximityCraftingMenu menu) {
		long startTime = System.nanoTime();

		long poolStart = System.nanoTime();
		List<AvailableIngredientStack> availableStacks = buildJeiAvailableItemPool(menu);
		long poolTime = System.nanoTime() - poolStart;
		long availabilitySignature = computeAvailabilitySignature(menu.containerId, availableStacks);

		if (availabilitySignature == cachedAvailabilitySignature && cachedAvailabilityContainerId == menu.containerId) {
			if (isDebugLoggingEnabled()) {
				long totalTime = System.nanoTime() - startTime;
				ProximityCrafting.LOGGER.info(
						"[NC-JEI] computeCraftableRecipes cache-hit in {}ms (pool: {}ms, results: {})",
						totalTime / 1_000_000.0,
						poolTime / 1_000_000.0,
						cachedCraftableRecipes.size()
				);
			}
			return new CraftableSnapshot(cachedCraftableRecipes, cachedCraftableOutputItemIds);
		}

		if (availableStacks.isEmpty()) {
			logMatcherPoolDebug(menu, availableStacks, 0, 0);
			cachedAvailabilitySignature = availabilitySignature;
			cachedAvailabilityContainerId = menu.containerId;
			cachedCraftableRecipes = List.of();
			cachedCraftableOutputItemIds = Set.of();
			return new CraftableSnapshot(cachedCraftableRecipes, cachedCraftableOutputItemIds);
		}

		List<CraftingRecipe> craftableRecipes = new ArrayList<>();
		int candidateRecipes = 0;
		long matchingStart = System.nanoTime();
		for (CraftingRecipe recipe : menu.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
			if (!isEligibleCraftingRecipe(recipe)) {
				continue;
			}
			candidateRecipes++;
			if (!canCraftWithAvailableStacks(recipe, availableStacks)) {
				continue;
			}
			craftableRecipes.add(recipe);
		}
		long matchingTime = System.nanoTime() - matchingStart;

		logMatcherPoolDebug(menu, availableStacks, candidateRecipes, craftableRecipes.size());

		Set<String> craftableOutputIds = new LinkedHashSet<>();
		for (CraftingRecipe recipe : craftableRecipes) {
			ItemStack result = recipe.getResultItem(menu.getLevel().registryAccess());
			if (result.isEmpty() || !result.isItemEnabled(menu.getLevel().enabledFeatures())) {
				continue;
			}
			ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(result.getItem());
			if (itemId != null) {
				craftableOutputIds.add(itemId.toString());
			}
		}

		cachedAvailabilitySignature = availabilitySignature;
		cachedAvailabilityContainerId = menu.containerId;
		cachedCraftableRecipes = List.copyOf(craftableRecipes);
		cachedCraftableOutputItemIds = Set.copyOf(craftableOutputIds);
		
		if (isDebugLoggingEnabled()) {
			long totalTime = System.nanoTime() - startTime;
			ProximityCrafting.LOGGER.info("[NC-JEI] computeCraftableRecipes in {}ms (pool: {}ms, matching: {}ms, results: {})",
					totalTime / 1_000_000.0, poolTime / 1_000_000.0, matchingTime / 1_000_000.0, craftableRecipes.size());
		}

		return new CraftableSnapshot(cachedCraftableRecipes, cachedCraftableOutputItemIds);
	}

	private static long computeAvailabilitySignature(int containerId, List<AvailableIngredientStack> availableStacks) {
		if (availableStacks.isEmpty()) {
			return EMPTY_SIGNATURE + containerId;
		}
		List<String> tokens = new ArrayList<>(availableStacks.size());
		for (AvailableIngredientStack entry : availableStacks) {
			tokens.add(getStackKey(entry.stack) + "#" + entry.count);
		}
		Collections.sort(tokens);
		long hash = 1125899906842597L;
		hash = (31L * hash) + containerId;
		for (String token : tokens) {
			hash = (31L * hash) + token.hashCode();
		}
		return hash;
	}

	private static void invalidateCraftableCache() {
		cachedAvailabilitySignature = EMPTY_SIGNATURE;
		cachedAvailabilityContainerId = -1;
		cachedCraftableRecipes = List.of();
		cachedCraftableOutputItemIds = Set.of();
	}

	private static List<AvailableIngredientStack> buildJeiAvailableItemPool(ProximityCraftingMenu menu) {
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
		ItemStack normalized = normalizeForIngredientList(stack);
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

	private static void logMatcherPoolDebug(
			ProximityCraftingMenu menu,
			List<AvailableIngredientStack> availableStacks,
			int candidateRecipes,
			int craftableRecipes
	) {
		if (!isDebugLoggingEnabled()) {
			return;
		}

		int totalItems = 0;
		int diamondCount = 0;
		int stickCount = 0;
		List<String> sample = new ArrayList<>();
		for (AvailableIngredientStack entry : availableStacks) {
			totalItems += entry.count;
			ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(entry.stack.getItem());
			String itemIdString = itemId == null ? "unknown" : itemId.toString();
			if ("minecraft:diamond".equals(itemIdString)) {
				diamondCount += entry.count;
			} else if ("minecraft:stick".equals(itemIdString)) {
				stickCount += entry.count;
			}

			if (sample.size() < 12) {
				sample.add(itemIdString + " x" + entry.count);
			}
		}

		ProximityCrafting.LOGGER.info(
				"[NC-JEI] MatcherPool menu={} entries={} totalItems={} diamonds={} sticks={} recipeCandidates={} craftableRecipes={} sample={}",
				menu.containerId,
				availableStacks.size(),
				totalItems,
				diamondCount,
				stickCount,
				candidateRecipes,
				craftableRecipes,
				sample
		);
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

	private static List<ItemStack> getStacksForKeys(Collection<String> keys) {
		if (keys.isEmpty()) {
			return List.of();
		}

		List<ItemStack> stacks = new ArrayList<>(keys.size());
		for (String key : keys) {
			ItemStack stack = universeStacksByKey.get(key);
			if (stack != null && !stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}
		return stacks;
	}

	private static void invokeIngredientMutation(Object ingredientManager, String methodName, List<ItemStack> stacks) {
		if (stacks.isEmpty()) {
			return;
		}
		Object ingredientType = itemStackIngredientType;
		if (ingredientType == null) {
			return;
		}

		try {
			Method mutationMethod = findMethod(ingredientManager.getClass(), methodName, 2);
			if (mutationMethod == null) {
				return;
			}
			mutationMethod.invoke(ingredientManager, ingredientType, stacks);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Failed JEI ingredient mutation {} for {} stacks", methodName, stacks.size(), exception);
		}
	}

	@Nullable
	private static Object getIngredientManager() {
		Object runtime = jeiRuntime;
		if (runtime == null) {
			return null;
		}
		try {
			return invokeNoArg(runtime, "getIngredientManager");
		} catch (RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Unable to access JEI ingredient manager", exception);
			return null;
		}
	}

	private static void disableAndRestore() {
		Object ingredientManager = getIngredientManager();
		if (ingredientManager != null) {
			// Cancel pending hides and schedule progressive restore of hidden items.
			pendingRemoveKeys.clear();
			pendingAddKeys.clear();
			pendingAddKeys.addAll(removedKeys);
			restoreNonItemIngredients(ingredientManager);
			// Let JEI refresh naturally while items are restored in chunks; avoid a full forced rebuild spike.
			pendingIngredientListRebuild = false;
		}

		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[NC-JEI] disableAndRestore restoredItems={} restoredNonItemTypes={} universe={} activeMenu={}",
					removedKeys.size(),
					removedNonItemIngredientsByType.size(),
					universeStacksByKey.size(),
					activeContainerId
			);
		}

		pendingStateReset = true;
		invalidateCraftableCache();
		enabled = false;
		activeContainerId = -1;
	}

	private static boolean processMutationChunk(String methodName, Deque<String> keysQueue, boolean addingBack, int chunkSize) {
		if (keysQueue.isEmpty()) {
			return false;
		}

		Object ingredientManager = getIngredientManager();
		if (ingredientManager == null) {
			keysQueue.clear();
			return false;
		}

		List<String> chunkKeys = new ArrayList<>(Math.min(keysQueue.size(), chunkSize));
		while (!keysQueue.isEmpty() && chunkKeys.size() < chunkSize) {
			chunkKeys.add(keysQueue.removeFirst());
		}

		List<ItemStack> stacks = getStacksForKeys(chunkKeys);
		invokeIngredientMutation(ingredientManager, methodName, stacks);

		for (String key : chunkKeys) {
			if (addingBack) {
				removedKeys.remove(key);
			} else {
				removedKeys.add(key);
			}
		}

		return !chunkKeys.isEmpty();
	}

	@Nullable
	private static Object resolveItemStackIngredientType() {
		try {
			Class<?> vanillaTypesClass = Class.forName(VANILLA_TYPES_CLASS);
			Field itemStackField = vanillaTypesClass.getField(VANILLA_ITEM_STACK_FIELD);
			return itemStackField.get(null);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Unable to resolve JEI VanillaTypes.ITEM_STACK reflectively", exception);
			return null;
		}
	}

	private static ItemStack normalizeForIngredientList(ItemStack stack) {
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

	private static Object invokeNoArg(Object target, String methodName) {
		try {
			Method method = findMethod(target.getClass(), methodName, 0);
			if (method == null) {
				throw new IllegalStateException("Method not found: " + methodName);
			}
			return method.invoke(target);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to invoke method " + methodName, exception);
		}
	}

	@Nullable
	private static Rect2i tryExtractRect(Object owner, String fieldName) {
		if (owner == null) {
			return null;
		}

		Object rectObject = getFieldValue(owner, fieldName);
		return toRect(rectObject);
	}

	@Nullable
	private static Rect2i toRect(Object rectObject) {
		if (rectObject == null) {
			return null;
		}

		try {
			Method getX = findMethod(rectObject.getClass(), "getX", 0);
			Method getY = findMethod(rectObject.getClass(), "getY", 0);
			Method getWidth = findMethod(rectObject.getClass(), "getWidth", 0);
			Method getHeight = findMethod(rectObject.getClass(), "getHeight", 0);
			if (getX == null || getY == null || getWidth == null || getHeight == null) {
				return null;
			}

			Object x = getX.invoke(rectObject);
			Object y = getY.invoke(rectObject);
			Object width = getWidth.invoke(rectObject);
			Object height = getHeight.invoke(rectObject);
			if (x instanceof Integer xInt && y instanceof Integer yInt && width instanceof Integer widthInt && height instanceof Integer heightInt) {
				return new Rect2i(xInt, yInt, widthInt, heightInt);
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return null;
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

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	@Nullable
	private static Object resolveRecipeIngredientRole(String enumName) {
		try {
			Class<?> roleClass = Class.forName("mezz.jei.api.recipe.RecipeIngredientRole");
			@SuppressWarnings("unchecked")
			Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) roleClass.asSubclass(Enum.class);
			@SuppressWarnings({"unchecked", "rawtypes"})
			Enum<?> result = Enum.valueOf((Class) enumClass, enumName);
			return result;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Unable to resolve JEI RecipeIngredientRole.{}", enumName, exception);
			return null;
		}
	}

	@Nullable
	private static Object createFocusForTypedIngredient(Object focusFactory, Object role, Object typedIngredient) throws ReflectiveOperationException {
		for (Method method : focusFactory.getClass().getMethods()) {
			if (!method.getName().equals("createFocus") || method.getParameterCount() != 2) {
				continue;
			}
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (!parameterTypes[0].isInstance(role)) {
				continue;
			}
			if (!parameterTypes[1].isInstance(typedIngredient)) {
				continue;
			}

			return method.invoke(focusFactory, role, typedIngredient);
		}
		return null;
	}

	private static boolean hideNonItemIngredients(Object ingredientManager) {
		try {
			Object registeredTypesObj = invokeNoArg(ingredientManager, "getRegisteredIngredientTypes");
			if (!(registeredTypesObj instanceof Collection<?> registeredTypes) || registeredTypes.isEmpty()) {
				return false;
			}

			int hiddenTypesThisRefresh = 0;
			for (Object ingredientType : registeredTypes) {
				if (ingredientType == null || ingredientType == itemStackIngredientType) {
					continue;
				}
				if (removedNonItemIngredientsByType.containsKey(ingredientType)) {
					continue;
				}

				Collection<?> allIngredients = getAllIngredientsForType(ingredientManager, ingredientType);
				if (allIngredients == null || allIngredients.isEmpty()) {
					continue;
				}

				List<Object> hiddenIngredients = new ArrayList<>(allIngredients.size());
				hiddenIngredients.addAll(allIngredients);
				invokeIngredientMutationWithType(ingredientManager, "removeIngredientsAtRuntime", ingredientType, hiddenIngredients);
				removedNonItemIngredientsByType.put(ingredientType, hiddenIngredients);
				hiddenTypesThisRefresh++;
			}

			if (isDebugLoggingEnabled() && hiddenTypesThisRefresh > 0) {
				ProximityCrafting.LOGGER.info("[NC-JEI] Hid non-item ingredient types in JEI: {}", hiddenTypesThisRefresh);
			}
			return hiddenTypesThisRefresh > 0;
		} catch (RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Failed to hide non-item JEI ingredients during craftable filtering", exception);
			return false;
		}
	}

	private static void restoreNonItemIngredients(Object ingredientManager) {
		if (removedNonItemIngredientsByType.isEmpty()) {
			return;
		}

		int restoredTypes = 0;
		for (Map.Entry<Object, List<Object>> entry : removedNonItemIngredientsByType.entrySet()) {
			Object ingredientType = entry.getKey();
			List<Object> removedIngredients = entry.getValue();
			if (ingredientType == null || removedIngredients == null || removedIngredients.isEmpty()) {
				continue;
			}
			invokeIngredientMutationWithType(ingredientManager, "addIngredientsAtRuntime", ingredientType, removedIngredients);
			restoredTypes++;
		}

		if (isDebugLoggingEnabled() && restoredTypes > 0) {
			ProximityCrafting.LOGGER.info("[NC-JEI] Restored non-item ingredient types in JEI: {}", restoredTypes);
		}
	}

	@Nullable
	private static Collection<?> getAllIngredientsForType(Object ingredientManager, Object ingredientType) {
		try {
			Method method = findMethod(ingredientManager.getClass(), "getAllIngredients", 1);
			if (method == null) {
				return null;
			}
			Object value = method.invoke(ingredientManager, ingredientType);
			if (value instanceof Collection<?> collection) {
				return collection;
			}
			return null;
		} catch (ReflectiveOperationException exception) {
			return null;
		}
	}

	private static void invokeIngredientMutationWithType(Object ingredientManager, String methodName, Object ingredientType, Collection<?> ingredients) {
		if (ingredientType == null || ingredients == null || ingredients.isEmpty()) {
			return;
		}

		try {
			Method mutationMethod = findMethod(ingredientManager.getClass(), methodName, 2);
			if (mutationMethod == null) {
				return;
			}
			mutationMethod.invoke(ingredientManager, ingredientType, ingredients);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Failed JEI ingredient mutation {} for {} entries of non-item type", methodName, ingredients.size(), exception);
		}
	}

	private static void forceIngredientListOverlayRebuild() {
		long startTime = System.nanoTime();
		Object runtime = jeiRuntime;
		if (runtime == null) {
			return;
		}

		try {
			boolean filterRebuilt = rebuildIngredientFilter(runtime);
			boolean filterNudged = nudgeIngredientFilterText(runtime);
			boolean overlayUpdated = forceOverlayScreenPropertiesUpdate(runtime);
			
			if (isDebugLoggingEnabled()) {
				long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
				ProximityCrafting.LOGGER.info(
						"[NC-JEI] forceIngredientListOverlayRebuild completed in {}ms (filterRebuilt={} filterNudged={} overlayUpdated={})",
						elapsedMs,
						filterRebuilt,
						filterNudged,
						overlayUpdated
				);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	private static void forceIngredientListOverlayVisualRefresh() {
		Object runtime = jeiRuntime;
		if (runtime == null) {
			return;
		}

		try {
			boolean filterNudged = nudgeIngredientFilterText(runtime);
			boolean overlayUpdated = forceOverlayScreenPropertiesUpdate(runtime);
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[NC-JEI] forceIngredientListOverlayVisualRefresh filterNudged={} overlayUpdated={}",
						filterNudged,
						overlayUpdated
				);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	private static boolean rebuildIngredientFilter(Object runtime) throws ReflectiveOperationException {
		Object ingredientFilter = invokeNoArg(runtime, "getIngredientFilter");
		if (ingredientFilter == null) {
			return false;
		}

		Method rebuildMethod = findMethod(ingredientFilter.getClass(), "rebuildItemFilter", 0);
		if (rebuildMethod != null) {
			rebuildMethod.invoke(ingredientFilter);
			return true;
		}

		Object backingFilter = getFieldValue(ingredientFilter, "ingredientFilter");
		if (backingFilter == null) {
			return false;
		}

		Method backingRebuildMethod = findMethod(backingFilter.getClass(), "rebuildItemFilter", 0);
		if (backingRebuildMethod == null) {
			return false;
		}
		backingRebuildMethod.invoke(backingFilter);
		return true;
	}

	private static boolean nudgeIngredientFilterText(Object runtime) throws ReflectiveOperationException {
		Object ingredientFilter = invokeNoArg(runtime, "getIngredientFilter");
		if (ingredientFilter == null) {
			return false;
		}

		Method getFilterTextMethod = findMethod(ingredientFilter.getClass(), "getFilterText", 0);
		Method setFilterTextMethod = findMethod(ingredientFilter.getClass(), "setFilterText", 1);
		if (getFilterTextMethod == null || setFilterTextMethod == null) {
			return false;
		}

		Object currentFilterTextObject = getFilterTextMethod.invoke(ingredientFilter);
		String currentFilterText = currentFilterTextObject instanceof String filterText
				? filterText
				: String.valueOf(currentFilterTextObject);
		String nudgedFilterText = buildNudgedFilterText(currentFilterText);
		if (nudgedFilterText.equals(currentFilterText)) {
			return false;
		}

		setFilterTextMethod.invoke(ingredientFilter, nudgedFilterText);
		setFilterTextMethod.invoke(ingredientFilter, currentFilterText);
		return true;
	}

	private static String buildNudgedFilterText(String currentFilterText) {
		if (currentFilterText.isEmpty()) {
			return " ";
		}
		if (currentFilterText.length() >= 128) {
			return currentFilterText.substring(1);
		}
		return currentFilterText + " ";
	}

	private static boolean forceOverlayScreenPropertiesUpdate(Object runtime) throws ReflectiveOperationException {
		Object ingredientListOverlay = invokeNoArg(runtime, "getIngredientListOverlay");
		if (ingredientListOverlay == null) {
			return false;
		}
		Object updater = invokeNoArg(ingredientListOverlay, "getScreenPropertiesUpdater");
		if (updater == null) {
			return false;
		}
		Method updateScreenMethod = findMethod(updater.getClass(), "updateScreen", 1);
		Method updateMethod = findMethod(updater.getClass(), "update", 0);
		if (updateScreenMethod == null || updateMethod == null) {
			return false;
		}

		// Ensure JEI marks the updater as changed even when the current screen/properties are stable.
		Object updaterAfterNull = updateScreenMethod.invoke(updater, new Object[]{null});
		Object updateSource = updaterAfterNull != null ? updaterAfterNull : updater;
		Object updaterAfterScreen = updateScreenMethod.invoke(updateSource, Minecraft.getInstance().screen);
		updateMethod.invoke(updaterAfterScreen != null ? updaterAfterScreen : updateSource);
		return true;
	}

	@Nullable
	private static Object getTypedIngredientUnderMouse(@Nullable Object overlay) {
		if (overlay == null) {
			return null;
		}

		try {
			Object typedIngredientOptional = invokeNoArg(overlay, "getIngredientUnderMouse");
			if (typedIngredientOptional instanceof Optional<?> optional && optional.isPresent()) {
				return optional.get();
			}
		} catch (RuntimeException ignored) {
		}
		return null;
	}

	private static String readIngredientInfoForLog(Object typedIngredient) {
		if (typedIngredient == null) {
			return "null";
		}

		try {
			Method getTypeMethod = findMethod(typedIngredient.getClass(), "getType", 0);
			Method getIngredientMethod = findMethod(typedIngredient.getClass(), "getIngredient", 0);
			if (getTypeMethod == null || getIngredientMethod == null) {
				return typedIngredient.getClass().getSimpleName();
			}
			Object type = getTypeMethod.invoke(typedIngredient);
			Object ingredient = getIngredientMethod.invoke(typedIngredient);
			String typeName = type == null ? "null-type" : type.getClass().getSimpleName();
			if (ingredient instanceof ItemStack stack) {
				return typeName + ":" + stack.getDescriptionId();
			}
			return typeName + ":" + String.valueOf(ingredient);
		} catch (ReflectiveOperationException ignored) {
			return typedIngredient.getClass().getSimpleName();
		}
	}

	private static ItemStack getItemStackFromTypedIngredient(Object typedIngredient) {
		if (typedIngredient == null) {
			return ItemStack.EMPTY;
		}
		try {
			Method getIngredientMethod = findMethod(typedIngredient.getClass(), "getIngredient", 0);
			if (getIngredientMethod == null) {
				return ItemStack.EMPTY;
			}
			Object ingredient = getIngredientMethod.invoke(typedIngredient);
			if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
				return normalizeForIngredientList(stack);
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return ItemStack.EMPTY;
	}

	@Nullable
	private static HoveredTypedIngredient getHoveredTypedIngredient(Object runtime) {
		Object ingredientListOverlay = invokeNoArg(runtime, "getIngredientListOverlay");
		Object typedIngredient = getTypedIngredientUnderMouse(ingredientListOverlay);
		if (typedIngredient != null) {
			return new HoveredTypedIngredient(typedIngredient, "ingredient_list");
		}

		Object bookmarkOverlay = invokeNoArg(runtime, "getBookmarkOverlay");
		typedIngredient = getTypedIngredientUnderMouse(bookmarkOverlay);
		if (typedIngredient != null) {
			return new HoveredTypedIngredient(typedIngredient, "bookmark");
		}
		return null;
	}

	private record CraftableSnapshot(List<CraftingRecipe> recipes, Set<String> outputItemIds) {
	}

	private static final class AvailableIngredientStack {
		private final ItemStack stack;
		private int count;

		private AvailableIngredientStack(ItemStack stack, int count) {
			this.stack = stack;
			this.count = count;
		}
	}

	private static final class HoveredTypedIngredient {
		private final Object typedIngredient;
		private final String source;

		private HoveredTypedIngredient(Object typedIngredient, String source) {
			this.typedIngredient = typedIngredient;
			this.source = source;
		}
	}

	private static final class ClickProbe {
		private final long timestampMs;
		private final int menuId;
		private final int mouseButton;
		private final boolean enabledForMenu;
		private final boolean runtimeAvailable;
		private final String ingredientSource;
		private final String ingredientInfo;
		private final String phase;

		private ClickProbe(
				long timestampMs,
				int menuId,
				int mouseButton,
				boolean enabledForMenu,
				boolean runtimeAvailable,
				String ingredientSource,
				String ingredientInfo,
				String phase
		) {
			this.timestampMs = timestampMs;
			this.menuId = menuId;
			this.mouseButton = mouseButton;
			this.enabledForMenu = enabledForMenu;
			this.runtimeAvailable = runtimeAvailable;
			this.ingredientSource = ingredientSource;
			this.ingredientInfo = ingredientInfo;
			this.phase = phase;
		}
	}
}


