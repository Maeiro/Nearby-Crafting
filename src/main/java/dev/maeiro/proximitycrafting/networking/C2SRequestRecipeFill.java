package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SRequestRecipeFill {
	private final ResourceLocation recipeId;
	private final boolean craftAll;

	public C2SRequestRecipeFill(ResourceLocation recipeId, boolean craftAll) {
		this.recipeId = recipeId;
		this.craftAll = craftAll;
	}

	public C2SRequestRecipeFill(FriendlyByteBuf buf) {
		this.recipeId = buf.readResourceLocation();
		this.craftAll = buf.readBoolean();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeResourceLocation(recipeId);
		buf.writeBoolean(craftAll);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}

			FillResult result = menu.fillRecipeById(recipeId, craftAll);
			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeBookSourceSnapshot(
							menu.containerId,
							RecipeBookSourceSnapshotBuilder.build(menu)
					)
			);
			ProximityCraftingNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CRecipeFillFeedback(result.success(), result.messageKey(), result.craftedAmount()));
		});
		ctx.setPacketHandled(true);
	}
}


