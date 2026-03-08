package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

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

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			long startNs = System.nanoTime();
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || !(minecraft.player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			if (menu.containerId != payload.containerId()) {
				return;
			}

			boolean sourcesChanged = menu.setClientRecipeBookSupplementalSources(payload.sourceEntries());
			if (minecraft.screen instanceof ProximityCraftingScreen proximityCraftingScreen) {
				proximityCraftingScreen.onSourceSnapshotAppliedClient(payload.sourceEntries().size(), sourcesChanged);
			}
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] packet.S2CRecipeBookSourceSnapshot menu={} entries={} applyMs={}",
						payload.containerId(),
						payload.sourceEntries().size(),
						String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
				);
			}
		});
		ctx.setPacketHandled(true);
	}

	private static boolean isDebugLoggingEnabled() {
		return ProximityCraftingConfig.isClientDebugLoggingEnabled();
	}
}


