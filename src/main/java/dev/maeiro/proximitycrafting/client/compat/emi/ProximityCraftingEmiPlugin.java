package dev.maeiro.proximitycrafting.client.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import dev.maeiro.proximitycrafting.registry.ModBlocks;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

import java.util.List;

@EmiEntrypoint
public class ProximityCraftingEmiPlugin implements EmiPlugin {
	@Override
	public void register(EmiRegistry registry) {
		registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(ModBlocks.PROXIMITY_CRAFTING_TABLE.get()));
		registry.addRecipeHandler(ModMenuTypes.PROXIMITY_CRAFTING_MENU.get(), new ProximityCraftingEmiRecipeHandler());
	}

	private static class ProximityCraftingEmiRecipeHandler implements EmiRecipeHandler<ProximityCraftingMenu> {
		@Override
		public EmiPlayerInventory getInventory(AbstractContainerScreen<ProximityCraftingMenu> screen) {
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
		public boolean canCraft(EmiRecipe recipe, EmiCraftContext<ProximityCraftingMenu> context) {
			return recipe.getId() != null;
		}

		@Override
		public boolean craft(EmiRecipe recipe, EmiCraftContext<ProximityCraftingMenu> context) {
			ResourceLocation recipeId = recipe.getId();
			if (recipeId == null) {
				return false;
			}

			boolean craftAll = context.getAmount() > 1 || context.getDestination() != EmiCraftContext.Destination.NONE;
			ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(recipeId, craftAll));
			return true;
		}
	}
}



