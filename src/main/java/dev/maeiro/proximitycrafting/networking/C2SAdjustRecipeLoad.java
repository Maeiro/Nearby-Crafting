package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
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
			if (player == null || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				ProximityCrafting.LOGGER.info("[NC-SCROLL] server ignored packet: player/menu invalid");
				return;
			}

			ProximityCrafting.LOGGER.info(
					"[NC-SCROLL] server received adjust packet steps={} menu={} player={}",
					steps,
					menu.containerId,
					player.getGameProfile().getName()
			);
			FillResult result = menu.adjustRecipeLoad(steps);
			ProximityCrafting.LOGGER.info(
					"[NC-SCROLL] server adjust result success={} key={} amount={}",
					result.success(),
					result.messageKey(),
					result.craftedAmount()
			);
			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeBookSourceSnapshot(
							menu.containerId,
							RecipeBookSourceSnapshotBuilder.build(menu)
					)
			);
			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeFillFeedback(result.success(), result.messageKey(), result.craftedAmount())
			);
		});
		ctx.setPacketHandled(true);
	}
}


