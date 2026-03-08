package dev.maeiro.proximitycrafting.networking;

import dev.architectury.networking.NetworkManager;
import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

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

	public void handle(Supplier<NetworkManager.PacketContext> ctxSupplier) {
		NetworkManager.PacketContext ctx = ctxSupplier.get();
		ctx.queue(() -> {
			long startNs = System.nanoTime();
			if (!(ctx.getPlayer() instanceof ServerPlayer player) || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			if (menu.containerId != containerId) {
				return;
			}

			List<RecipeBookSourceEntry> entries = menu.getServerRecipeBookSnapshot(true, "packet_request_sources");
			ProximityCraftingNetwork.CHANNEL.sendToPlayer(player, new S2CRecipeBookSourceSnapshot(containerId, entries));

			if (isDebugLoggingEnabled()) {
				double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] packet.C2SRequestRecipeBookSources player={} menu={} snapshotEntries={} took={}ms",
						player.getGameProfile().getName(),
						menu.containerId,
						entries.size(),
						String.format("%.3f", totalMs)
				);
			}
		});
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.serverRuntimeSettings().debugLogging();
		} catch (RuntimeException exception) {
			return false;
		}
	}
}
