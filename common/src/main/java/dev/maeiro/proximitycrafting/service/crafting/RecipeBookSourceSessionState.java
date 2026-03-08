package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class RecipeBookSourceSessionState {
	private final long snapshotCacheTtlMs;
	private final long adjustSnapshotMinIntervalMs;
	private List<RecipeBookSourceEntry> clientRecipeBookSupplementalSources = List.of();
	private List<RecipeBookSourceEntry> serverRecipeBookSnapshotCache = List.of();
	private long serverRecipeBookSnapshotCacheBuiltAtMs;
	private boolean serverRecipeBookSnapshotCacheValid;
	private boolean serverRecipeBookSnapshotPrewarmPending = true;
	private long lastAdjustSnapshotSentAtMs;

	public RecipeBookSourceSessionState(long snapshotCacheTtlMs, long adjustSnapshotMinIntervalMs) {
		this.snapshotCacheTtlMs = snapshotCacheTtlMs;
		this.adjustSnapshotMinIntervalMs = adjustSnapshotMinIntervalMs;
	}

	public boolean setClientRecipeBookSupplementalSources(List<RecipeBookSourceEntry> sourceEntries) {
		if (sourceEntries == null || sourceEntries.isEmpty()) {
			boolean changed = !this.clientRecipeBookSupplementalSources.isEmpty();
			this.clientRecipeBookSupplementalSources = List.of();
			return changed;
		}

		List<RecipeBookSourceEntry> sanitized = new ArrayList<>(sourceEntries.size());
		for (RecipeBookSourceEntry sourceEntry : sourceEntries) {
			if (sourceEntry == null || sourceEntry.count() <= 0 || sourceEntry.stack().isEmpty()) {
				continue;
			}
			ItemStack normalized = sourceEntry.stack().copy();
			normalized.setCount(1);
			sanitized.add(new RecipeBookSourceEntry(normalized, sourceEntry.count()));
		}
		List<RecipeBookSourceEntry> normalizedSources = List.copyOf(sanitized);
		boolean changed = !areRecipeBookSourceListsEqual(this.clientRecipeBookSupplementalSources, normalizedSources);
		this.clientRecipeBookSupplementalSources = normalizedSources;
		return changed;
	}

	public List<RecipeBookSourceEntry> getClientRecipeBookSupplementalSources() {
		return clientRecipeBookSupplementalSources;
	}

	public SnapshotResult getServerRecipeBookSnapshot(boolean preferCache, long nowMs, Supplier<List<RecipeBookSourceEntry>> rebuildSupplier) {
		if (preferCache
				&& serverRecipeBookSnapshotCacheValid
				&& (nowMs - serverRecipeBookSnapshotCacheBuiltAtMs) <= snapshotCacheTtlMs) {
			return new SnapshotResult(serverRecipeBookSnapshotCache, true, nowMs - serverRecipeBookSnapshotCacheBuiltAtMs);
		}

		List<RecipeBookSourceEntry> rebuilt = List.copyOf(rebuildSupplier.get());
		serverRecipeBookSnapshotCache = rebuilt;
		serverRecipeBookSnapshotCacheBuiltAtMs = nowMs;
		serverRecipeBookSnapshotCacheValid = true;
		serverRecipeBookSnapshotPrewarmPending = false;
		return new SnapshotResult(serverRecipeBookSnapshotCache, false, 0L);
	}

	public void invalidateServerRecipeBookSnapshotCache() {
		serverRecipeBookSnapshotCacheValid = false;
	}

	public AdjustSnapshotDecision shouldSendSnapshotForAdjust(long nowMs) {
		if (lastAdjustSnapshotSentAtMs != 0L && (nowMs - lastAdjustSnapshotSentAtMs) < adjustSnapshotMinIntervalMs) {
			return new AdjustSnapshotDecision(false, nowMs - lastAdjustSnapshotSentAtMs, adjustSnapshotMinIntervalMs);
		}
		lastAdjustSnapshotSentAtMs = nowMs;
		return new AdjustSnapshotDecision(true, 0L, adjustSnapshotMinIntervalMs);
	}

	public boolean consumeServerSnapshotPrewarmPending() {
		if (!serverRecipeBookSnapshotPrewarmPending) {
			return false;
		}
		serverRecipeBookSnapshotPrewarmPending = false;
		return true;
	}

	private static boolean areRecipeBookSourceListsEqual(List<RecipeBookSourceEntry> left, List<RecipeBookSourceEntry> right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null || left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			RecipeBookSourceEntry leftEntry = left.get(i);
			RecipeBookSourceEntry rightEntry = right.get(i);
			if (leftEntry.count() != rightEntry.count()) {
				return false;
			}
			if (!ItemStack.isSameItemSameTags(leftEntry.stack(), rightEntry.stack())) {
				return false;
			}
		}
		return true;
	}

	public record SnapshotResult(
			List<RecipeBookSourceEntry> entries,
			boolean cacheHit,
			long cacheAgeMs
	) {
	}

	public record AdjustSnapshotDecision(
			boolean shouldSend,
			long elapsedMs,
			long minIntervalMs
	) {
	}
}
