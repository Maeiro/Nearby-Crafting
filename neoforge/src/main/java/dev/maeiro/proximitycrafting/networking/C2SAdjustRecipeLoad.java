package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.AdjustRecipeLoadRequestPayload;
import dev.maeiro.proximitycrafting.networking.request.ServerMenuRequestController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

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

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			REQUEST_CONTROLLER.handleAdjustRecipeLoad(
					player,
					menu,
					RESPONSE_TRANSPORT,
					new AdjustRecipeLoadRequestPayload(steps)
			);
		});
		ctx.setPacketHandled(true);
	}
}
