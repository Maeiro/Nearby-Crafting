package dev.maeiro.proximitycrafting.service.source;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public class ForgeItemSourceSlot implements ItemSourceSlot {
	private final IItemHandler handler;
	private final int slot;
	private final SlotIdentity identity;

	public ForgeItemSourceSlot(IItemHandler handler, int slot) {
		this.handler = handler;
		this.slot = slot;
		this.identity = SlotIdentity.of(handler, slot);
	}

	@Override
	public ItemStack peekStack() {
		return handler.getStackInSlot(slot);
	}

	@Override
	public ItemStack extract(int amount, boolean simulate) {
		return handler.extractItem(slot, amount, simulate);
	}

	@Override
	public ItemStack insert(ItemStack stack, boolean simulate) {
		return handler.insertItem(slot, stack, simulate);
	}

	@Override
	public SlotIdentity identity() {
		return identity;
	}
}
