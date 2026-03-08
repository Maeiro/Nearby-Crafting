package dev.maeiro.proximitycrafting.client.runtime;

import dev.maeiro.proximitycrafting.ProximityCrafting;

public final class ScreenScrollController {
	private final long scrollDebugLogThrottleMs;
	private final long scrollPerfLogIntervalMs;
	private final double scrollSlowEventMs;

	private long scrollPerfWindowStartMs = 0L;
	private int scrollPerfEvents = 0;
	private int scrollPerfHandledEvents = 0;
	private long scrollPerfTotalNs = 0L;
	private double scrollPerfMaxMs = 0.0D;
	private long lastScrollDebugLogAtMs = 0L;
	private int suppressedScrollDebugLogs = 0;

	public ScreenScrollController(long scrollDebugLogThrottleMs, long scrollPerfLogIntervalMs, double scrollSlowEventMs) {
		this.scrollDebugLogThrottleMs = scrollDebugLogThrottleMs;
		this.scrollPerfLogIntervalMs = scrollPerfLogIntervalMs;
		this.scrollSlowEventMs = scrollSlowEventMs;
	}

	public void reset() {
		scrollPerfWindowStartMs = 0L;
		scrollPerfEvents = 0;
		scrollPerfHandledEvents = 0;
		scrollPerfTotalNs = 0L;
		scrollPerfMaxMs = 0.0D;
		lastScrollDebugLogAtMs = 0L;
		suppressedScrollDebugLogs = 0;
	}

	public int normalizeScrollSteps(ScreenRuntimeHost host, double scrollDelta, String source, int maxScrollStepsPerEvent) {
		int rawSteps = Math.max(1, (int) Math.round(Math.abs(scrollDelta)));
		int normalizedSteps = Math.min(rawSteps, maxScrollStepsPerEvent);
		if (rawSteps != normalizedSteps) {
			logDebug(
					host,
					"[PROXC-SCROLL] client source={} clamped scroll steps raw={} clamped={} delta={}",
					source,
					rawSteps,
					normalizedSteps,
					scrollDelta
			);
		}
		return normalizedSteps;
	}

	public boolean finish(ScreenRuntimeHost host, String source, double scrollDelta, boolean handled, long startNs) {
		recordScrollPerf(host, source, scrollDelta, handled, startNs);
		return handled;
	}

	public void logDebug(ScreenRuntimeHost host, String pattern, Object... args) {
		if (!host.debugLoggingEnabled()) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		if ((nowMs - lastScrollDebugLogAtMs) < scrollDebugLogThrottleMs) {
			suppressedScrollDebugLogs++;
			return;
		}
		if (suppressedScrollDebugLogs > 0) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] throttled={} menu={}",
					suppressedScrollDebugLogs,
					host.containerId()
			);
			suppressedScrollDebugLogs = 0;
		}
		lastScrollDebugLogAtMs = nowMs;
		ProximityCrafting.LOGGER.info(pattern, args);
	}

	private void recordScrollPerf(ScreenRuntimeHost host, String source, double scrollDelta, boolean handled, long startNs) {
		if (!host.debugLoggingEnabled()) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		if (scrollPerfWindowStartMs == 0L) {
			scrollPerfWindowStartMs = nowMs;
		}
		double elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0D;
		scrollPerfEvents++;
		if (handled) {
			scrollPerfHandledEvents++;
		}
		scrollPerfTotalNs += (long) (elapsedMs * 1_000_000.0D);
		if (elapsedMs > scrollPerfMaxMs) {
			scrollPerfMaxMs = elapsedMs;
		}

		if (elapsedMs >= scrollSlowEventMs) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-CLIENT] scroll.slow menu={} source={} handled={} delta={} elapsedMs={} inFlight={} pendingFill={} pendingAdjust={} sourceSyncInFlight={}",
					host.containerId(),
					source,
					handled,
					scrollDelta,
					String.format("%.3f", elapsedMs),
					host.sessionState().isRecipeActionInFlight(),
					host.sessionState().getPendingFillRecipeId() == null ? "null" : host.sessionState().getPendingFillRecipeId(),
					host.sessionState().getPendingAdjustSteps(),
					host.sessionState().isSourceSyncInFlight()
			);
		}

		if ((nowMs - scrollPerfWindowStartMs) >= scrollPerfLogIntervalMs) {
			double avgMs = scrollPerfEvents == 0 ? 0.0D : (scrollPerfTotalNs / 1_000_000.0D) / scrollPerfEvents;
			ProximityCrafting.LOGGER.info(
					"[PROXC-CLIENT] scroll.window menu={} events={} handled={} avgMs={} maxMs={} inFlight={} pendingFill={} pendingAdjust={} sourceSyncInFlight={} sourceSyncQueued={} windowMs={}",
					host.containerId(),
					scrollPerfEvents,
					scrollPerfHandledEvents,
					String.format("%.3f", avgMs),
					String.format("%.3f", scrollPerfMaxMs),
					host.sessionState().isRecipeActionInFlight(),
					host.sessionState().getPendingFillRecipeId() == null ? "null" : host.sessionState().getPendingFillRecipeId(),
					host.sessionState().getPendingAdjustSteps(),
					host.sessionState().isSourceSyncInFlight(),
					host.sessionState().isSourceSyncQueued(),
					nowMs - scrollPerfWindowStartMs
			);
			scrollPerfWindowStartMs = nowMs;
			scrollPerfEvents = 0;
			scrollPerfHandledEvents = 0;
			scrollPerfTotalNs = 0L;
			scrollPerfMaxMs = 0.0D;
		}
	}
}

