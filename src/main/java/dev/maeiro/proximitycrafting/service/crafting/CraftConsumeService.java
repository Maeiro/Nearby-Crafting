package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CraftConsumeService {
	private CraftConsumeService() {
	}

	public static int craftAll(ProximityCraftingMenu menu, Player player) {
		int crafted = 0;
		int maxIterations = ProximityCraftingConfig.SERVER.maxShiftCraftIterations.get();

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



