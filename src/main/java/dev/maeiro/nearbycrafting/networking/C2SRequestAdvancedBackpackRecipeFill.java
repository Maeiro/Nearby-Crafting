package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade.AdvancedBackpackRecipeFillService;
import dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade.AdvancedBackpackRecipeSourceSnapshotBuilder;
import dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade.AdvancedCraftingUpgradeContainer;
import dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade.AdvancedCraftingUpgradeWrapper;
import dev.maeiro.nearbycrafting.service.crafting.FillResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;

import java.util.Optional;
import java.util.function.Supplier;

public class C2SRequestAdvancedBackpackRecipeFill {
	private final int containerId;
	private final ResourceLocation recipeId;
	private final boolean craftAll;

	public C2SRequestAdvancedBackpackRecipeFill(int containerId, ResourceLocation recipeId, boolean craftAll) {
		this.containerId = containerId;
		this.recipeId = recipeId;
		this.craftAll = craftAll;
	}

	public C2SRequestAdvancedBackpackRecipeFill(FriendlyByteBuf buf) {
		this.containerId = buf.readInt();
		this.recipeId = buf.readResourceLocation();
		this.craftAll = buf.readBoolean();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(containerId);
		buf.writeResourceLocation(recipeId);
		buf.writeBoolean(craftAll);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof BackpackContainer menu)) {
				return;
			}
			if (menu.containerId != containerId) {
				return;
			}

			FillResult result = FillResult.failure("nearbycrafting.feedback.invalid_recipe_type");
			Optional<? extends Recipe<?>> optionalRecipe = player.level().getRecipeManager().byKey(recipeId);
			if (optionalRecipe.isEmpty() || !(optionalRecipe.get() instanceof CraftingRecipe craftingRecipe)) {
				result = FillResult.failure("nearbycrafting.feedback.recipe_not_found");
			} else {
				Optional<UpgradeContainerBase<?, ?>> openContainerOptional = menu.getOpenContainer();
				if (openContainerOptional.isPresent() && openContainerOptional.get() instanceof AdvancedCraftingUpgradeContainer advancedContainer) {
					if (advancedContainer.getUpgradeWrapper() instanceof AdvancedCraftingUpgradeWrapper wrapper) {
						result = AdvancedBackpackRecipeFillService.fillFromRecipe(player, advancedContainer, wrapper, craftingRecipe, craftAll);
					}
				}
			}

			menu.getOpenContainer()
					.filter(openContainer -> openContainer.getUpgradeWrapper() instanceof AdvancedCraftingUpgradeWrapper)
					.ifPresent(openContainer -> {
						AdvancedCraftingUpgradeWrapper wrapper = (AdvancedCraftingUpgradeWrapper) openContainer.getUpgradeWrapper();
						NearbyCraftingNetwork.CHANNEL.send(
								PacketDistributor.PLAYER.with(() -> player),
								new S2CRecipeBookSourceSnapshot(
										containerId,
										AdvancedBackpackRecipeSourceSnapshotBuilder.build(player, wrapper)
								)
						);
					});

			NearbyCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeFillFeedback(result.success(), result.messageKey(), result.craftedAmount())
			);
		});
		ctx.setPacketHandled(true);
	}
}
