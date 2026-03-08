package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

public class C2SClearCraftGrid {
	private final int containerId;

	public C2SClearCraftGrid(int containerId) {
		this.containerId = containerId;
	}

	public C2SClearCraftGrid(FriendlyByteBuf buf) {
		this.containerId = buf.readInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(containerId);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			long startNs = System.nanoTime();
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			if (menu.containerId != containerId) {
				return;
			}

			boolean hadItems = menu.hasAnyCraftGridItems();
			long clearStartNs = 0L;
			long clearEndNs = 0L;
			if (hadItems) {
				clearStartNs = System.nanoTime();
				menu.clearCraftGridToPlayerOrDrop();
				menu.invalidateServerRecipeBookSnapshotCache();
				clearEndNs = System.nanoTime();
			}

			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeFillFeedback(
							true,
							hadItems
									? "proximitycrafting.feedback.grid_cleared"
									: "proximitycrafting.feedback.grid_already_empty",
							0
					)
			);

			if (hadItems) {
				List<RecipeBookSourceEntry> snapshotEntries = menu.getServerRecipeBookSnapshot(
						false,
						"packet_clear_grid"
				);
				ProximityCraftingNetwork.CHANNEL.send(
						PacketDistributor.PLAYER.with(() -> player),
						new S2CRecipeBookSourceSnapshot(containerId, snapshotEntries)
				);
			}

			if (isDebugLoggingEnabled()) {
				double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
				double clearMs = hadItems ? (clearEndNs - clearStartNs) / 1_000_000.0D : 0.0D;
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] packet.C2SClearCraftGrid player={} menu={} hadItems={} clearMs={} totalMs={}",
						player.getGameProfile().getName(),
						menu.containerId,
						hadItems,
						String.format("%.3f", clearMs),
						String.format("%.3f", totalMs)
				);
			}
		});
		ctx.setPacketHandled(true);
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
	}
}
