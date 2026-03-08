package dev.maeiro.proximitycrafting.client.presenter;

import net.minecraft.world.item.ItemStack;

public record IngredientsPanelEntry(ItemStack displayStack, int availableCount, int requiredCount) {
}
