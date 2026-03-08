package dev.maeiro.proximitycrafting.networking.payload;

import net.minecraft.network.FriendlyByteBuf;

public record RecipeFillFeedbackPayload(boolean success, String messageKey, int craftedAmount) {
	public static RecipeFillFeedbackPayload decode(FriendlyByteBuf buf) {
		return new RecipeFillFeedbackPayload(
				buf.readBoolean(),
				buf.readUtf(),
				buf.readInt()
		);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBoolean(success);
		buf.writeUtf(messageKey);
		buf.writeInt(craftedAmount);
	}
}
