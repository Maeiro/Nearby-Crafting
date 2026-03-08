package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.world.entity.player.Player;

public final class CraftConsumeService {
	private CraftConsumeService() {
	}

	public static int craftAll(ProximityCraftingMenu menu, Player player) {
		return CraftConsumeOperations.craftAll(new FabricCraftConsumeSessionAdapter(menu), player);
	}
}

