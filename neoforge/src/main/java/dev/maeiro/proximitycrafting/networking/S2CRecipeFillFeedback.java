package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.net.ProximityClientServices;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
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
			Component feedback = payload.craftedAmount() > 0
					? Component.translatable(payload.messageKey(), payload.craftedAmount())
					: Component.translatable(payload.messageKey());

			boolean handled = ProximityClientServices.getClientResponseDispatcher().handleRecipeFillFeedback(payload);
			if (!handled && minecraft.player != null) {
				minecraft.player.displayClientMessage(feedback, true);
			}
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] packet.S2CRecipeFillFeedback success={} key={} amount={} handled={} applyMs={}",
						payload.success(),
						payload.messageKey(),
						payload.craftedAmount(),
						handled,
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
