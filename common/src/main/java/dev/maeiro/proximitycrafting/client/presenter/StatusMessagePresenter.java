package dev.maeiro.proximitycrafting.client.presenter;

import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;
import net.minecraft.network.chat.Component;

public final class StatusMessagePresenter {
	public static final int STATUS_COLOR_SUCCESS = 0x55FF55;
	public static final int STATUS_COLOR_FAILURE = 0xFF5555;
	public static final int STATUS_COLOR_INFO = 0xFFFFFF;

	private StatusMessageView currentMessage;

	public void showInfo(Component message, long nowMs) {
		show(message, STATUS_COLOR_INFO, 1400, nowMs);
	}

	public void showSuccess(Component message, long nowMs) {
		show(message, STATUS_COLOR_SUCCESS, 1600, nowMs);
	}

	public void showFailure(Component message, long nowMs) {
		show(message, STATUS_COLOR_FAILURE, 1700, nowMs);
	}

	public void showFeedback(RecipeFillFeedbackPayload payload, long nowMs) {
		Component message = payload.craftedAmount() > 0
				? Component.translatable(payload.messageKey(), payload.craftedAmount())
				: Component.translatable(payload.messageKey());
		if (payload.success()) {
			showSuccess(message, nowMs);
		} else {
			showFailure(message, nowMs);
		}
	}

	public StatusMessageView current(long nowMs) {
		if (currentMessage == null) {
			return null;
		}
		if (nowMs > currentMessage.expiresAtMs()) {
			currentMessage = null;
			return null;
		}
		return currentMessage;
	}

	private void show(Component message, int color, int durationMs, long nowMs) {
		currentMessage = new StatusMessageView(message, color, nowMs + Math.max(250, durationMs));
	}
}
