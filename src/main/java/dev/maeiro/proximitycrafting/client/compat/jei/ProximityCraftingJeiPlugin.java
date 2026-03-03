package dev.maeiro.proximitycrafting.client.compat.jei;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.C2SRequestRecipeFill;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import dev.maeiro.proximitycrafting.registry.ModItems;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@JeiPlugin
public class ProximityCraftingJeiPlugin implements IModPlugin {
	private static final ResourceLocation PLUGIN_UID = new ResourceLocation(ProximityCrafting.MOD_ID, "jei_plugin");

	@Override
	public ResourceLocation getPluginUid() {
		return PLUGIN_UID;
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(new ItemStack(ModItems.PROXIMITY_CRAFTING_TABLE.get()), RecipeTypes.CRAFTING);
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		IRecipeTransferHandlerHelper transferHelper = registration.getTransferHelper();
		registration.addRecipeTransferHandler(new ProximityCraftingTransferHandler(transferHelper), RecipeTypes.CRAFTING);
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		ProximityCraftingJeiCraftableFilterController.onRuntimeAvailable(jeiRuntime);
	}

	@Override
	public void onRuntimeUnavailable() {
		ProximityCraftingJeiCraftableFilterController.onRuntimeUnavailable();
	}

	private static class ProximityCraftingTransferHandler implements IRecipeTransferHandler<ProximityCraftingMenu, CraftingRecipe> {
		private final IRecipeTransferHandlerHelper transferHelper;

		private ProximityCraftingTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
			this.transferHelper = transferHelper;
		}

		@Override
		public Class<? extends ProximityCraftingMenu> getContainerClass() {
			return ProximityCraftingMenu.class;
		}

		@Override
		public Optional<MenuType<ProximityCraftingMenu>> getMenuType() {
			return Optional.of(ModMenuTypes.PROXIMITY_CRAFTING_MENU.get());
		}

		@Override
		public mezz.jei.api.recipe.RecipeType<CraftingRecipe> getRecipeType() {
			return RecipeTypes.CRAFTING;
		}

		@Nullable
		@Override
		public IRecipeTransferError transferRecipe(ProximityCraftingMenu container, CraftingRecipe recipe, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
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
				ProximityCrafting.LOGGER.warn("JEI transfer failed to resolve recipe id for recipe {}", recipe.getClass().getName());
				return transferHelper.createInternalError();
			}

			ProximityCraftingNetwork.CHANNEL.sendToServer(new C2SRequestRecipeFill(recipeId, maxTransfer));
			return null;
		}
	}
}


