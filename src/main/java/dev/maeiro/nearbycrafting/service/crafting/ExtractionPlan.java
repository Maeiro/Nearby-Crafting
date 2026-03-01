package dev.maeiro.nearbycrafting.service.crafting;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExtractionPlan {
	private final List<PlannedExtraction> steps;

	public ExtractionPlan(List<PlannedExtraction> steps) {
		this.steps = List.copyOf(steps);
	}

	public List<PlannedExtraction> steps() {
		return steps;
	}

	public ItemStack[] commit() {
		ItemStack[] resultGrid = new ItemStack[9];
		for (int i = 0; i < resultGrid.length; i++) {
			resultGrid[i] = ItemStack.EMPTY;
		}

		List<CommittedExtraction> committed = new ArrayList<>();
		for (PlannedExtraction step : steps) {
			if (step.isEmpty()) {
				continue;
			}

			ItemStack extracted = step.sourceRef().handler().extractItem(step.sourceRef().slot(), step.count(), false);
			if (extracted.isEmpty() || extracted.getCount() < step.count() || !step.requiredIngredient().test(extracted)) {
				rollback(committed);
				return null;
			}

			ItemStack targetStack = extracted.copy();
			targetStack.setCount(step.count());
			resultGrid[step.targetSlot()] = targetStack;
			committed.add(new CommittedExtraction(step, targetStack.copy()));

			if (extracted.getCount() > step.count()) {
				ItemStack remainder = extracted.copy();
				remainder.shrink(step.count());
				ItemStack notReinserted = step.sourceRef().handler().insertItem(step.sourceRef().slot(), remainder, false);
				if (!notReinserted.isEmpty()) {
					rollback(committed);
					return null;
				}
			}
		}

		return resultGrid;
	}

	private void rollback(List<CommittedExtraction> committed) {
		Collections.reverse(committed);
		for (CommittedExtraction extraction : committed) {
			ItemStack notInserted = extraction.step.sourceRef().handler().insertItem(
					extraction.step.sourceRef().slot(),
					extraction.stack,
					false
			);
			if (!notInserted.isEmpty()) {
				NearbyCrafting.LOGGER.warn(
						"NearbyCrafting rollback couldn't fully reinsert stack {} for source {}:{}",
						notInserted,
						extraction.step.sourceRef().sourceType(),
						extraction.step.sourceRef().slot()
				);
			}
		}
	}

	private record CommittedExtraction(PlannedExtraction step, ItemStack stack) {
	}
}

