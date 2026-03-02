package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.client.screen.NearbyCraftingScreen;
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
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null) {
				return;
			}

			Component feedback = craftedAmount > 0
					? Component.translatable(messageKey, craftedAmount)
					: Component.translatable(messageKey);
			minecraft.player.displayClientMessage(feedback, true);

			if (minecraft.screen instanceof NearbyCraftingScreen nearbyCraftingScreen) {
				nearbyCraftingScreen.requestImmediateSourceSyncAndRefresh();
			}
		});
		ctx.setPacketHandled(true);
	}
}
