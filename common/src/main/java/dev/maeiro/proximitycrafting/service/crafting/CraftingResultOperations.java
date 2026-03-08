package dev.maeiro.proximitycrafting.service.crafting;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class CraftingResultOperations {
	private CraftingResultOperations() {
	}

	public static void updateCraftingResult(CraftingResultPort port) {
		Level level = port.getLevel();
		if (level.isClientSide()) {
			return;
		}

		Player player = port.getPlayer();
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		AbstractContainerMenu menu = port.getMenu();
		ResultContainer resultSlots = port.getResultSlots();
		CraftingContainer craftSlots = port.getCraftSlots();
		ItemStack result = ItemStack.EMPTY;
		Optional<CraftingRecipe> optional = resolvePreferredActiveRecipe(level, craftSlots, port.getTrackedRecipe());
		if (optional.isPresent()) {
			CraftingRecipe recipe = optional.get();
			if (resultSlots.setRecipeUsed(level, serverPlayer, recipe)) {
				ItemStack assembled = recipe.assemble(craftSlots, level.registryAccess());
				if (assembled.isItemEnabled(level.enabledFeatures())) {
					result = assembled;
				}
			}
		}

		resultSlots.setItem(0, result);
		menu.setRemoteSlot(0, result);
		serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, result));
	}

	public static Optional<CraftingRecipe> resolvePreferredActiveRecipe(Level level, CraftingContainer craftSlots, CraftingRecipe trackedRecipe) {
		Optional<CraftingRecipe> preferredTracked = resolvePreferredTrackedRecipe(level, craftSlots, trackedRecipe);
		if (preferredTracked.isPresent()) {
			return preferredTracked;
		}
		return resolveCurrentRecipe(level, craftSlots);
	}

	public static Optional<CraftingRecipe> resolvePreferredTrackedRecipe(Level level, CraftingContainer craftSlots, CraftingRecipe trackedRecipe) {
		if (trackedRecipe == null || level == null) {
			return Optional.empty();
		}
		if (!trackedRecipe.matches(craftSlots, level)) {
			return Optional.empty();
		}
		return Optional.of(trackedRecipe);
	}

	public static Optional<CraftingRecipe> resolveCurrentRecipe(Level level, CraftingContainer craftSlots) {
		if (level == null || level.getServer() == null) {
			return Optional.empty();
		}
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
	}
}
