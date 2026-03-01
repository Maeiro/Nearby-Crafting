package dev.maeiro.nearbycrafting.client.compat.jei;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.nearbycrafting.networking.NearbyCraftingNetwork;
import dev.maeiro.nearbycrafting.registry.ModItems;
import dev.maeiro.nearbycrafting.registry.ModMenuTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@JeiPlugin
public class NearbyCraftingJeiPlugin implements IModPlugin {
	private static final ResourceLocation PLUGIN_UID = new ResourceLocation(NearbyCrafting.MOD_ID, "jei_plugin");

	@Override
	public ResourceLocation getPluginUid() {
		return PLUGIN_UID;
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(new ItemStack(ModItems.NEARBY_CRAFTING_TABLE.get()), RecipeTypes.CRAFTING);
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		IRecipeTransferHandlerHelper transferHelper = registration.getTransferHelper();
		registration.addRecipeTransferHandler(new NearbyCraftingTransferHandler(transferHelper), RecipeTypes.CRAFTING);
	}

	private static class NearbyCraftingTransferHandler implements IRecipeTransferHandler<NearbyCraftingMenu, CraftingRecipe> {
		private final IRecipeTransferHandlerHelper transferHelper;

		private NearbyCraftingTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
			this.transferHelper = transferHelper;
		}

		@Override
		public Class<? extends NearbyCraftingMenu> getContainerClass() {
			return NearbyCraftingMenu.class;
		}

		@Override
		public Optional<MenuType<NearbyCraftingMenu>> getMenuType() {
			return Optional.of(ModMenuTypes.NEARBY_CRAFTING_MENU.get());
		}

		@Override
		public mezz.jei.api.recipe.RecipeType<CraftingRecipe> getRecipeType() {
			return RecipeTypes.CRAFTING;
		}

		@Nullable
		@Override
		public IRecipeTransferError transferRecipe(NearbyCraftingMenu container, CraftingRecipe recipe, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
			if (!doTransfer) {
				return null;
			}

			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level == null) {
				return transferHelper.createInternalError();
			}

			ResourceLocation recipeId = minecraft.level.getRecipeManager()
					.byKey(recipe.getId())
					.isPresent() ? recipe.getId() : null;

			if (recipeId == null) {
				NearbyCrafting.LOGGER.warn("JEI transfer failed to resolve recipe id for recipe {}", recipe.getClass().getName());
				return transferHelper.createInternalError();
			}

			NearbyCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(recipeId, maxTransfer));
			return null;
		}
	}
}
