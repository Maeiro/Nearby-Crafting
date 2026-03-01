package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SUpdateClientPreferences {
	private final int containerId;
	private final boolean autoRefillAfterCraft;
	private final boolean includePlayerInventory;
	private final String sourcePriority;

	public C2SUpdateClientPreferences(int containerId, boolean autoRefillAfterCraft, boolean includePlayerInventory, String sourcePriority) {
		this.containerId = containerId;
		this.autoRefillAfterCraft = autoRefillAfterCraft;
		this.includePlayerInventory = includePlayerInventory;
		this.sourcePriority = sourcePriority;
	}

	public C2SUpdateClientPreferences(FriendlyByteBuf buf) {
		this.containerId = buf.readInt();
		this.autoRefillAfterCraft = buf.readBoolean();
		this.includePlayerInventory = buf.readBoolean();
		this.sourcePriority = buf.readUtf();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(containerId);
		buf.writeBoolean(autoRefillAfterCraft);
		buf.writeBoolean(includePlayerInventory);
		buf.writeUtf(sourcePriority);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof NearbyCraftingMenu menu)) {
				return;
			}
			if (menu.containerId != containerId) {
				return;
			}

			NearbyCraftingConfig.SourcePriority resolvedPriority = NearbyCraftingConfig.SourcePriority.fromConfig(sourcePriority);
			menu.setClientPreferences(autoRefillAfterCraft, includePlayerInventory, resolvedPriority);
		});
		ctx.setPacketHandled(true);
	}
}
