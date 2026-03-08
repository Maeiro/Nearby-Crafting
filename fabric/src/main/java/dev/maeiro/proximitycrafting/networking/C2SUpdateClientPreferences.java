package dev.maeiro.proximitycrafting.networking;

import dev.architectury.networking.NetworkManager;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.UpdateClientPreferencesRequestPayload;
import dev.maeiro.proximitycrafting.networking.request.ServerMenuRequestController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class C2SUpdateClientPreferences {
	private static final ServerMenuRequestController REQUEST_CONTROLLER = new ServerMenuRequestController();
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

	public void handle(Supplier<NetworkManager.PacketContext> ctxSupplier) {
		NetworkManager.PacketContext ctx = ctxSupplier.get();
		ctx.queue(() -> {
			if (!(ctx.getPlayer() instanceof ServerPlayer player) || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			REQUEST_CONTROLLER.handleUpdateClientPreferences(
					menu,
					new UpdateClientPreferencesRequestPayload(
							containerId,
							autoRefillAfterCraft,
							includePlayerInventory,
							sourcePriority
					)
			);
		});
	}
}
