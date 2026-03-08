package dev.maeiro.proximitycrafting.networking;

import dev.architectury.networking.NetworkManager;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.AdjustRecipeLoadRequestPayload;
import dev.maeiro.proximitycrafting.networking.request.ServerMenuRequestController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class C2SAdjustRecipeLoad {
	private static final ServerMenuRequestController REQUEST_CONTROLLER = new ServerMenuRequestController();
	private static final PlatformServerResponseTransport RESPONSE_TRANSPORT = new PlatformServerResponseTransport();
	private final int steps;

	public C2SAdjustRecipeLoad(int steps) {
		this.steps = steps;
	}

	public C2SAdjustRecipeLoad(FriendlyByteBuf buf) {
		this.steps = buf.readVarInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(steps);
	}

	public void handle(Supplier<NetworkManager.PacketContext> ctxSupplier) {
		NetworkManager.PacketContext ctx = ctxSupplier.get();
		ctx.queue(() -> {
			if (!(ctx.getPlayer() instanceof ServerPlayer player) || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			REQUEST_CONTROLLER.handleAdjustRecipeLoad(
					player,
					menu,
					RESPONSE_TRANSPORT,
					new AdjustRecipeLoadRequestPayload(steps)
			);
		});
	}
}
