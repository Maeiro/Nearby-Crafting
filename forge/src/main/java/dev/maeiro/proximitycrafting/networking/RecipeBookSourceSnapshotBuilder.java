package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.service.scan.ForgeScanOptionsFactory;
import dev.maeiro.proximitycrafting.service.scan.ProximityInventoryScanner;
import dev.maeiro.proximitycrafting.service.scan.ScanOptions;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeBookSourceSnapshotBuilder {
	private RecipeBookSourceSnapshotBuilder() {
	}

	public static List<RecipeBookSourceEntry> build(ProximityCraftingMenu menu) {
		long startNs = System.nanoTime();
		List<ItemSourceRef> sourceRefs = collectRecipeBookSources(menu);
		long collectNs = System.nanoTime();
		List<RecipeBookSourceEntry> entries = aggregateSourceEntries(sourceRefs);
		long endNs = System.nanoTime();

		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] snapshot.build menu={} sourceRefs={} entries={} collectMs={} aggregateMs={} totalMs={}",
					menu.containerId,
					sourceRefs.size(),
					entries.size(),
					formatMs(collectNs - startNs),
					formatMs(endNs - collectNs),
					formatMs(endNs - startNs)
			);
		}
		return entries;
	}

	private static List<ItemSourceRef> collectRecipeBookSources(ProximityCraftingMenu menu) {
		ScanOptions scanOptions = ForgeScanOptionsFactory.fromMenu(menu);
		List<ItemSourceRef> sources = new ArrayList<>();
		sources.addAll(ProximityInventoryScanner.collectContainerSources(menu.getLevel(), menu.getTablePos(), scanOptions));

		if (scanOptions.includePlayerInventory()) {
			for (ItemSourceRef sourceRef : ProximityInventoryScanner.collectPlayerSources(menu.getPlayer(), scanOptions)) {
				if (sourceRef.sourceType() == ItemSourceRef.SourceType.PLAYER_BACKPACK) {
					sources.add(sourceRef);
				}
			}
		}

		return sources;
	}

	private static List<RecipeBookSourceEntry> aggregateSourceEntries(List<ItemSourceRef> sourceRefs) {
		Map<String, ItemStack> exemplarStacks = new LinkedHashMap<>();
		Map<String, Integer> totalCounts = new LinkedHashMap<>();

		for (ItemSourceRef sourceRef : sourceRefs) {
			ItemStack stack = sourceRef.slotRef().peekStack();
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

		List<RecipeBookSourceEntry> entries = new ArrayList<>(exemplarStacks.size());
		for (Map.Entry<String, ItemStack> exemplarEntry : exemplarStacks.entrySet()) {
			int count = totalCounts.getOrDefault(exemplarEntry.getKey(), 0);
			if (count <= 0) {
				continue;
			}
			entries.add(new RecipeBookSourceEntry(exemplarEntry.getValue(), count));
		}
		return entries;
	}

	private static String buildStackKey(ItemStack stack) {
		CompoundTag serialized = new CompoundTag();
		stack.save(serialized);
		serialized.remove("Count");
		return serialized.toString();
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return dev.maeiro.proximitycrafting.config.ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private static String formatMs(long nanos) {
		return String.format("%.3f", nanos / 1_000_000.0D);
	}
}


