package dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade;

import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.service.prefs.PlayerPreferenceStore;
import dev.maeiro.nearbycrafting.service.scan.NearbyInventoryScanner;
import dev.maeiro.nearbycrafting.service.source.ItemSourceRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdvancedBackpackRecipeSourceSnapshotBuilder {
	private AdvancedBackpackRecipeSourceSnapshotBuilder() {
	}

	public static List<NearbyCraftingMenu.RecipeBookSourceEntry> build(Player player, AdvancedCraftingUpgradeWrapper wrapper) {
		boolean includePlayerInventory = PlayerPreferenceStore.get(player)
				.map(PlayerPreferenceStore.PreferenceSnapshot::includePlayerInventory)
				.orElse(true);
		NearbyCraftingConfig.SourcePriority priority = PlayerPreferenceStore.get(player)
				.map(PlayerPreferenceStore.PreferenceSnapshot::sourcePriority)
				.orElseGet(() -> NearbyCraftingConfig.SourcePriority.fromConfig(NearbyCraftingConfig.SERVER.advancedBackpackSourcePriority.get()));

		List<ItemSourceRef> sources = NearbyInventoryScanner.collectAdvancedBackpackSources(
				player.level(),
				wrapper.getScanAnchorOrPlayerPos(player),
				player,
				includePlayerInventory,
				priority,
				wrapper.getHostBackpackInventory()
		);
		return aggregateSourceEntries(sources);
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
