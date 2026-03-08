package dev.maeiro.proximitycrafting.service.crafting;

public record ResultTakeOutcome(
		boolean refillAttempted,
		FillResult refillResult
) {
	public boolean refillSucceeded() {
		return refillResult != null && refillResult.success();
	}
}
