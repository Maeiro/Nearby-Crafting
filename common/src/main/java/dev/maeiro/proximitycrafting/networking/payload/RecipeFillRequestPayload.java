package dev.maeiro.proximitycrafting.networking.payload;

import net.minecraft.resources.ResourceLocation;

public record RecipeFillRequestPayload(ResourceLocation recipeId, boolean craftAll) {
}
