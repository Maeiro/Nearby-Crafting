package dev.maeiro.proximitycrafting.service.crafting;

public final class ResultTakeOperations {
	private ResultTakeOperations() {
	}

	public static ResultTakeOutcome afterResultTaken(ResultTakePort port) {
		if (!port.isAutoRefillAfterCraft() || port.isResultShiftCraftInProgress()) {
			return new ResultTakeOutcome(false, null);
		}

		FillResult refillResult = port.refillLastRecipe();
		return new ResultTakeOutcome(true, refillResult);
	}
}
