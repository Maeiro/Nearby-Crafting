package dev.maeiro.nearbycrafting.service.source;

import dev.maeiro.nearbycrafting.service.crafting.ExtractionPlan;
import dev.maeiro.nearbycrafting.service.crafting.PlannedExtraction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IngredientSourcePool {
	private final List<ItemSourceRef> sourceRefs;

	public IngredientSourcePool(List<ItemSourceRef> sourceRefs) {
		this.sourceRefs = sourceRefs;
	}

	public Optional<ExtractionPlan> plan(List<Ingredient> targetGridIngredients) {
		if (targetGridIngredients.size() != 9) {
			return Optional.empty();
		}

		Map<ItemSourceRef, Integer> reservations = new HashMap<>();
		List<PlannedExtraction> steps = new ArrayList<>(9);

		for (int slot = 0; slot < 9; slot++) {
			Ingredient ingredient = targetGridIngredients.get(slot);
			if (ingredient.isEmpty()) {
				steps.add(PlannedExtraction.empty(slot));
				continue;
			}

			PlannedExtraction chosen = findMatchingSource(slot, ingredient, reservations);
			if (chosen == null) {
				return Optional.empty();
			}

			steps.add(chosen);
			reservations.merge(chosen.sourceRef(), chosen.count(), Integer::sum);
		}

		return Optional.of(new ExtractionPlan(steps));
	}

	private PlannedExtraction findMatchingSource(int targetSlot, Ingredient ingredient, Map<ItemSourceRef, Integer> reservations) {
		for (ItemSourceRef sourceRef : sourceRefs) {
			ItemStack stack = sourceRef.handler().getStackInSlot(sourceRef.slot());
			if (stack.isEmpty() || !ingredient.test(stack)) {
				continue;
			}

			int reserved = reservations.getOrDefault(sourceRef, 0);
			int available = stack.getCount() - reserved;
			if (available <= 0) {
				continue;
			}

			ItemStack oneItem = stack.copy();
			oneItem.setCount(1);
			return new PlannedExtraction(targetSlot, ingredient, sourceRef, 1, oneItem);
		}

		return null;
	}
}

