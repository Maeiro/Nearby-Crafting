package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillFeedbackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CRecipeFillFeedback {
	private final RecipeFillFeedbackPayload payload;

	public S2CRecipeFillFeedback(boolean success, String messageKey, int craftedAmount) {
		this(new RecipeFillFeedbackPayload(success, messageKey, craftedAmount));
	}

	public S2CRecipeFillFeedback(FriendlyByteBuf buf) {
		this(RecipeFillFeedbackPayload.decode(buf));
	}

	public S2CRecipeFillFeedback(RecipeFillFeedbackPayload payload) {
		this.payload = payload;
	}

	public void encode(FriendlyByteBuf buf) {
		payload.encode(buf);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			long startNs = System.nanoTime();
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null) {
				return;
			}

			Component feedback = payload.craftedAmount() > 0
					? Component.translatable(payload.messageKey(), payload.craftedAmount())
					: Component.translatable(payload.messageKey());

			if (minecraft.screen instanceof ProximityCraftingScreen proximityCraftingScreen) {
				if (payload.success()) {
					proximityCraftingScreen.showSuccessStatusMessage(feedback);
				} else {
					proximityCraftingScreen.showFailureStatusMessage(feedback);
				}
				proximityCraftingScreen.onRecipeActionFeedbackReceived(payload.success(), payload.messageKey(), payload.craftedAmount());
			} else {
				minecraft.player.displayClientMessage(feedback, true);
			}
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] packet.S2CRecipeFillFeedback success={} key={} amount={} applyMs={}",
						payload.success(),
						payload.messageKey(),
						payload.craftedAmount(),
						String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
				);
			}
		});
		ctx.setPacketHandled(true);
	}

	private static boolean isDebugLoggingEnabled() {
		return ProximityCraftingConfig.isClientDebugLoggingEnabled();
	}
}
