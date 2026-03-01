package dev.maeiro.nearbycrafting.client.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.nearbycrafting.networking.NearbyCraftingNetwork;
import dev.maeiro.nearbycrafting.registry.ModBlocks;
import dev.maeiro.nearbycrafting.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

import java.util.List;

@EmiEntrypoint
public class NearbyCraftingEmiPlugin implements EmiPlugin {
	@Override
	public void register(EmiRegistry registry) {
		registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(ModBlocks.NEARBY_CRAFTING_TABLE.get()));
		registry.addRecipeHandler(ModMenuTypes.NEARBY_CRAFTING_MENU.get(), new NearbyCraftingEmiRecipeHandler());
	}

	private static class NearbyCraftingEmiRecipeHandler implements EmiRecipeHandler<NearbyCraftingMenu> {
		@Override
		public EmiPlayerInventory getInventory(AbstractContainerScreen<NearbyCraftingMenu> screen) {
			List<EmiStack> stacks = screen.getMenu().slots.stream()
					.skip(10)
					.limit(36)
					.map(Slot::getItem)
					.map(EmiStack::of)
					.toList();
			return new EmiPlayerInventory(stacks);
		}

		@Override
		public boolean supportsRecipe(EmiRecipe recipe) {
			return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING;
		}

		@Override
		public boolean canCraft(EmiRecipe recipe, EmiCraftContext<NearbyCraftingMenu> context) {
			return recipe.getId() != null;
		}

		@Override
		public boolean craft(EmiRecipe recipe, EmiCraftContext<NearbyCraftingMenu> context) {
			ResourceLocation recipeId = recipe.getId();
			if (recipeId == null) {
				return false;
			}

			boolean craftAll = context.getAmount() > 1 || context.getDestination() != EmiCraftContext.Destination.NONE;
			NearbyCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(recipeId, craftAll));
			return true;
		}
	}
}

