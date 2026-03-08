package dev.maeiro.proximitycrafting.client.runtime;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.presenter.StatusMessagePresenter;
import dev.maeiro.proximitycrafting.client.session.ClientRecipeSessionState;
import dev.maeiro.proximitycrafting.client.session.RecipeActionFeedbackApplyResult;
import dev.maeiro.proximitycrafting.config.ClientPreferences;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class ScreenActionController {
	private final ClientRecipeSessionState sessionState;
	private final long actionQueueLogIntervalMs;
	private long lastActionQueueLogAtMs = 0L;

	public ScreenActionController(
			long sourceSyncMinIntervalMs,
			long actionSendIntervalMs,
			long actionInFlightTimeoutMs,
			int maxAbsAdjustSteps,
			long actionQueueLogIntervalMs
	) {
		this.sessionState = new ClientRecipeSessionState(
				sourceSyncMinIntervalMs,
				actionSendIntervalMs,
				actionInFlightTimeoutMs,
				maxAbsAdjustSteps
		);
		this.actionQueueLogIntervalMs = actionQueueLogIntervalMs;
	}

	public ClientRecipeSessionState sessionState() {
		return sessionState;
	}

	public void reset() {
		sessionState.reset();
		lastActionQueueLogAtMs = 0L;
	}

	public void sendClientPreferencesUpdate(ScreenRuntimeHost host, ClientPreferences preferences) {
		host.requestSender().updateClientPreferences(
				host.containerId(),
				preferences.autoRefillAfterCraft(),
				preferences.includePlayerInventory(),
				preferences.sourcePriorityValue()
		);
		if (!host.debugLoggingEnabled()) {
			return;
		}
		ProximityCrafting.LOGGER.info(
				"[PROXC-PERF] client.sendPreferences menu={} autoRefill={} includePlayer={} sourcePriority={}",
				host.containerId(),
				preferences.autoRefillAfterCraft(),
				preferences.includePlayerInventory(),
				preferences.sourcePriorityValue()
		);
	}

	public void sendClearGrid(ScreenRuntimeHost host, long nowMs, String source) {
		sessionState.clearQueuedActionsForImmediateRequest(nowMs);
		host.requestSender().clearCraftGrid(host.containerId());
		if (!host.debugLoggingEnabled()) {
			return;
		}
		ProximityCrafting.LOGGER.info(
				"[PROXC-PERF] client.sendClearGrid source={} menu={}",
				source,
				host.containerId()
		);
	}

	public void queueRecipeFill(
			ScreenRuntimeHost host,
			@Nullable ResourceLocation recipeId,
			boolean craftAll,
			long nowMs,
			String source,
			int maxAbsAdjustStepsPerPacket
	) {
		ClientRecipeSessionState.QueueRecipeFillResult result = sessionState.queueRecipeFill(recipeId, craftAll, nowMs);
		if (host.debugLoggingEnabled()) {
			switch (result.disposition()) {
				case DEDUPED_IN_FLIGHT -> ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] client.queueRecipeFill dedupe=in_flight source={} menu={} recipe={} craftAll={}",
						source,
						host.containerId(),
						recipeId,
						craftAll
				);
				case DEDUPED_PENDING -> ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] client.queueRecipeFill dedupe=pending source={} menu={} recipe={} craftAll={}",
						source,
						host.containerId(),
						recipeId,
						craftAll
				);
				default -> {
				}
			}
		}
		if (result.disposition() == ClientRecipeSessionState.QueueRecipeFillDisposition.IGNORED_NULL) {
			return;
		}
		processQueuedActions(host, nowMs, "queue_fill", maxAbsAdjustStepsPerPacket);
	}

	public void queueAdjust(
			ScreenRuntimeHost host,
			int steps,
			long nowMs,
			String source,
			int maxAbsAdjustStepsPerPacket
	) {
		if (steps == 0) {
			return;
		}
		ClientRecipeSessionState.QueueAdjustResult result = sessionState.queueAdjust(steps, nowMs);
		if (host.debugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] client.queueAdjust source={} menu={} incomingSteps={} pendingBefore={} pendingAfter={}",
					source,
					host.containerId(),
					steps,
					result.pendingBefore(),
					result.pendingAfter()
			);
		}
		processQueuedActions(host, nowMs, "queue_adjust", maxAbsAdjustStepsPerPacket);
	}

	public void processQueuedActions(
			ScreenRuntimeHost host,
			long nowMs,
			String reason,
			int maxAbsAdjustStepsPerPacket
	) {
		logActionQueueState(host, "enter:" + reason, nowMs);
		ClientRecipeSessionState.ActionDispatchResult dispatchResult = sessionState.dispatchNextAction(
				host.requestSender(),
				nowMs,
				maxAbsAdjustStepsPerPacket
		);
		if (dispatchResult.timedOutCleared()) {
			if (host.debugLoggingEnabled()) {
				ProximityCrafting.LOGGER.warn(
						"[PROXC-PERF] client.recipeAction timeout clearing in-flight menu={} inFlightFill={} inFlightAdjust={} ageMs={} reason={}",
						host.containerId(),
						sessionState.getInFlightFillRecipeId() == null ? "null" : sessionState.getInFlightFillRecipeId(),
						sessionState.getInFlightAdjustSteps(),
						nowMs - sessionState.getLastRecipeActionSentAtMs(),
						reason
				);
			}
			logActionQueueState(host, "timeout_clear:" + reason, nowMs);
		}
		switch (dispatchResult.disposition()) {
			case BLOCKED_IN_FLIGHT -> logActionQueueState(host, "blocked_in_flight:" + reason, nowMs);
			case SENT_FILL -> {
				if (host.debugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[PROXC-PERF] client.sendRecipeFill menu={} recipe={} craftAll={} reason={} pendingAdjustAfterSend={}",
							host.containerId(),
							dispatchResult.recipeId(),
							dispatchResult.craftAll(),
							reason,
							dispatchResult.pendingAdjustRemaining()
					);
				}
				logActionQueueState(host, "sent_fill:" + reason, nowMs);
			}
			case SENT_ADJUST -> {
				if (host.debugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info(
							"[PROXC-PERF] client.sendAdjust menu={} steps={} reason={} pendingRemaining={}",
							host.containerId(),
							dispatchResult.stepsSent(),
							reason,
							dispatchResult.pendingAdjustRemaining()
					);
				}
				logActionQueueState(host, "sent_adjust:" + reason, nowMs);
			}
			default -> {
			}
		}
	}

	public void handleRecipeActionFeedback(
			ScreenRuntimeHost host,
			RecipeFillFeedbackPayload payload,
			RecipeActionFeedbackApplyResult result,
			StatusMessagePresenter statusMessagePresenter,
			long nowMs
	) {
		statusMessagePresenter.showFeedback(payload, nowMs);
		if (!host.debugLoggingEnabled()) {
			return;
		}
		ProximityCrafting.LOGGER.info(
				"[PROXC-PERF] client.recipeActionFeedback menu={} success={} key={} amount={} inFlightFill={} inFlightAdjust={} pendingFill={} pendingAdjust={}",
				host.containerId(),
				payload.success(),
				payload.messageKey(),
				payload.craftedAmount(),
				result.clearedInFlightFillRecipeId() == null ? "null" : result.clearedInFlightFillRecipeId(),
				result.clearedInFlightAdjustSteps(),
				result.pendingFillRecipeId() == null ? "null" : result.pendingFillRecipeId(),
				result.pendingAdjustSteps()
		);
	}

	private void logActionQueueState(ScreenRuntimeHost host, String phase, long nowMs) {
		if (!host.debugLoggingEnabled()) {
			return;
		}
		if ((nowMs - lastActionQueueLogAtMs) < actionQueueLogIntervalMs) {
			return;
		}
		lastActionQueueLogAtMs = nowMs;
		ProximityCrafting.LOGGER.info(
				"[PROXC-CLIENT] action.queue phase={} menu={} inFlight={} inFlightFill={} inFlightAdjust={} pendingFill={} pendingAdjust={} sourceSyncInFlight={} sourceSyncQueued={}",
				phase,
				host.containerId(),
				sessionState.isRecipeActionInFlight(),
				sessionState.getInFlightFillRecipeId() == null ? "null" : sessionState.getInFlightFillRecipeId(),
				sessionState.getInFlightAdjustSteps(),
				sessionState.getPendingFillRecipeId() == null ? "null" : sessionState.getPendingFillRecipeId(),
				sessionState.getPendingAdjustSteps(),
				sessionState.isSourceSyncInFlight(),
				sessionState.isSourceSyncQueued()
		);
	}
}

