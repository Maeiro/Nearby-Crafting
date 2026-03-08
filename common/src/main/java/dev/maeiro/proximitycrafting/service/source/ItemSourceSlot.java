package dev.maeiro.proximitycrafting.service.source;

import net.minecraft.world.item.ItemStack;

public interface ItemSourceSlot {
	ItemStack peekStack();

	ItemStack extract(int amount, boolean simulate);

	ItemStack insert(ItemStack stack, boolean simulate);

	SlotIdentity identity();
}
