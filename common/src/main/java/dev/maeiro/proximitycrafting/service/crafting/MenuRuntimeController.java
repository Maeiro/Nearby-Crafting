package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ClientPreferences;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class MenuRuntimeController {
	private final long snapshotCacheTtlMs;
	private final RecipeBookSourceSessionState recipeBookSourceSessionState;

	public MenuRuntimeController(long snapshotCacheTtlMs, long adjustSnapshotMinIntervalMs) {
		this.snapshotCacheTtlMs = snapshotCacheTtlMs;
		this.recipeBookSourceSessionState =
				new RecipeBookSourceSessionState(snapshotCacheTtlMs, adjustSnapshotMinIntervalMs);
	}

	public void onSlotsChanged(TrackedCraftGridSession trackedCraftGridSession, Runnable refreshCraftingResult) {
		if (trackedCraftGridSession.onCraftGridChangedDeferred()) {
			return;
		}
		refreshCraftingResult.run();
	}

	public void fillSupplementalRecipeBookSources(StackedContents itemHelper) {
		for (RecipeBookSourceEntry sourceEntry : recipeBookSourceSessionState.getClientRecipeBookSupplementalSources()) {
			if (sourceEntry.count() <= 0 || sourceEntry.stack().isEmpty()) {
				continue;
			}
			int remaining = sourceEntry.count();
			while (remaining > 0) {
				ItemStack stackChunk = sourceEntry.stack().copy();
				int chunkSize = Math.min(remaining, stackChunk.getMaxStackSize());
				stackChunk.setCount(chunkSize);
				itemHelper.accountStack(stackChunk);
				remaining -= chunkSize;
			}
		}
	}

	public boolean setClientRecipeBookSupplementalSources(List<RecipeBookSourceEntry> sourceEntries) {
		return recipeBookSourceSessionState.setClientRecipeBookSupplementalSources(sourceEntries);
	}

	public List<RecipeBookSourceEntry> getClientRecipeBookSupplementalSources() {
		return recipeBookSourceSessionState.getClientRecipeBookSupplementalSources();
	}

	public void invalidateServerRecipeBookSnapshotCache() {
		recipeBookSourceSessionState.invalidateServerRecipeBookSnapshotCache();
	}

	public void onSnapshotInputsChanged() {
		invalidateServerRecipeBookSnapshotCache();
	}

	public void onClientPreferencesChanged(ClientPreferences previous, ClientPreferences next) {
		ClientPreferences before = previous == null ? ClientPreferences.defaults() : previous;
		ClientPreferences after = next == null ? ClientPreferences.defaults() : next;
		boolean includeChanged = before.includePlayerInventory() != after.includePlayerInventory();
		boolean priorityChanged = before.sourcePriority() != after.sourcePriority();
		if (includeChanged || priorityChanged) {
			invalidateServerRecipeBookSnapshotCache();
		}
	}

	public void onBroadcastChanges(MenuRuntimeHost host, RecipeBookSnapshotSourcePort snapshotSourcePort) {
		if (host.isClientSide() || !recipeBookSourceSessionState.consumeServerSnapshotPrewarmPending()) {
			return;
		}
		getServerRecipeBookSnapshot(host, false, "menu_open_prewarm", snapshotSourcePort);
	}

	public List<RecipeBookSourceEntry> getServerRecipeBookSnapshot(
			MenuRuntimeHost host,
			boolean preferCache,
			String reason,
			RecipeBookSnapshotSourcePort snapshotSourcePort
	) {
		long nowMs = System.currentTimeMillis();
		RecipeBookSourceSessionState.SnapshotResult snapshotResult = recipeBookSourceSessionState.getServerRecipeBookSnapshot(
				preferCache,
				nowMs,
				() -> RecipeBookSnapshotOperations.buildSnapshot(snapshotSourcePort)
		);
		if (host.debugLoggingEnabled()) {
			if (snapshotResult.cacheHit()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] snapshot.cache.hit menu={} ageMs={} reason={} entries={}",
						host.containerId(),
						snapshotResult.cacheAgeMs(),
						reason,
						snapshotResult.entries().size()
				);
			} else {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] snapshot.cache.rebuild menu={} reason={} entries={} ttlMs={}",
						host.containerId(),
						reason,
						snapshotResult.entries().size(),
						snapshotCacheTtlMs
				);
			}
		}
		return snapshotResult.entries();
	}

	public boolean shouldSendSnapshotForAdjust(MenuRuntimeHost host) {
		long nowMs = System.currentTimeMillis();
		RecipeBookSourceSessionState.AdjustSnapshotDecision decision = recipeBookSourceSessionState.shouldSendSnapshotForAdjust(nowMs);
		if (!decision.shouldSend()) {
			if (host.debugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] snapshot.adjust.skip menu={} elapsedMs={} minIntervalMs={}",
						host.containerId(),
						decision.elapsedMs(),
						decision.minIntervalMs()
				);
			}
			return false;
		}
		return true;
	}

	public void sendRecipeBookSourceSnapshot(
			MenuRuntimeHost host,
			MenuSnapshotTransport snapshotTransport,
			ServerPlayer serverPlayer,
			boolean preferCache,
			String reason,
			RecipeBookSnapshotSourcePort snapshotSourcePort
	) {
		snapshotTransport.sendRecipeBookSourceSnapshot(
				serverPlayer,
				host.containerId(),
				getServerRecipeBookSnapshot(host, preferCache, reason, snapshotSourcePort)
		);
	}

	public void onResultTaken(
			MenuRuntimeHost host,
			MenuSnapshotTransport snapshotTransport,
			ServerPlayer serverPlayer,
			ResultTakeOutcome outcome,
			RecipeBookSnapshotSourcePort snapshotSourcePort
	) {
		if (outcome.refillSucceeded()) {
			invalidateServerRecipeBookSnapshotCache();
		}
		sendRecipeBookSourceSnapshot(host, snapshotTransport, serverPlayer, false, "result_slot_take", snapshotSourcePort);
	}
}
