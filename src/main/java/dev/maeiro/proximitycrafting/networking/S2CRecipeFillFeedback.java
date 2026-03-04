package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CRecipeFillFeedback {
	private final boolean success;
	private final String messageKey;
	private final int craftedAmount;

	public S2CRecipeFillFeedback(boolean success, String messageKey, int craftedAmount) {
		this.success = success;
		this.messageKey = messageKey;
		this.craftedAmount = craftedAmount;
	}

	public S2CRecipeFillFeedback(FriendlyByteBuf buf) {
		this.success = buf.readBoolean();
		this.messageKey = buf.readUtf();
		this.craftedAmount = buf.readInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBoolean(success);
		buf.writeUtf(messageKey);
		buf.writeInt(craftedAmount);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			long startNs = System.nanoTime();
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null) {
				return;
			}

			Component feedback = craftedAmount > 0
					? Component.translatable(messageKey, craftedAmount)
					: Component.translatable(messageKey);

			if (minecraft.screen instanceof ProximityCraftingScreen proximityCraftingScreen) {
				if (success) {
					proximityCraftingScreen.showSuccessStatusMessage(feedback);
				} else {
					proximityCraftingScreen.showFailureStatusMessage(feedback);
				}
				// Snapshot sync is already sent by fill/adjust packets; avoid redundant client->server sync bursts.
				proximityCraftingScreen.scheduleDeferredRecipeBookRefresh();
			} else {
				minecraft.player.displayClientMessage(feedback, true);
			}
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] packet.S2CRecipeFillFeedback success={} key={} amount={} applyMs={}",
						success,
						messageKey,
						craftedAmount,
						String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
				);
			}
		});
		ctx.setPacketHandled(true);
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
	}
}


