package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.service.scan.NearbyInventoryScanner;
import dev.maeiro.nearbycrafting.service.source.ItemSourceRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeBookSourceSnapshotBuilder {
	private RecipeBookSourceSnapshotBuilder() {
	}

	public static List<NearbyCraftingMenu.RecipeBookSourceEntry> build(NearbyCraftingMenu menu) {
		List<ItemSourceRef> sourceRefs = collectRecipeBookSources(menu);
		return aggregateSourceEntries(sourceRefs);
	}

	private static List<ItemSourceRef> collectRecipeBookSources(NearbyCraftingMenu menu) {
		List<ItemSourceRef> sources = new ArrayList<>();
		sources.addAll(NearbyInventoryScanner.collectContainerSources(menu.getLevel(), menu.getTablePos()));

		if (menu.isIncludePlayerInventory()) {
			for (ItemSourceRef sourceRef : NearbyInventoryScanner.collectPlayerSources(menu.getPlayer(), true)) {
				if (sourceRef.sourceType() == ItemSourceRef.SourceType.PLAYER_BACKPACK) {
					sources.add(sourceRef);
				}
			}
		}

		return sources;
	}

	private static List<NearbyCraftingMenu.RecipeBookSourceEntry> aggregateSourceEntries(List<ItemSourceRef> sourceRefs) {
		Map<String, ItemStack> exemplarStacks = new LinkedHashMap<>();
		Map<String, Integer> totalCounts = new LinkedHashMap<>();

		for (ItemSourceRef sourceRef : sourceRefs) {
			ItemStack stack = sourceRef.handler().getStackInSlot(sourceRef.slot());
			if (stack.isEmpty()) {
				continue;
			}

			String stackKey = buildStackKey(stack);
			exemplarStacks.computeIfAbsent(stackKey, ignored -> {
				ItemStack exemplar = stack.copy();
				exemplar.setCount(1);
				return exemplar;
			});
			totalCounts.merge(stackKey, stack.getCount(), Integer::sum);
		}

		List<NearbyCraftingMenu.RecipeBookSourceEntry> entries = new ArrayList<>(exemplarStacks.size());
		for (Map.Entry<String, ItemStack> exemplarEntry : exemplarStacks.entrySet()) {
			int count = totalCounts.getOrDefault(exemplarEntry.getKey(), 0);
			if (count <= 0) {
				continue;
			}
			entries.add(new NearbyCraftingMenu.RecipeBookSourceEntry(exemplarEntry.getValue(), count));
		}
		return entries;
	}

	private static String buildStackKey(ItemStack stack) {
		CompoundTag serialized = new CompoundTag();
		stack.save(serialized);
		serialized.remove("Count");
		return serialized.toString();
	}
}
