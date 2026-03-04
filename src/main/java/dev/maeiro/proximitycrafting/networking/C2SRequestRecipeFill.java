package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

public class C2SRequestRecipeFill {
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
			long startNs = System.nanoTime();
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}

			long fillStartNs = System.nanoTime();
			FillResult result = menu.fillRecipeById(recipeId, craftAll);
			long fillEndNs = System.nanoTime();
			boolean shouldSendSnapshot = result.success() && result.craftedAmount() > 0;
			long snapshotStartNs = 0L;
			long snapshotEndNs = 0L;
			int snapshotEntryCount = 0;
			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeFillFeedback(result.success(), result.messageKey(), result.craftedAmount())
			);
			if (shouldSendSnapshot) {
				snapshotStartNs = System.nanoTime();
				List<ProximityCraftingMenu.RecipeBookSourceEntry> snapshotEntries = RecipeBookSourceSnapshotBuilder.build(menu);
				snapshotEndNs = System.nanoTime();
				snapshotEntryCount = snapshotEntries.size();
				ProximityCraftingNetwork.CHANNEL.send(
						PacketDistributor.PLAYER.with(() -> player),
						new S2CRecipeBookSourceSnapshot(
								menu.containerId,
								snapshotEntries
						)
				);
			}

			if (isDebugLoggingEnabled()) {
				double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] packet.C2SRequestRecipeFill player={} menu={} recipe={} craftAll={} success={} amount={} fillMs={} snapshotMs={} totalMs={} snapshotEntries={}",
						player.getGameProfile().getName(),
						menu.containerId,
						recipeId,
						craftAll,
						result.success(),
						result.craftedAmount(),
						String.format("%.3f", (fillEndNs - fillStartNs) / 1_000_000.0D),
						String.format("%.3f", shouldSendSnapshot ? (snapshotEndNs - snapshotStartNs) / 1_000_000.0D : 0.0D),
						String.format("%.3f", totalMs),
						snapshotEntryCount
				);
			}
		});
		ctx.setPacketHandled(true);
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
	}
}
