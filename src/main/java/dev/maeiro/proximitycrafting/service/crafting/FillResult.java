package dev.maeiro.proximitycrafting.service.crafting;

public record FillResult(boolean success, String messageKey, int craftedAmount) {
	public static FillResult success(String messageKey, int craftedAmount) {
		return new FillResult(true, messageKey, craftedAmount);
	}

	public static FillResult failure(String messageKey) {
		return new FillResult(false, messageKey, 0);
	}
}


