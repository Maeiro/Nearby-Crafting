package dev.maeiro.proximitycrafting.networking.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record RecipeBookSourceEntry(ItemStack stack, int count) {
	public RecipeBookSourceEntry {
		stack = stack == null ? ItemStack.EMPTY : stack.copy();
	}

	public static RecipeBookSourceEntry decode(FriendlyByteBuf buf) {
		ItemStack stack = buf.readItem();
		int count = buf.readVarInt();
		return new RecipeBookSourceEntry(stack, count);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeItem(stack);
		buf.writeVarInt(count);
	}
}
