package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SAdjustRecipeLoad {
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
			long startNs = System.nanoTime();
			ServerPlayer player = ctx.getSender();
			if (player == null || !(player.containerMenu instanceof ProximityCraftingMenu menu)) {
				if (isDebugLoggingEnabled()) {
					ProximityCrafting.LOGGER.info("[PROXC-SCROLL] server ignored packet: player/menu invalid");
				}
				return;
			}

			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-SCROLL] server received adjust packet steps={} menu={} player={}",
						steps,
						menu.containerId,
						player.getGameProfile().getName()
				);
			}
			long adjustStartNs = System.nanoTime();
			FillResult result = menu.adjustRecipeLoad(steps);
			long adjustEndNs = System.nanoTime();
			boolean shouldSendSnapshot = result.success() && result.craftedAmount() > 0;
			long snapshotStartNs = 0L;
			long snapshotEndNs = 0L;
			int snapshotEntryCount = 0;
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-SCROLL] server adjust result success={} key={} amount={}",
						result.success(),
						result.messageKey(),
						result.craftedAmount()
				);
			}
			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new S2CRecipeFillFeedback(result.success(), result.messageKey(), result.craftedAmount())
			);
			if (shouldSendSnapshot) {
				snapshotStartNs = System.nanoTime();
				var snapshotEntries = RecipeBookSourceSnapshotBuilder.build(menu);
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
						"[PROXC-PERF] packet.C2SAdjustRecipeLoad player={} menu={} steps={} success={} amount={} adjustMs={} snapshotMs={} totalMs={} snapshotEntries={}",
						player.getGameProfile().getName(),
						menu.containerId,
						steps,
						result.success(),
						result.craftedAmount(),
						String.format("%.3f", (adjustEndNs - adjustStartNs) / 1_000_000.0D),
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
