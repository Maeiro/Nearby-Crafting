package dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade;

import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.service.crafting.ExtractionCommitResult;
import dev.maeiro.nearbycrafting.service.crafting.ExtractionPlan;
import dev.maeiro.nearbycrafting.service.crafting.FillResult;
import dev.maeiro.nearbycrafting.service.crafting.RecipeFillService;
import dev.maeiro.nearbycrafting.service.prefs.PlayerPreferenceStore;
import dev.maeiro.nearbycrafting.service.scan.NearbyInventoryScanner;
import dev.maeiro.nearbycrafting.service.source.IngredientSourcePool;
import dev.maeiro.nearbycrafting.service.source.ItemSourceRef;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AdvancedBackpackRecipeFillService {
	private AdvancedBackpackRecipeFillService() {
	}

	public static FillResult fillFromRecipe(
			Player player,
			CraftingUpgradeContainer container,
			AdvancedCraftingUpgradeWrapper wrapper,
			CraftingRecipe recipe,
			boolean craftAll
	) {
		List<Ingredient> targetGrid = RecipeFillService.buildTargetGrid(recipe);
		clearCraftMatrix(container, wrapper, player);

		List<ItemSourceRef> sources = collectSources(player, wrapper);
		IngredientSourcePool pool = new IngredientSourcePool(sources);
		Optional<ExtractionPlan> firstPlanOptional = pool.plan(targetGrid);
		if (firstPlanOptional.isEmpty()) {
			return FillResult.failure("nearbycrafting.feedback.not_enough_ingredients");
		}

		ExtractionCommitResult firstCommit = firstPlanOptional.get().commit();
		if (firstCommit == null || !applyCommitAsSet(container, firstCommit)) {
			if (firstCommit != null) {
				rollbackCommit(firstCommit);
			}
			return FillResult.failure("nearbycrafting.feedback.fill_failed");
		}

		int loadedCrafts = 1;
		if (craftAll) {
			loadedCrafts += fillAdditionalCrafts(container, pool, targetGrid);
		}

		container.getCraftMatrix().setChanged();
		if (craftAll) {
			return FillResult.success("nearbycrafting.feedback.filled_max", loadedCrafts);
		}
		return FillResult.success("nearbycrafting.feedback.filled", 0);
	}

	private static List<ItemSourceRef> collectSources(Player player, AdvancedCraftingUpgradeWrapper wrapper) {
		boolean includePlayerInventory = PlayerPreferenceStore.get(player)
				.map(PlayerPreferenceStore.PreferenceSnapshot::includePlayerInventory)
				.orElse(true);
		NearbyCraftingConfig.SourcePriority priority = PlayerPreferenceStore.get(player)
				.map(PlayerPreferenceStore.PreferenceSnapshot::sourcePriority)
				.orElseGet(() -> NearbyCraftingConfig.SourcePriority.fromConfig(NearbyCraftingConfig.SERVER.advancedBackpackSourcePriority.get()));

		return NearbyInventoryScanner.collectAdvancedBackpackSources(
				player.level(),
				wrapper.getScanAnchorOrPlayerPos(player),
				player,
				includePlayerInventory,
				priority,
				wrapper.getHostBackpackInventory()
		);
	}

	private static void clearCraftMatrix(CraftingUpgradeContainer container, AdvancedCraftingUpgradeWrapper wrapper, Player player) {
		Container craftMatrix = container.getCraftMatrix();
		for (int slot = 0; slot < craftMatrix.getContainerSize(); slot++) {
			ItemStack current = craftMatrix.getItem(slot);
			if (current.isEmpty()) {
				continue;
			}
			ItemStack remaining = current.copy();
			if (wrapper.insertIntoStorageOrPlayer(player, remaining)) {
				craftMatrix.setItem(slot, ItemStack.EMPTY);
				continue;
			}
			boolean inserted = player.getInventory().add(remaining);
			if (!inserted && !remaining.isEmpty()) {
				player.drop(remaining, false);
			}
			craftMatrix.setItem(slot, ItemStack.EMPTY);
		}
	}

	private static int fillAdditionalCrafts(CraftingUpgradeContainer container, IngredientSourcePool pool, List<Ingredient> targetGrid) {
		int additionalCrafts = 0;
		int maxIterations = NearbyCraftingConfig.SERVER.maxShiftCraftIterations.get();
		for (int iteration = 1; iteration < maxIterations; iteration++) {
			if (!hasRoomForAnotherCraft(container, targetGrid)) {
				break;
			}

			List<ItemStack> exactTemplate = buildExactTemplate(container, targetGrid);
			Optional<ExtractionPlan> planOptional = pool.planExactStacks(exactTemplate);
			if (planOptional.isEmpty()) {
				break;
			}

			ExtractionCommitResult commitResult = planOptional.get().commit();
			if (commitResult == null || !applyCommitAsAdd(container, commitResult)) {
				if (commitResult != null) {
					rollbackCommit(commitResult);
				}
				break;
			}
			additionalCrafts++;
		}
		return additionalCrafts;
	}

	private static boolean applyCommitAsSet(CraftingUpgradeContainer container, ExtractionCommitResult commitResult) {
		Container craftMatrix = container.getCraftMatrix();
		ItemStack[] extractedStacks = commitResult.extractedStacks();
		for (int slot = 0; slot < 9; slot++) {
			craftMatrix.setItem(slot, extractedStacks[slot].copy());
		}
		return true;
	}

	private static boolean applyCommitAsAdd(CraftingUpgradeContainer container, ExtractionCommitResult commitResult) {
		Container craftMatrix = container.getCraftMatrix();
		ItemStack[] extractedStacks = commitResult.extractedStacks();

		for (int slot = 0; slot < 9; slot++) {
			ItemStack extracted = extractedStacks[slot];
			if (extracted.isEmpty()) {
				continue;
			}
			ItemStack current = craftMatrix.getItem(slot);
			if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, extracted)) {
				return false;
			}
			int slotLimit = current.isEmpty() ? extracted.getMaxStackSize() : current.getMaxStackSize();
			slotLimit = Math.min(slotLimit, craftMatrix.getMaxStackSize());
			int mergedCount = (current.isEmpty() ? 0 : current.getCount()) + extracted.getCount();
			if (mergedCount > slotLimit) {
				return false;
			}
		}

		for (int slot = 0; slot < 9; slot++) {
			ItemStack extracted = extractedStacks[slot];
			if (extracted.isEmpty()) {
				continue;
			}
			ItemStack current = craftMatrix.getItem(slot);
			if (current.isEmpty()) {
				craftMatrix.setItem(slot, extracted.copy());
			} else {
				ItemStack merged = current.copy();
				merged.grow(extracted.getCount());
				craftMatrix.setItem(slot, merged);
			}
		}
		return true;
	}

	private static boolean hasRoomForAnotherCraft(CraftingUpgradeContainer container, List<Ingredient> targetGrid) {
		Container craftMatrix = container.getCraftMatrix();
		for (int slot = 0; slot < 9; slot++) {
			if (targetGrid.get(slot).isEmpty()) {
				continue;
			}
			ItemStack current = craftMatrix.getItem(slot);
			if (current.isEmpty()) {
				return false;
			}
			int slotLimit = Math.min(craftMatrix.getMaxStackSize(), current.getMaxStackSize());
			if (current.getCount() >= slotLimit) {
				return false;
			}
		}
		return true;
	}

	private static List<ItemStack> buildExactTemplate(CraftingUpgradeContainer container, List<Ingredient> targetGrid) {
		Container craftMatrix = container.getCraftMatrix();
		List<ItemStack> template = new ArrayList<>(9);
		for (int slot = 0; slot < 9; slot++) {
			if (targetGrid.get(slot).isEmpty()) {
				template.add(ItemStack.EMPTY);
				continue;
			}
			ItemStack stack = craftMatrix.getItem(slot);
			if (stack.isEmpty()) {
				template.add(ItemStack.EMPTY);
				continue;
			}
			ItemStack one = stack.copy();
			one.setCount(1);
			template.add(one);
		}
		return template;
	}

	private static void rollbackCommit(ExtractionCommitResult commitResult) {
		ItemStack[] extractedStacks = commitResult.extractedStacks();
		ItemSourceRef[] sourceRefs = commitResult.sourceRefs();
		for (int slot = extractedStacks.length - 1; slot >= 0; slot--) {
			ItemStack extracted = extractedStacks[slot];
			ItemSourceRef sourceRef = sourceRefs[slot];
			if (extracted.isEmpty() || sourceRef == null) {
				continue;
			}
			sourceRef.handler().insertItem(sourceRef.slot(), extracted.copy(), false);
		}
	}
}
