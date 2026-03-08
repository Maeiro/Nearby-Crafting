package dev.maeiro.proximitycrafting.service.crafting;

public interface ResultTakePort {
	boolean isAutoRefillAfterCraft();

	boolean isResultShiftCraftInProgress();

	FillResult refillLastRecipe();
}
