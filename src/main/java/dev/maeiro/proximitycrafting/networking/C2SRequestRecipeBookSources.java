package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

public class C2SRequestRecipeBookSources {
	private final int containerId;

	public C2SRequestRecipeBookSources(int containerId) {
		this.containerId = containerId;
	}

	public C2SRequestRecipeBookSources(FriendlyByteBuf buf) {
		this.containerId = buf.readInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(containerId);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			if (menu.containerId != containerId) {
				return;
			}

			List<ProximityCraftingMenu.RecipeBookSourceEntry> entries = RecipeBookSourceSnapshotBuilder.build(menu);
			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeBookSourceSnapshot(containerId, entries)
			);
		});
		ctx.setPacketHandled(true);
	}
}


