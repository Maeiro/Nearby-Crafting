package dev.maeiro.proximitycrafting.service.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface CraftConsumeSessionPort {
	int getMaxShiftCraftIterations();

	boolean hasResultItem();

	ItemStack quickMoveResult(Player player);
}
