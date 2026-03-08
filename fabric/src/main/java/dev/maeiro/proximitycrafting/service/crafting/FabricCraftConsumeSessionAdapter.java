package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class FabricCraftConsumeSessionAdapter implements CraftConsumeSessionPort {
	private final ProximityCraftingMenu menu;

	FabricCraftConsumeSessionAdapter(ProximityCraftingMenu menu) {
		this.menu = menu;
	}

	@Override
	public int getMaxShiftCraftIterations() {
		return ProximityCraftingConfig.serverRuntimeSettings().maxShiftCraftIterations();
	}

	@Override
	public boolean hasResultItem() {
		return menu.getSlot(0).hasItem();
	}

	@Override
	public ItemStack quickMoveResult(Player player) {
		return menu.quickMoveStack(player, 0);
	}
}

