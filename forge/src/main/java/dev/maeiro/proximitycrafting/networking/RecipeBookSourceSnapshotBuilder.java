package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.service.scan.ForgeScanOptionsFactory;
import dev.maeiro.proximitycrafting.service.scan.ProximityInventoryScanner;
import dev.maeiro.proximitycrafting.service.scan.RecipeBookSourceAggregator;
import dev.maeiro.proximitycrafting.service.scan.ScanOptions;
import dev.maeiro.proximitycrafting.service.scan.SourceCollectionResult;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;

import java.util.List;

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
		SourceCollectionResult result = ProximityInventoryScanner.collectSourceResult(
				menu.getLevel(),
				menu.getTablePos(),
				menu.getPlayer(),
				scanOptions
		);
		return result.recipeBookSupplementalSources();
	}

	private static List<RecipeBookSourceEntry> aggregateSourceEntries(List<ItemSourceRef> sourceRefs) {
		return RecipeBookSourceAggregator.aggregateEntries(sourceRefs);
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return dev.maeiro.proximitycrafting.config.ProximityCraftingConfig.serverRuntimeSettings().debugLogging();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private static String formatMs(long nanos) {
		return String.format("%.3f", nanos / 1_000_000.0D);
	}
}


