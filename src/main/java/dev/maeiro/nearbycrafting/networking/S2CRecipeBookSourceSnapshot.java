package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.client.screen.NearbyCraftingScreen;
import dev.maeiro.nearbycrafting.menu.NearbyCraftingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CRecipeBookSourceSnapshot {
	private final int containerId;
	private final List<NearbyCraftingMenu.RecipeBookSourceEntry> sourceEntries;

	public S2CRecipeBookSourceSnapshot(int containerId, List<NearbyCraftingMenu.RecipeBookSourceEntry> sourceEntries) {
		this.containerId = containerId;
		this.sourceEntries = sourceEntries == null ? List.of() : List.copyOf(sourceEntries);
	}

	public S2CRecipeBookSourceSnapshot(FriendlyByteBuf buf) {
		this.containerId = buf.readInt();
		int entryCount = buf.readVarInt();
		List<NearbyCraftingMenu.RecipeBookSourceEntry> decodedEntries = new ArrayList<>(entryCount);
		for (int i = 0; i < entryCount; i++) {
			ItemStack stack = buf.readItem();
			int count = buf.readVarInt();
			if (!stack.isEmpty() && count > 0) {
				decodedEntries.add(new NearbyCraftingMenu.RecipeBookSourceEntry(stack, count));
			}
		}
		this.sourceEntries = List.copyOf(decodedEntries);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(containerId);
		buf.writeVarInt(sourceEntries.size());
		for (NearbyCraftingMenu.RecipeBookSourceEntry sourceEntry : sourceEntries) {
			buf.writeItem(sourceEntry.stack());
			buf.writeVarInt(sourceEntry.count());
		}
	}

	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || !(minecraft.player.containerMenu instanceof NearbyCraftingMenu menu)) {
				return;
			}
			if (menu.containerId != containerId) {
				return;
			}

			menu.setClientRecipeBookSupplementalSources(sourceEntries);
			if (minecraft.screen instanceof NearbyCraftingScreen nearbyCraftingScreen) {
				nearbyCraftingScreen.refreshRecipeBookFromSyncedSources();
			}
		});
		ctx.setPacketHandled(true);
	}
}
