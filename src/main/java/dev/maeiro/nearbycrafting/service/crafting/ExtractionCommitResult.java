package dev.maeiro.nearbycrafting.service.crafting;

import dev.maeiro.nearbycrafting.service.source.ItemSourceRef;
import net.minecraft.world.item.ItemStack;

public record ExtractionCommitResult(ItemStack[] extractedStacks, ItemSourceRef[] sourceRefs) {
}
