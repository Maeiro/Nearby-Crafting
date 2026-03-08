package dev.maeiro.proximitycrafting.service.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CraftConsumeOperations {
	private CraftConsumeOperations() {
	}

	public static int craftAll(CraftConsumeSessionPort session, Player player) {
		int crafted = 0;
		int maxIterations = session.getMaxShiftCraftIterations();

		for (int i = 0; i < maxIterations; i++) {
			if (!session.hasResultItem()) {
				break;
			}

			ItemStack moved = session.quickMoveResult(player);
			if (moved.isEmpty()) {
				break;
			}
			crafted += moved.getCount();
		}

		return crafted;
	}
}
