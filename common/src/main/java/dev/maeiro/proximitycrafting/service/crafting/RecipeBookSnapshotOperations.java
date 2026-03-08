package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.service.scan.RecipeBookSourceAggregator;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;

import java.util.List;

public final class RecipeBookSnapshotOperations {
	private RecipeBookSnapshotOperations() {
	}

	public static List<RecipeBookSourceEntry> buildSnapshot(RecipeBookSnapshotSourcePort sourcePort) {
		long startNs = System.nanoTime();
		List<ItemSourceRef> sourceRefs = sourcePort.collectRecipeBookSourceRefs();
		long collectNs = System.nanoTime();
		List<RecipeBookSourceEntry> entries = RecipeBookSourceAggregator.aggregateEntries(sourceRefs);
		long endNs = System.nanoTime();

		if (sourcePort.debugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] snapshot.build menu={} sourceRefs={} entries={} collectMs={} aggregateMs={} totalMs={}",
					sourcePort.debugContextId(),
					sourceRefs.size(),
					entries.size(),
					formatMs(collectNs - startNs),
					formatMs(endNs - collectNs),
					formatMs(endNs - startNs)
			);
		}
		return entries;
	}

	private static String formatMs(long nanos) {
		return String.format("%.3f", nanos / 1_000_000.0D);
	}
}
