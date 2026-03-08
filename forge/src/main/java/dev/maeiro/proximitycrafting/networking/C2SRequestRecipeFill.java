package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.networking.payload.RecipeFillRequestPayload;
import dev.maeiro.proximitycrafting.networking.request.ServerMenuRequestController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SRequestRecipeFill {
	private static final ServerMenuRequestController REQUEST_CONTROLLER = new ServerMenuRequestController();
	private static final PlatformServerResponseTransport RESPONSE_TRANSPORT = new PlatformServerResponseTransport();
	private final ResourceLocation recipeId;
	private final boolean craftAll;

	public C2SRequestRecipeFill(ResourceLocation recipeId, boolean craftAll) {
		this.recipeId = recipeId;
		this.craftAll = craftAll;
	}

	public C2SRequestRecipeFill(FriendlyByteBuf buf) {
		this.recipeId = buf.readResourceLocation();
		this.craftAll = buf.readBoolean();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeResourceLocation(recipeId);
		buf.writeBoolean(craftAll);
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			REQUEST_CONTROLLER.handleRecipeFill(
					player,
					menu,
					RESPONSE_TRANSPORT,
					new RecipeFillRequestPayload(recipeId, craftAll)
			);
		});
		ctx.setPacketHandled(true);
	}
}
