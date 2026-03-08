package dev.maeiro.proximitycrafting.networking;

import dev.architectury.networking.NetworkManager;
import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.client.net.ProximityClientServices;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceSnapshotPayload;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.function.Supplier;

public class S2CRecipeBookSourceSnapshot {
	private final RecipeBookSourceSnapshotPayload payload;

	public S2CRecipeBookSourceSnapshot(int containerId, List<RecipeBookSourceEntry> sourceEntries) {
		this(new RecipeBookSourceSnapshotPayload(containerId, sourceEntries));
	}

	public S2CRecipeBookSourceSnapshot(FriendlyByteBuf buf) {
		this(RecipeBookSourceSnapshotPayload.decode(buf));
	}

	public S2CRecipeBookSourceSnapshot(RecipeBookSourceSnapshotPayload payload) {
		this.payload = payload;
	}

	public void encode(FriendlyByteBuf buf) {
		payload.encode(buf);
	}

	public void handle(Supplier<NetworkManager.PacketContext> ctxSupplier) {
		NetworkManager.PacketContext ctx = ctxSupplier.get();
		ctx.queue(() -> {
			long startNs = System.nanoTime();
			boolean handled = ProximityClientServices.getClientResponseDispatcher().handleRecipeBookSourceSnapshot(payload);
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] packet.S2CRecipeBookSourceSnapshot menu={} entries={} handled={} applyMs={}",
						payload.containerId(),
						payload.sourceEntries().size(),
						handled,
						String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
				);
			}
		});
	}

	private static boolean isDebugLoggingEnabled() {
		return ProximityCraftingConfig.isClientDebugLoggingEnabled();
	}
}
