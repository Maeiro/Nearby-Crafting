package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.service.prefs.PlayerPreferenceStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSyncSharedPreferences {
	private final boolean includePlayerInventory;
	private final String sourcePriority;

	public C2SSyncSharedPreferences(boolean includePlayerInventory, String sourcePriority) {
		this.includePlayerInventory = includePlayerInventory;
		this.sourcePriority = sourcePriority;
	}

	public C2SSyncSharedPreferences(FriendlyByteBuf buf) {
		this.includePlayerInventory = buf.readBoolean();
		this.sourcePriority = buf.readUtf();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBoolean(includePlayerInventory);
		buf.writeUtf(sourcePriority);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null) {
				return;
			}

			NearbyCraftingConfig.SourcePriority resolvedPriority = NearbyCraftingConfig.SourcePriority.fromConfig(sourcePriority);
			PlayerPreferenceStore.update(player.getUUID(), includePlayerInventory, resolvedPriority);
			if (NearbyCraftingConfig.SERVER.debugLogging.get()) {
				NearbyCrafting.LOGGER.info(
						"[NC-ADV-UPGRADE] synced shared prefs player={} includePlayerInventory={} sourcePriority={}",
						player.getGameProfile().getName(),
						includePlayerInventory,
						resolvedPriority
				);
			}
		});
		ctx.setPacketHandled(true);
	}
}
