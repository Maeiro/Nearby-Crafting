package dev.maeiro.proximitycrafting.networking;

import dev.architectury.networking.NetworkManager;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.ClearCraftGridRequestPayload;
import dev.maeiro.proximitycrafting.networking.request.ServerMenuRequestController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class C2SClearCraftGrid {
	private static final ServerMenuRequestController REQUEST_CONTROLLER = new ServerMenuRequestController();
	private static final PlatformServerResponseTransport RESPONSE_TRANSPORT = new PlatformServerResponseTransport();
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

	public void handle(Supplier<NetworkManager.PacketContext> ctxSupplier) {
		NetworkManager.PacketContext ctx = ctxSupplier.get();
		ctx.queue(() -> {
			if (!(ctx.getPlayer() instanceof ServerPlayer player) || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			REQUEST_CONTROLLER.handleClearCraftGrid(
					player,
					menu,
					RESPONSE_TRANSPORT,
					new ClearCraftGridRequestPayload(containerId)
			);
		});
	}
}
