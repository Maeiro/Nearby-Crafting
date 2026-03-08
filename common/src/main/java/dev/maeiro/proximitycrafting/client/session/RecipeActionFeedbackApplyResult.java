package dev.maeiro.proximitycrafting.client.session;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record RecipeActionFeedbackApplyResult(
		boolean hadInFlightAction,
		@Nullable ResourceLocation clearedInFlightFillRecipeId,
		int clearedInFlightAdjustSteps,
		@Nullable ResourceLocation pendingFillRecipeId,
		int pendingAdjustSteps
) {
}
