package dev.maeiro.nearbycrafting.service.crafting;

import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CraftConsumeService {
	private CraftConsumeService() {
	}

	public static int craftAll(NearbyCraftingMenu menu, Player player) {
		int crafted = 0;
		int maxIterations = NearbyCraftingConfig.SERVER.maxShiftCraftIterations.get();

		for (int i = 0; i < maxIterations; i++) {
			Slot resultSlot = menu.getSlot(0);
			if (!resultSlot.hasItem()) {
				break;
			}

			ItemStack moved = menu.quickMoveStack(player, 0);
			if (moved.isEmpty()) {
				break;
			}
			crafted += moved.getCount();
		}

		return crafted;
	}
}

