package dev.maeiro.proximitycrafting.networking.request;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ClientPreferences;
import dev.maeiro.proximitycrafting.networking.payload.AdjustRecipeLoadRequestPayload;
import dev.maeiro.proximitycrafting.networking.payload.ClearCraftGridRequestPayload;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourcesRequestPayload;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillRequestPayload;
import dev.maeiro.proximitycrafting.networking.payload.UpdateClientPreferencesRequestPayload;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class ServerMenuRequestController {
	private static final int MAX_ABS_STEPS_PER_PACKET = 2;

	public void handleRecipeFill(
			ServerPlayer player,
			ServerMenuRequestHost host,
			ServerResponseTransport transport,
			RecipeFillRequestPayload payload
	) {
		long startNs = System.nanoTime();
		long fillStartNs = System.nanoTime();
		FillResult result = host.fillRecipeById(payload.recipeId(), payload.craftAll());
		long fillEndNs = System.nanoTime();
		boolean shouldSendSnapshot = result.success() && result.craftedAmount() > 0;
		long snapshotStartNs = 0L;
		long snapshotEndNs = 0L;
		int snapshotEntryCount = 0;

		transport.sendRecipeFillFeedback(
				player,
				new RecipeFillFeedbackPayload(result.success(), result.messageKey(), result.craftedAmount())
		);

		if (shouldSendSnapshot) {
			snapshotStartNs = System.nanoTime();
			List<RecipeBookSourceEntry> snapshotEntries = host.getServerRecipeBookSnapshot(false, "packet_fill_result");
			snapshotEndNs = System.nanoTime();
			snapshotEntryCount = snapshotEntries.size();
			transport.sendRecipeBookSourceSnapshot(player, host.containerId(), snapshotEntries);
		}

		if (host.debugLoggingEnabled()) {
			double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] packet.C2SRequestRecipeFill player={} menu={} recipe={} craftAll={} success={} amount={} fillMs={} snapshotMs={} totalMs={} snapshotEntries={}",
					player.getGameProfile().getName(),
					host.containerId(),
					payload.recipeId(),
					payload.craftAll(),
					result.success(),
					result.craftedAmount(),
					String.format("%.3f", (fillEndNs - fillStartNs) / 1_000_000.0D),
					String.format("%.3f", shouldSendSnapshot ? (snapshotEndNs - snapshotStartNs) / 1_000_000.0D : 0.0D),
					String.format("%.3f", totalMs),
					snapshotEntryCount
			);
		}
	}

	public void handleAdjustRecipeLoad(
			ServerPlayer player,
			ServerMenuRequestHost host,
			ServerResponseTransport transport,
			AdjustRecipeLoadRequestPayload payload
	) {
		long startNs = System.nanoTime();
		int requestedSteps = payload.steps();
		if (host.debugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] server received adjust packet steps={} menu={} player={}",
					requestedSteps,
					host.containerId(),
					player.getGameProfile().getName()
			);
		}

		int effectiveSteps = Math.max(-MAX_ABS_STEPS_PER_PACKET, Math.min(MAX_ABS_STEPS_PER_PACKET, requestedSteps));
		if (effectiveSteps == 0) {
			if (host.debugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-SCROLL] server ignored adjust packet after clamp steps={} menu={} player={}",
						requestedSteps,
						host.containerId(),
						player.getGameProfile().getName()
				);
			}
			return;
		}
		if (effectiveSteps != requestedSteps && host.debugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] server clamped adjust steps requested={} effective={} menu={} player={}",
					requestedSteps,
					effectiveSteps,
					host.containerId(),
					player.getGameProfile().getName()
			);
		}

		long adjustStartNs = System.nanoTime();
		FillResult result = host.adjustRecipeLoad(effectiveSteps);
		long adjustEndNs = System.nanoTime();
		boolean shouldSendSnapshot = result.success() && result.craftedAmount() > 0 && host.shouldSendSnapshotForAdjust();
		long snapshotStartNs = 0L;
		long snapshotEndNs = 0L;
		int snapshotEntryCount = 0;

		if (host.debugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] server adjust result success={} key={} amount={}",
					result.success(),
					result.messageKey(),
					result.craftedAmount()
			);
		}

		transport.sendRecipeFillFeedback(
				player,
				new RecipeFillFeedbackPayload(result.success(), result.messageKey(), result.craftedAmount())
		);

		if (shouldSendSnapshot) {
			snapshotStartNs = System.nanoTime();
			List<RecipeBookSourceEntry> snapshotEntries = host.getServerRecipeBookSnapshot(false, "packet_adjust_result");
			snapshotEndNs = System.nanoTime();
			snapshotEntryCount = snapshotEntries.size();
			transport.sendRecipeBookSourceSnapshot(player, host.containerId(), snapshotEntries);
		}

		if (host.debugLoggingEnabled()) {
			double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] packet.C2SAdjustRecipeLoad player={} menu={} requestedSteps={} effectiveSteps={} success={} amount={} adjustMs={} snapshotMs={} totalMs={} snapshotEntries={}",
					player.getGameProfile().getName(),
					host.containerId(),
					requestedSteps,
					effectiveSteps,
					result.success(),
					result.craftedAmount(),
					String.format("%.3f", (adjustEndNs - adjustStartNs) / 1_000_000.0D),
					String.format("%.3f", shouldSendSnapshot ? (snapshotEndNs - snapshotStartNs) / 1_000_000.0D : 0.0D),
					String.format("%.3f", totalMs),
					snapshotEntryCount
			);
		}
	}

	public void handleClearCraftGrid(
			ServerPlayer player,
			ServerMenuRequestHost host,
			ServerResponseTransport transport,
			ClearCraftGridRequestPayload payload
	) {
		if (host.containerId() != payload.containerId()) {
			return;
		}

		long startNs = System.nanoTime();
		boolean hadItems = host.hasAnyCraftGridItems();
		long clearStartNs = 0L;
		long clearEndNs = 0L;

		if (hadItems) {
			clearStartNs = System.nanoTime();
			host.clearCraftGridToPlayerOrDrop();
			host.invalidateServerRecipeBookSnapshotCache();
			clearEndNs = System.nanoTime();
		}

		transport.sendRecipeFillFeedback(
				player,
				new RecipeFillFeedbackPayload(
						true,
						hadItems ? "proximitycrafting.feedback.grid_cleared" : "proximitycrafting.feedback.grid_already_empty",
						0
				)
		);

		if (hadItems) {
			List<RecipeBookSourceEntry> snapshotEntries = host.getServerRecipeBookSnapshot(false, "packet_clear_grid");
			transport.sendRecipeBookSourceSnapshot(player, payload.containerId(), snapshotEntries);
		}

		if (host.debugLoggingEnabled()) {
			double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
			double clearMs = hadItems ? (clearEndNs - clearStartNs) / 1_000_000.0D : 0.0D;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] packet.C2SClearCraftGrid player={} menu={} hadItems={} clearMs={} totalMs={}",
					player.getGameProfile().getName(),
					host.containerId(),
					hadItems,
					String.format("%.3f", clearMs),
					String.format("%.3f", totalMs)
			);
		}
	}

	public void handleRequestRecipeBookSources(
			ServerPlayer player,
			ServerMenuRequestHost host,
			ServerResponseTransport transport,
			RecipeBookSourcesRequestPayload payload
	) {
		if (host.containerId() != payload.containerId()) {
			return;
		}

		long startNs = System.nanoTime();
		List<RecipeBookSourceEntry> entries = host.getServerRecipeBookSnapshot(true, "packet_request_sources");
		transport.sendRecipeBookSourceSnapshot(player, payload.containerId(), entries);

		if (host.debugLoggingEnabled()) {
			double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] packet.C2SRequestRecipeBookSources player={} menu={} snapshotEntries={} took={}ms",
					player.getGameProfile().getName(),
					host.containerId(),
					entries.size(),
					String.format("%.3f", totalMs)
			);
		}
	}

	public void handleUpdateClientPreferences(
			ServerMenuRequestHost host,
			UpdateClientPreferencesRequestPayload payload
	) {
		if (host.containerId() != payload.containerId()) {
			return;
		}
		ClientPreferences preferences = ClientPreferences.fromConfigValues(
				payload.autoRefillAfterCraft(),
				payload.includePlayerInventory(),
				payload.sourcePriority()
		);
		host.setClientPreferences(preferences);
	}
}
