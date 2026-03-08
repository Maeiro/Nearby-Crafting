package dev.maeiro.proximitycrafting.client.session;

public record SourceSnapshotApplyResult(
		boolean hadSourceSyncInFlight,
		boolean actionBusy,
		boolean shouldRequestQueuedSyncNow,
		long sourceSyncRttMs,
		long actionSnapshotApplyDelayMs
) {
}
