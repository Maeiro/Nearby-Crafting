package dev.maeiro.proximitycrafting.client.session;

import dev.maeiro.proximitycrafting.client.net.ClientRequestSender;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class ClientRecipeSessionState {
	private final long sourceSyncMinIntervalMs;
	private final long actionSendIntervalMs;
	private final long actionInFlightTimeoutMs;
	private final int maxAbsAdjustSteps;

	private long lastSourceSyncSentAtMs = 0L;
	private boolean sourceSyncInFlight = false;
	private boolean sourceSyncQueued = false;
	private long lastRecipeActionQueuedAtMs = 0L;
	private long lastRecipeActionSentAtMs = 0L;
	private boolean recipeActionInFlight = false;
	private long recipeActionInFlightStartedAtMs = 0L;
	private boolean deferredRecipeBookRefreshAfterAction = false;
	@Nullable
	private PendingFillRequest inFlightFillRequest;
	private int inFlightAdjustSteps = 0;
	@Nullable
	private PendingFillRequest pendingFillRequest;
	private int pendingAdjustSteps = 0;

	public ClientRecipeSessionState(
			long sourceSyncMinIntervalMs,
			long actionSendIntervalMs,
			long actionInFlightTimeoutMs,
			int maxAbsAdjustSteps
	) {
		this.sourceSyncMinIntervalMs = sourceSyncMinIntervalMs;
		this.actionSendIntervalMs = actionSendIntervalMs;
		this.actionInFlightTimeoutMs = actionInFlightTimeoutMs;
		this.maxAbsAdjustSteps = maxAbsAdjustSteps;
	}

	public SourceSyncRequestResult requestSourceSync(ClientRequestSender requestSender, int containerId, long nowMs) {
		if (sourceSyncInFlight) {
			sourceSyncQueued = true;
			return new SourceSyncRequestResult(SourceSyncDisposition.QUEUED_IN_FLIGHT, -1L);
		}

		long elapsedSinceLastSendMs = lastSourceSyncSentAtMs == 0L ? -1L : nowMs - lastSourceSyncSentAtMs;
		if (lastSourceSyncSentAtMs != 0L && elapsedSinceLastSendMs < sourceSyncMinIntervalMs) {
			sourceSyncQueued = true;
			return new SourceSyncRequestResult(SourceSyncDisposition.QUEUED_MIN_INTERVAL, elapsedSinceLastSendMs);
		}

		sourceSyncInFlight = true;
		sourceSyncQueued = false;
		lastSourceSyncSentAtMs = nowMs;
		requestSender.requestRecipeBookSources(containerId);
		return new SourceSyncRequestResult(SourceSyncDisposition.SENT, elapsedSinceLastSendMs);
	}

	public QueueRecipeFillResult queueRecipeFill(@Nullable ResourceLocation recipeId, boolean craftAll, long nowMs) {
		if (recipeId == null) {
			return new QueueRecipeFillResult(QueueRecipeFillDisposition.IGNORED_NULL);
		}

		lastRecipeActionQueuedAtMs = nowMs;
		if (inFlightFillRequest != null && inFlightFillRequest.matches(recipeId, craftAll)) {
			return new QueueRecipeFillResult(QueueRecipeFillDisposition.DEDUPED_IN_FLIGHT);
		}
		if (pendingFillRequest != null && pendingFillRequest.matches(recipeId, craftAll)) {
			return new QueueRecipeFillResult(QueueRecipeFillDisposition.DEDUPED_PENDING);
		}

		pendingFillRequest = new PendingFillRequest(recipeId, craftAll);
		return new QueueRecipeFillResult(QueueRecipeFillDisposition.QUEUED);
	}

	public QueueAdjustResult queueAdjust(int steps, long nowMs) {
		if (steps == 0) {
			return new QueueAdjustResult(0, pendingAdjustSteps, true);
		}

		lastRecipeActionQueuedAtMs = nowMs;
		int before = pendingAdjustSteps;
		long combined = (long) before + steps;
		if (combined > maxAbsAdjustSteps) {
			combined = maxAbsAdjustSteps;
		} else if (combined < -maxAbsAdjustSteps) {
			combined = -maxAbsAdjustSteps;
		}
		pendingAdjustSteps = (int) combined;
		return new QueueAdjustResult(before, pendingAdjustSteps, false);
	}

	public void clearQueuedActionsForImmediateRequest(long nowMs) {
		lastRecipeActionQueuedAtMs = nowMs;
		pendingFillRequest = null;
		pendingAdjustSteps = 0;
	}

	public ActionDispatchResult dispatchNextAction(ClientRequestSender requestSender, long nowMs, int maxAbsAdjustStepsPerPacket) {
		boolean timedOutCleared = false;
		if (recipeActionInFlight && (nowMs - recipeActionInFlightStartedAtMs) >= actionInFlightTimeoutMs) {
			clearRecipeActionInFlight();
			timedOutCleared = true;
		}

		if (recipeActionInFlight) {
			return new ActionDispatchResult(ActionDispatchDisposition.BLOCKED_IN_FLIGHT, null, false, 0, pendingAdjustSteps, timedOutCleared);
		}
		if (pendingFillRequest == null && pendingAdjustSteps == 0) {
			return new ActionDispatchResult(ActionDispatchDisposition.NONE, null, false, 0, pendingAdjustSteps, timedOutCleared);
		}
		if (lastRecipeActionSentAtMs != 0L && (nowMs - lastRecipeActionSentAtMs) < actionSendIntervalMs) {
			return new ActionDispatchResult(ActionDispatchDisposition.RATE_LIMITED, null, false, 0, pendingAdjustSteps, timedOutCleared);
		}

		if (pendingFillRequest != null) {
			PendingFillRequest fillRequest = pendingFillRequest;
			pendingFillRequest = null;
			requestSender.requestRecipeFill(fillRequest.recipeId(), fillRequest.craftAll());
			markRecipeActionInFlight(fillRequest, 0, nowMs);
			return new ActionDispatchResult(
					ActionDispatchDisposition.SENT_FILL,
					fillRequest.recipeId(),
					fillRequest.craftAll(),
					0,
					pendingAdjustSteps,
					timedOutCleared
			);
		}

		int direction = pendingAdjustSteps > 0 ? 1 : -1;
		int chunk = Math.min(Math.abs(pendingAdjustSteps), maxAbsAdjustStepsPerPacket);
		int stepsToSend = chunk * direction;
		pendingAdjustSteps -= stepsToSend;
		requestSender.adjustRecipeLoad(stepsToSend);
		markRecipeActionInFlight(null, stepsToSend, nowMs);
		return new ActionDispatchResult(
				ActionDispatchDisposition.SENT_ADJUST,
				null,
				false,
				stepsToSend,
				pendingAdjustSteps,
				timedOutCleared
		);
	}

	public boolean shouldDeferPeriodicSourceSync(long nowMs, long actionCooldownMs) {
		if (isActionBusy()) {
			return true;
		}
		if (lastRecipeActionQueuedAtMs == 0L) {
			return false;
		}
		return (nowMs - lastRecipeActionQueuedAtMs) < actionCooldownMs;
	}

	public SourceSnapshotApplyResult applySourceSnapshot(long nowMs, boolean sourcesChanged) {
		boolean hadSourceSyncInFlight = sourceSyncInFlight;
		boolean actionBusy = isActionBusy();
		sourceSyncInFlight = false;
		boolean shouldRequestQueuedSyncNow = sourceSyncQueued;
		sourceSyncQueued = false;
		long sourceSyncRttMs = (hadSourceSyncInFlight && lastSourceSyncSentAtMs != 0L)
				? (nowMs - lastSourceSyncSentAtMs)
				: -1L;
		long actionSnapshotApplyDelayMs = (!hadSourceSyncInFlight && lastRecipeActionSentAtMs != 0L)
				? (nowMs - lastRecipeActionSentAtMs)
				: -1L;
		return new SourceSnapshotApplyResult(
				hadSourceSyncInFlight,
				actionBusy,
				shouldRequestQueuedSyncNow,
				sourceSyncRttMs,
				actionSnapshotApplyDelayMs
		);
	}

	public RecipeActionFeedbackApplyResult applyRecipeActionFeedback() {
		boolean hadInFlightAction = recipeActionInFlight || inFlightFillRequest != null || inFlightAdjustSteps != 0;
		ResourceLocation clearedFillRecipeId = inFlightFillRequest == null ? null : inFlightFillRequest.recipeId();
		int clearedAdjustSteps = inFlightAdjustSteps;
		clearRecipeActionInFlight();
		return new RecipeActionFeedbackApplyResult(
				hadInFlightAction,
				clearedFillRecipeId,
				clearedAdjustSteps,
				getPendingFillRecipeId(),
				pendingAdjustSteps
		);
	}

	public void markDeferredRecipeBookRefreshAfterAction() {
		deferredRecipeBookRefreshAfterAction = true;
	}

	public void clearDeferredRecipeBookRefreshAfterAction() {
		deferredRecipeBookRefreshAfterAction = false;
	}

	public boolean shouldFlushDeferredRecipeBookRefresh() {
		return deferredRecipeBookRefreshAfterAction && !isActionBusy();
	}

	public void consumeDeferredRecipeBookRefresh() {
		deferredRecipeBookRefreshAfterAction = false;
	}

	public void reset() {
		sourceSyncInFlight = false;
		sourceSyncQueued = false;
		lastSourceSyncSentAtMs = 0L;
		lastRecipeActionQueuedAtMs = 0L;
		lastRecipeActionSentAtMs = 0L;
		recipeActionInFlight = false;
		recipeActionInFlightStartedAtMs = 0L;
		deferredRecipeBookRefreshAfterAction = false;
		inFlightFillRequest = null;
		inFlightAdjustSteps = 0;
		pendingFillRequest = null;
		pendingAdjustSteps = 0;
	}

	public boolean isSourceSyncInFlight() {
		return sourceSyncInFlight;
	}

	public boolean isSourceSyncQueued() {
		return sourceSyncQueued;
	}

	public boolean isRecipeActionInFlight() {
		return recipeActionInFlight;
	}

	public boolean hasPendingRecipeActions() {
		return pendingFillRequest != null || pendingAdjustSteps != 0;
	}

	public boolean isActionBusy() {
		return recipeActionInFlight || pendingFillRequest != null || pendingAdjustSteps != 0;
	}

	@Nullable
	public ResourceLocation getInFlightFillRecipeId() {
		return inFlightFillRequest == null ? null : inFlightFillRequest.recipeId();
	}

	public int getInFlightAdjustSteps() {
		return inFlightAdjustSteps;
	}

	@Nullable
	public ResourceLocation getPendingFillRecipeId() {
		return pendingFillRequest == null ? null : pendingFillRequest.recipeId();
	}

	public int getPendingAdjustSteps() {
		return pendingAdjustSteps;
	}

	public long getLastRecipeActionSentAtMs() {
		return lastRecipeActionSentAtMs;
	}

	private void markRecipeActionInFlight(@Nullable PendingFillRequest fillRequest, int adjustSteps, long nowMs) {
		recipeActionInFlight = true;
		recipeActionInFlightStartedAtMs = nowMs;
		lastRecipeActionSentAtMs = nowMs;
		inFlightFillRequest = fillRequest;
		inFlightAdjustSteps = adjustSteps;
	}

	private void clearRecipeActionInFlight() {
		recipeActionInFlight = false;
		recipeActionInFlightStartedAtMs = 0L;
		inFlightFillRequest = null;
		inFlightAdjustSteps = 0;
	}

	private record PendingFillRequest(ResourceLocation recipeId, boolean craftAll) {
		private boolean matches(ResourceLocation otherRecipeId, boolean otherCraftAll) {
			return craftAll == otherCraftAll && recipeId.equals(otherRecipeId);
		}
	}

	public enum SourceSyncDisposition {
		SENT,
		QUEUED_IN_FLIGHT,
		QUEUED_MIN_INTERVAL
	}

	public record SourceSyncRequestResult(SourceSyncDisposition disposition, long elapsedSinceLastSendMs) {
	}

	public enum QueueRecipeFillDisposition {
		QUEUED,
		DEDUPED_IN_FLIGHT,
		DEDUPED_PENDING,
		IGNORED_NULL
	}

	public record QueueRecipeFillResult(QueueRecipeFillDisposition disposition) {
	}

	public record QueueAdjustResult(int pendingBefore, int pendingAfter, boolean ignored) {
	}

	public enum ActionDispatchDisposition {
		NONE,
		BLOCKED_IN_FLIGHT,
		RATE_LIMITED,
		SENT_FILL,
		SENT_ADJUST
	}

	public record ActionDispatchResult(
			ActionDispatchDisposition disposition,
			@Nullable ResourceLocation recipeId,
			boolean craftAll,
			int stepsSent,
			int pendingAdjustRemaining,
			boolean timedOutCleared
	) {
	}
}

