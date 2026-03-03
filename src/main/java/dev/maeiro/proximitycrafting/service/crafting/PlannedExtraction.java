package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record PlannedExtraction(
		int targetSlot,
		Ingredient requiredIngredient,
		ItemSourceRef sourceRef,
		int count,
		ItemStack displayStack
) {
	public static PlannedExtraction empty(int targetSlot) {
		return new PlannedExtraction(targetSlot, Ingredient.EMPTY, null, 0, ItemStack.EMPTY);
	}

	public boolean isEmpty() {
		return requiredIngredient.isEmpty() || sourceRef == null || count <= 0 || displayStack.isEmpty();
	}
}


