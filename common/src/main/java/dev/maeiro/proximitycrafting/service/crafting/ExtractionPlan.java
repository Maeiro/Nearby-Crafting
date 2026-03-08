package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
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

	public ExtractionCommitResult commit() {
		ItemStack[] resultGrid = new ItemStack[9];
		ItemSourceRef[] sourceRefs = new ItemSourceRef[9];
		for (int i = 0; i < resultGrid.length; i++) {
			resultGrid[i] = ItemStack.EMPTY;
			sourceRefs[i] = null;
		}

		List<CommittedExtraction> committed = new ArrayList<>();
		for (PlannedExtraction step : steps) {
			if (step.isEmpty()) {
				continue;
			}

			ItemStack extracted = step.sourceRef().slotRef().extract(step.count(), false);
			boolean ingredientMatch = step.requiredIngredient().test(extracted);
			boolean exactStackMatch = step.displayStack().isEmpty() || ItemStack.isSameItemSameTags(step.displayStack(), extracted);
			if (extracted.isEmpty() || extracted.getCount() < step.count() || !ingredientMatch || !exactStackMatch) {
				rollback(committed);
				return null;
			}

			ItemStack targetStack = extracted.copy();
			targetStack.setCount(step.count());
			resultGrid[step.targetSlot()] = targetStack;
			sourceRefs[step.targetSlot()] = step.sourceRef();
			committed.add(new CommittedExtraction(step, targetStack.copy()));

			if (extracted.getCount() > step.count()) {
				ItemStack remainder = extracted.copy();
				remainder.shrink(step.count());
				ItemStack notReinserted = step.sourceRef().slotRef().insert(remainder, false);
				if (!notReinserted.isEmpty()) {
					rollback(committed);
					return null;
				}
			}
		}

		return new ExtractionCommitResult(resultGrid, sourceRefs);
	}

	private void rollback(List<CommittedExtraction> committed) {
		Collections.reverse(committed);
		for (CommittedExtraction extraction : committed) {
			ItemStack notInserted = extraction.step.sourceRef().slotRef().insert(extraction.stack, false);
			if (!notInserted.isEmpty()) {
				ProximityCrafting.LOGGER.warn(
						"ProximityCrafting rollback couldn't fully reinsert stack {} for source {}:{}",
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


