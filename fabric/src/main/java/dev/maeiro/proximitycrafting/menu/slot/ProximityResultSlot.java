package dev.maeiro.proximitycrafting.menu.slot;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

public class ProximityResultSlot extends ResultSlot {
	private final ProximityCraftingMenu menu;

	public ProximityResultSlot(ProximityCraftingMenu menu, Player player, CraftingContainer craftSlots, Container resultContainer, int slotIndex, int x, int y) {
		super(player, craftSlots, resultContainer, slotIndex, x, y);
		this.menu = menu;
	}

	@Override
	public void onTake(Player player, ItemStack stack) {
		super.onTake(player, stack);

		if (player instanceof ServerPlayer serverPlayer) {
			menu.handleResultSlotTake(serverPlayer);
		}
	}
}


