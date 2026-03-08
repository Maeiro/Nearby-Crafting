package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.world.item.ItemStack;

public record ExtractionCommitResult(ItemStack[] extractedStacks, ItemSourceRef[] sourceRefs) {
}

