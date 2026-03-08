package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TrackedCraftGridSession {
	private final Map<ItemSourceRef, Integer>[] craftSlotSourceLedger;
	private boolean sourceTrackingMutationActive;
	private int suppressCraftSlotChangedDepth;
	private boolean craftSlotChangesPending;

	public TrackedCraftGridSession(int craftGridSize) {
		this.craftSlotSourceLedger = createSourceLedger(craftGridSize);
	}

	public ClearResult clearCraftGridToPlayerOrDrop(TrackedCraftGridPort port) {
		int clearedSlots = 0;
		int returnedToSources = 0;
		int returnedToInventory = 0;
		int droppedItems = 0;
		boolean anySlotChanged = false;

		beginBulkMutation();
		try {
			for (int slot = 0; slot < port.getCraftGridSize(); slot++) {
				ItemStack stack = port.getCraftGridItem(slot);
				Map<ItemSourceRef, Integer> sourceAllocations = craftSlotSourceLedger[slot];
				if (stack.isEmpty()) {
					if (!sourceAllocations.isEmpty()) {
						clearCraftSlotSource(slot);
					}
					continue;
				}
				clearedSlots++;

				ItemStack remaining = stack.copy();
				if (!sourceAllocations.isEmpty()) {
					returnedToSources += returnStackToTrackedSources(sourceAllocations, remaining);
				}

				int remainingBeforeInventory = remaining.getCount();
				port.getPlayer().getInventory().add(remaining);
				returnedToInventory += Math.max(0, remainingBeforeInventory - remaining.getCount());
				if (!remaining.isEmpty()) {
					droppedItems += remaining.getCount();
					port.getPlayer().drop(remaining, false);
				}

				int slotIndex = slot;
				runWithSourceTrackingMutation(() -> port.setCraftGridItem(slotIndex, ItemStack.EMPTY));
				clearCraftSlotSource(slotIndex);
				anySlotChanged = true;
			}
		} finally {
			endBulkMutation(port, true);
		}

		if (anySlotChanged) {
			port.markCraftGridChanged();
		}
		return new ClearResult(clearedSlots, returnedToSources, returnedToInventory, droppedItems, anySlotChanged);
	}

	public void setCraftSlotFromSource(TrackedCraftGridPort port, int slot, ItemStack stack, ItemSourceRef sourceRef) {
		ItemStack storedStack = stack.copy();
		runWithSourceTrackingMutation(() -> port.setCraftGridItem(slot, storedStack));
		clearCraftSlotSource(slot);
		if (!storedStack.isEmpty() && sourceRef != null) {
			addCraftSlotSource(slot, sourceRef, storedStack.getCount());
		}
	}

	public boolean addCraftSlotFromSource(TrackedCraftGridPort port, int slot, ItemStack stack, ItemSourceRef sourceRef) {
		if (stack.isEmpty()) {
			return true;
		}
		if (!canAcceptCraftSlotStack(port, slot, stack)) {
			return false;
		}

		ItemStack current = port.getCraftGridItem(slot);
		ItemStack updated = current.isEmpty() ? stack.copy() : current.copy();
		if (!current.isEmpty()) {
			updated.grow(stack.getCount());
		}

		runWithSourceTrackingMutation(() -> port.setCraftGridItem(slot, updated));
		if (sourceRef != null) {
			addCraftSlotSource(slot, sourceRef, stack.getCount());
		}
		return true;
	}

	public RemoveResult removeFromCraftSlotToSources(TrackedCraftGridPort port, int slot, int count) {
		if (slot < 0 || slot >= port.getCraftGridSize() || count <= 0) {
			return RemoveResult.notChanged();
		}

		ItemStack current = port.getCraftGridItem(slot);
		if (current.isEmpty()) {
			return RemoveResult.notChanged();
		}

		int amountToRemove = Math.min(count, current.getCount());
		if (amountToRemove <= 0) {
			return RemoveResult.notChanged();
		}

		ItemStack removed = current.copy();
		removed.setCount(amountToRemove);
		Map<ItemSourceRef, Integer> sourceAllocations = craftSlotSourceLedger[slot];
		boolean hadTrackedSources = !sourceAllocations.isEmpty();
		if (hadTrackedSources) {
			returnStackToSourcesOrPlayer(port, slot, removed);
		} else {
			port.getPlayer().getInventory().add(removed);
			if (!removed.isEmpty()) {
				port.getPlayer().drop(removed, false);
			}
		}

		ItemStack updated = current.copy();
		updated.shrink(amountToRemove);
		runWithSourceTrackingMutation(() -> port.setCraftGridItem(slot, updated.isEmpty() ? ItemStack.EMPTY : updated));
		if (hadTrackedSources) {
			consumeCraftSlotSource(slot, amountToRemove);
		}
		port.markCraftGridChanged();
		return new RemoveResult(true, amountToRemove, hadTrackedSources);
	}

	public boolean canAcceptCraftSlotStack(TrackedCraftGridPort port, int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		ItemStack current = port.getCraftGridItem(slot);
		if (current.isEmpty()) {
			return stack.getCount() <= getSlotStackLimit(port, stack);
		}
		if (!ItemStack.isSameItemSameTags(current, stack)) {
			return false;
		}
		return current.getCount() + stack.getCount() <= getSlotStackLimit(port, current);
	}

	public boolean onCraftGridChangedDeferred() {
		if (suppressCraftSlotChangedDepth > 0) {
			craftSlotChangesPending = true;
			return true;
		}
		return false;
	}

	public void beginBulkMutation() {
		suppressCraftSlotChangedDepth++;
	}

	public void endBulkMutation(TrackedCraftGridPort port) {
		endBulkMutation(port, true);
	}

	public void endBulkMutation(TrackedCraftGridPort port, boolean flushIfPending) {
		if (suppressCraftSlotChangedDepth <= 0) {
			return;
		}
		suppressCraftSlotChangedDepth--;
		if (flushIfPending && suppressCraftSlotChangedDepth == 0 && craftSlotChangesPending) {
			craftSlotChangesPending = false;
			port.flushCraftingGridChange();
		}
	}

	public void clearPendingCraftSlotChanges() {
		craftSlotChangesPending = false;
	}

	public boolean isSourceTrackingMutationActive() {
		return sourceTrackingMutationActive;
	}

	public void onContainerRemove(int slot, int count) {
		if (count <= 0 || sourceTrackingMutationActive) {
			return;
		}
		consumeCraftSlotSource(slot, count);
	}

	public void onContainerSet(int slot) {
		if (!sourceTrackingMutationActive) {
			clearCraftSlotSource(slot);
		}
	}

	public void onContainerCleared() {
		if (!sourceTrackingMutationActive) {
			clearAllCraftSlotSources();
		}
	}

	private void returnStackToSourcesOrPlayer(TrackedCraftGridPort port, int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		ItemStack remaining = stack.copy();
		Map<ItemSourceRef, Integer> sourceAllocations = craftSlotSourceLedger[slot];
		if (!sourceAllocations.isEmpty()) {
			returnStackToTrackedSources(sourceAllocations, remaining);
		}

		boolean inserted = port.getPlayer().getInventory().add(remaining);
		if (!inserted && !remaining.isEmpty()) {
			port.getPlayer().drop(remaining, false);
		}
	}

	private int returnStackToTrackedSources(Map<ItemSourceRef, Integer> sourceAllocations, ItemStack remaining) {
		if (remaining.isEmpty() || sourceAllocations.isEmpty()) {
			return 0;
		}

		int returnedAmount = 0;
		for (Map.Entry<ItemSourceRef, Integer> allocation : sourceAllocations.entrySet()) {
			if (remaining.isEmpty()) {
				break;
			}

			ItemSourceRef sourceRef = allocation.getKey();
			int targetAmount = Math.min(allocation.getValue(), remaining.getCount());
			if (targetAmount <= 0) {
				continue;
			}

			try {
				ItemStack toReturn = remaining.copy();
				toReturn.setCount(targetAmount);
				ItemStack notInserted = sourceRef.slotRef().insert(toReturn, false);
				int inserted = targetAmount - notInserted.getCount();
				if (inserted > 0) {
					remaining.shrink(inserted);
					returnedAmount += inserted;
				}
			} catch (RuntimeException ignored) {
				// The caller owns logging and fallback behavior is handled by the remaining stack path.
			}
		}
		return returnedAmount;
	}

	private int getSlotStackLimit(TrackedCraftGridPort port, ItemStack stack) {
		return Math.min(port.getCraftGridMaxStackSize(), stack.getMaxStackSize());
	}

	private void addCraftSlotSource(int slot, ItemSourceRef sourceRef, int count) {
		if (slot < 0 || slot >= craftSlotSourceLedger.length || sourceRef == null || count <= 0) {
			return;
		}
		craftSlotSourceLedger[slot].merge(sourceRef, count, Integer::sum);
	}

	private void consumeCraftSlotSource(int slot, int count) {
		if (slot < 0 || slot >= craftSlotSourceLedger.length || count <= 0) {
			return;
		}
		Map<ItemSourceRef, Integer> slotLedger = craftSlotSourceLedger[slot];
		if (slotLedger.isEmpty()) {
			return;
		}

		int remaining = count;
		var iterator = slotLedger.entrySet().iterator();
		while (iterator.hasNext() && remaining > 0) {
			Map.Entry<ItemSourceRef, Integer> entry = iterator.next();
			int tracked = entry.getValue();
			if (tracked <= remaining) {
				remaining -= tracked;
				iterator.remove();
			} else {
				entry.setValue(tracked - remaining);
				remaining = 0;
			}
		}
	}

	private void clearCraftSlotSource(int slot) {
		if (slot >= 0 && slot < craftSlotSourceLedger.length) {
			craftSlotSourceLedger[slot].clear();
		}
	}

	private void clearAllCraftSlotSources() {
		for (Map<ItemSourceRef, Integer> slotLedger : craftSlotSourceLedger) {
			slotLedger.clear();
		}
	}

	private void runWithSourceTrackingMutation(Runnable runnable) {
		sourceTrackingMutationActive = true;
		try {
			runnable.run();
		} finally {
			sourceTrackingMutationActive = false;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<ItemSourceRef, Integer>[] createSourceLedger(int craftGridSize) {
		Map<ItemSourceRef, Integer>[] ledger = (Map<ItemSourceRef, Integer>[]) new Map[craftGridSize];
		for (int i = 0; i < ledger.length; i++) {
			ledger[i] = new LinkedHashMap<>();
		}
		return ledger;
	}

	public record ClearResult(
			int clearedSlots,
			int returnedToSources,
			int returnedToInventory,
			int droppedItems,
			boolean anySlotChanged
	) {
	}

	public record RemoveResult(
			boolean changed,
			int removedCount,
			boolean hadTrackedSources
	) {
		public static RemoveResult notChanged() {
			return new RemoveResult(false, 0, false);
		}
	}
}
