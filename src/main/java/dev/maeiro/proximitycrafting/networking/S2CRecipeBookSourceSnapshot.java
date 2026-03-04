package dev.maeiro.proximitycrafting.networking;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.client.screen.ProximityCraftingScreen;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CRecipeBookSourceSnapshot {
	private final int containerId;
	private final List<ProximityCraftingMenu.RecipeBookSourceEntry> sourceEntries;

	public S2CRecipeBookSourceSnapshot(int containerId, List<ProximityCraftingMenu.RecipeBookSourceEntry> sourceEntries) {
		this.containerId = containerId;
		this.sourceEntries = sourceEntries == null ? List.of() : List.copyOf(sourceEntries);
	}

	public S2CRecipeBookSourceSnapshot(FriendlyByteBuf buf) {
		this.containerId = buf.readInt();
		int entryCount = buf.readVarInt();
		List<ProximityCraftingMenu.RecipeBookSourceEntry> decodedEntries = new ArrayList<>(entryCount);
		for (int i = 0; i < entryCount; i++) {
			ItemStack stack = buf.readItem();
			int count = buf.readVarInt();
			if (!stack.isEmpty() && count > 0) {
				decodedEntries.add(new ProximityCraftingMenu.RecipeBookSourceEntry(stack, count));
			}
		}
		this.sourceEntries = List.copyOf(decodedEntries);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(containerId);
		buf.writeVarInt(sourceEntries.size());
		for (ProximityCraftingMenu.RecipeBookSourceEntry sourceEntry : sourceEntries) {
			buf.writeItem(sourceEntry.stack());
			buf.writeVarInt(sourceEntry.count());
		}
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			long startNs = System.nanoTime();
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || !(minecraft.player.containerMenu instanceof ProximityCraftingMenu menu)) {
				return;
			}
			if (menu.containerId != containerId) {
				return;
			}

			boolean sourcesChanged = menu.setClientRecipeBookSupplementalSources(sourceEntries);
			if (minecraft.screen instanceof ProximityCraftingScreen proximityCraftingScreen) {
				proximityCraftingScreen.onSourceSnapshotAppliedClient(sourceEntries.size());
				if (sourcesChanged) {
					proximityCraftingScreen.scheduleDeferredRecipeBookRefresh();
				}
			}
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PC-PERF] packet.S2CRecipeBookSourceSnapshot menu={} entries={} applyMs={}",
						containerId,
						sourceEntries.size(),
						String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
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


