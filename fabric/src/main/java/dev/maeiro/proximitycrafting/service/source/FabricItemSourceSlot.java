package dev.maeiro.proximitycrafting.service.source;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class FabricItemSourceSlot implements ItemSourceSlot {
	private final Container container;
	private final int slot;
	private final SlotIdentity identity;

	public FabricItemSourceSlot(Container container, int slot) {
		this.container = container;
		this.slot = slot;
		this.identity = SlotIdentity.of(container, slot);
	}

	@Override
	public ItemStack peekStack() {
		return container.getItem(slot);
	}

	@Override
	public ItemStack extract(int amount, boolean simulate) {
		ItemStack current = container.getItem(slot);
		if (current.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}

		int extractedCount = Math.min(amount, current.getCount());
		ItemStack extracted = current.copy();
		extracted.setCount(extractedCount);
		if (!simulate) {
			container.removeItem(slot, extractedCount);
			container.setChanged();
		}
		return extracted;
	}

	@Override
	public ItemStack insert(ItemStack stack, boolean simulate) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack current = container.getItem(slot);
		if (current.isEmpty()) {
			int accepted = Math.min(Math.min(container.getMaxStackSize(), stack.getMaxStackSize()), stack.getCount());
			if (!simulate) {
				ItemStack inserted = stack.copy();
				inserted.setCount(accepted);
				container.setItem(slot, inserted);
				container.setChanged();
			}
			ItemStack remainder = stack.copy();
			remainder.shrink(accepted);
			return remainder;
		}

		if (!ItemStack.isSameItemSameTags(current, stack)) {
			return stack.copy();
		}

		int maxStackSize = Math.min(container.getMaxStackSize(), current.getMaxStackSize());
		int space = maxStackSize - current.getCount();
		if (space <= 0) {
			return stack.copy();
		}

		int accepted = Math.min(space, stack.getCount());
		if (!simulate) {
			ItemStack updated = current.copy();
			updated.grow(accepted);
			container.setItem(slot, updated);
			container.setChanged();
		}
		ItemStack remainder = stack.copy();
		remainder.shrink(accepted);
		return remainder;
	}

	@Override
	public SlotIdentity identity() {
		return identity;
	}
}
