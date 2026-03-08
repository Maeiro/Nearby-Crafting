package dev.maeiro.proximitycrafting.client.runtime;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.session.ClientRecipeSessionState;
import dev.maeiro.proximitycrafting.client.session.SourceSnapshotApplyResult;

public final class ScreenSyncCoordinator {
	private int recipeBookSourceSyncTicker = 0;
	private int deferredRefreshTicks = 0;

	public TickResult tick(long nowMs, boolean deferPeriodicSourceSync, int syncIntervalTicks) {
		boolean shouldRefreshRecipeBookNow = false;
		boolean shouldRequestSourceSyncNow = false;
		if (deferredRefreshTicks > 0) {
			deferredRefreshTicks--;
			if (deferredRefreshTicks == 0) {
				shouldRefreshRecipeBookNow = true;
			}
		}
		if (!deferPeriodicSourceSync) {
			recipeBookSourceSyncTicker++;
			if (recipeBookSourceSyncTicker >= syncIntervalTicks) {
				recipeBookSourceSyncTicker = 0;
				shouldRequestSourceSyncNow = true;
			}
		} else {
			recipeBookSourceSyncTicker = 0;
		}
		return new TickResult(shouldRefreshRecipeBookNow, shouldRequestSourceSyncNow);
	}

	public ClientRecipeSessionState.SourceSyncRequestResult requestSourceSync(ScreenRuntimeHost host, long nowMs) {
		ClientRecipeSessionState.SourceSyncRequestResult requestResult = host.sessionState().requestSourceSync(
				host.requestSender(),
				host.containerId(),
				nowMs
		);
		if (!host.debugLoggingEnabled()) {
			return requestResult;
		}
		switch (requestResult.disposition()) {
			case QUEUED_IN_FLIGHT -> ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.requestRecipeBookSourceSync queued menu={} reason=in_flight deferredRefreshTicks={}",
					host.containerId(),
					deferredRefreshTicks
			);
			case QUEUED_MIN_INTERVAL -> ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.requestRecipeBookSourceSync queued menu={} reason=min_interval elapsedMs={} deferredRefreshTicks={}",
					host.containerId(),
					requestResult.elapsedSinceLastSendMs(),
					deferredRefreshTicks
			);
			case SENT -> ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.requestRecipeBookSourceSync menu={} deferredRefreshTicks={}",
					host.containerId(),
					deferredRefreshTicks
			);
		}
		return requestResult;
	}

	public SourceSnapshotUiDecision onSourceSnapshotApplied(
			ScreenRuntimeHost host,
			int entryCount,
			boolean sourcesChanged,
			SourceSnapshotApplyResult result
	) {
		if (host.debugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.sourceSnapshotApplied menu={} entries={} source_sync_rtt_ms={} action_snapshot_apply_delay_ms={} hadSourceSyncInFlight={} actionBusy={}",
					host.containerId(),
					entryCount,
					result.sourceSyncRttMs(),
					result.actionSnapshotApplyDelayMs(),
					result.hadSourceSyncInFlight(),
					result.actionBusy()
			);
		}
		return new SourceSnapshotUiDecision(
				sourcesChanged,
				result.shouldRequestQueuedSyncNow(),
				result.actionBusy()
		);
	}

	public boolean shouldDeferPeriodicSourceSync(ScreenRuntimeHost host, long nowMs, long actionCooldownMs) {
		return host.sessionState().shouldDeferPeriodicSourceSync(nowMs, actionCooldownMs);
	}

	public void scheduleDeferredRecipeBookRefresh() {
		deferredRefreshTicks = Math.max(deferredRefreshTicks, 2);
	}

	public boolean flushDeferredRecipeBookRefreshAfterAction(ScreenRuntimeHost host, String reason) {
		if (!host.sessionState().shouldFlushDeferredRecipeBookRefresh()) {
			return false;
		}
		host.sessionState().consumeDeferredRecipeBookRefresh();
		scheduleDeferredRecipeBookRefresh();
		if (host.debugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.flushDeferredRecipeBookRefresh menu={} reason={}",
					host.containerId(),
					reason
			);
		}
		return true;
	}

	public void clearDeferredRecipeBookRefresh() {
		deferredRefreshTicks = 0;
	}

	public void reset() {
		recipeBookSourceSyncTicker = 0;
		deferredRefreshTicks = 0;
	}

	public record TickResult(boolean shouldRefreshRecipeBookNow, boolean shouldRequestSourceSyncNow) {
	}

	public record SourceSnapshotUiDecision(
			boolean sourcesChanged,
			boolean shouldRequestQueuedSyncNow,
			boolean actionBusy
	) {
	}
}

