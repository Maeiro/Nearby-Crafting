package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import dev.maeiro.nearbycrafting.service.crafting.FillResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SAdjustRecipeLoad {
	private final int steps;

	public C2SAdjustRecipeLoad(int steps) {
		this.steps = steps;
	}

	public C2SAdjustRecipeLoad(FriendlyByteBuf buf) {
		this.steps = buf.readVarInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(steps);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof NearbyCraftingMenu menu)) {
				NearbyCrafting.LOGGER.info("[NC-SCROLL] server ignored packet: player/menu invalid");
				return;
			}

			NearbyCrafting.LOGGER.info(
					"[NC-SCROLL] server received adjust packet steps={} menu={} player={}",
					steps,
					menu.containerId,
					player.getGameProfile().getName()
			);
			FillResult result = menu.adjustRecipeLoad(steps);
			NearbyCrafting.LOGGER.info(
					"[NC-SCROLL] server adjust result success={} key={} amount={}",
					result.success(),
					result.messageKey(),
					result.craftedAmount()
			);
			NearbyCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeBookSourceSnapshot(
							menu.containerId,
							RecipeBookSourceSnapshotBuilder.build(menu)
					)
			);
			NearbyCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeFillFeedback(result.success(), result.messageKey(), result.craftedAmount())
			);
		});
		ctx.setPacketHandled(true);
	}
}
