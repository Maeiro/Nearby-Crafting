package dev.maeiro.proximitycrafting.menu;

import dev.maeiro.proximitycrafting.service.crafting.TrackedCraftGridSession;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;

public class SourceTrackingCraftingContainer extends TransientCraftingContainer {
	private final TrackedCraftGridSession trackedCraftGridSession;

	public SourceTrackingCraftingContainer(
			AbstractContainerMenu menu,
			TrackedCraftGridSession trackedCraftGridSession,
			int width,
			int height
	) {
		super(menu, width, height);
		this.trackedCraftGridSession = trackedCraftGridSession;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = super.removeItem(slot, amount);
		trackedCraftGridSession.onContainerRemove(slot, removed.getCount());
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack removed = super.removeItemNoUpdate(slot);
		trackedCraftGridSession.onContainerRemove(slot, removed.getCount());
		return removed;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		super.setItem(slot, stack);
		trackedCraftGridSession.onContainerSet(slot);
	}

	@Override
	public void clearContent() {
		super.clearContent();
		trackedCraftGridSession.onContainerCleared();
	}
}
