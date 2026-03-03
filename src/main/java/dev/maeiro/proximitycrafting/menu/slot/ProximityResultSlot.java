package dev.maeiro.proximitycrafting.menu.slot;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import dev.maeiro.proximitycrafting.networking.RecipeBookSourceSnapshotBuilder;
import dev.maeiro.proximitycrafting.networking.S2CRecipeBookSourceSnapshot;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import dev.maeiro.proximitycrafting.service.crafting.RecipeFillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

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
			// Do not auto-refill during shift-click result extraction; otherwise a single loaded
			// recipe can chain-refill and consume all available ingredients.
			if (menu.isAutoRefillAfterCraft() && !menu.isResultShiftCraftInProgress()) {
				FillResult refillResult = RecipeFillService.refillLastRecipe(menu);
				if (!refillResult.success()) {
					// Silent fail to avoid chat spam while crafting manually.
				}
			}

			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> serverPlayer),
					new S2CRecipeBookSourceSnapshot(
							menu.containerId,
							RecipeBookSourceSnapshotBuilder.build(menu)
					)
			);
		}
	}
}


