package dev.maeiro.proximitycrafting.networking.payload;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record RecipeBookSourceSnapshotPayload(int containerId, List<RecipeBookSourceEntry> sourceEntries) {
	public RecipeBookSourceSnapshotPayload {
		sourceEntries = sourceEntries == null ? List.of() : List.copyOf(sourceEntries);
	}

	public static RecipeBookSourceSnapshotPayload decode(FriendlyByteBuf buf) {
		int containerId = buf.readInt();
		int entryCount = buf.readVarInt();
		List<RecipeBookSourceEntry> decodedEntries = new ArrayList<>(entryCount);
		for (int i = 0; i < entryCount; i++) {
			RecipeBookSourceEntry entry = RecipeBookSourceEntry.decode(buf);
			if (!entry.stack().isEmpty() && entry.count() > 0) {
				decodedEntries.add(entry);
			}
		}
		return new RecipeBookSourceSnapshotPayload(containerId, decodedEntries);
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(containerId);
		buf.writeVarInt(sourceEntries.size());
		for (RecipeBookSourceEntry sourceEntry : sourceEntries) {
			sourceEntry.encode(buf);
		}
	}
}
