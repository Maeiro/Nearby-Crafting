package dev.maeiro.nearbycrafting.menu.slot;

import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.networking.NearbyCraftingNetwork;
import dev.maeiro.nearbycrafting.networking.RecipeBookSourceSnapshotBuilder;
import dev.maeiro.nearbycrafting.networking.S2CRecipeBookSourceSnapshot;
import dev.maeiro.nearbycrafting.service.crafting.FillResult;
import dev.maeiro.nearbycrafting.service.crafting.RecipeFillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

public class NearbyResultSlot extends ResultSlot {
	private final NearbyCraftingMenu menu;

	public NearbyResultSlot(NearbyCraftingMenu menu, Player player, CraftingContainer craftSlots, Container resultContainer, int slotIndex, int x, int y) {
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

			NearbyCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> serverPlayer),
					new S2CRecipeBookSourceSnapshot(
							menu.containerId,
							RecipeBookSourceSnapshotBuilder.build(menu)
					)
			);
		}
	}
}
