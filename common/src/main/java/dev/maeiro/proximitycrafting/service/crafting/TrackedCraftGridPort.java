package dev.maeiro.proximitycrafting.service.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface TrackedCraftGridPort {
	Player getPlayer();

	int debugContextId();

	ItemStack getCraftGridItem(int slot);

	void setCraftGridItem(int slot, ItemStack stack);

	void markCraftGridChanged();

	int getCraftGridSize();

	int getCraftGridMaxStackSize();

	void flushCraftingGridChange();
}
