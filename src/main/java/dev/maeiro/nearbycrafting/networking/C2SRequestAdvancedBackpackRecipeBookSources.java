package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade.AdvancedBackpackRecipeSourceSnapshotBuilder;
import dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade.AdvancedCraftingUpgradeWrapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;

import java.util.function.Supplier;

public class C2SRequestAdvancedBackpackRecipeBookSources {
	private final int containerId;

	public C2SRequestAdvancedBackpackRecipeBookSources(int containerId) {
		this.containerId = containerId;
	}

	public C2SRequestAdvancedBackpackRecipeBookSources(FriendlyByteBuf buf) {
		this.containerId = buf.readInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(containerId);
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
		});
		ctx.setPacketHandled(true);
	}
}
