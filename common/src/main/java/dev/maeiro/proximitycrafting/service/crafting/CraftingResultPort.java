package dev.maeiro.proximitycrafting.service.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;

public interface CraftingResultPort {
	AbstractContainerMenu getMenu();

	Level getLevel();

	Player getPlayer();

	CraftingContainer getCraftSlots();

	ResultContainer getResultSlots();

	CraftingRecipe getTrackedRecipe();
}
