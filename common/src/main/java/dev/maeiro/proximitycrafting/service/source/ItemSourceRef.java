package dev.maeiro.proximitycrafting.service.source;

import net.minecraft.core.BlockPos;
import java.util.Objects;

public class ItemSourceRef {
	private final ItemSourceSlot slotRef;
	private final SourceType sourceType;
	private final BlockPos blockPos;
	private final SlotIdentity identity;

	public ItemSourceRef(ItemSourceSlot slotRef, SourceType sourceType, BlockPos blockPos) {
		this.slotRef = slotRef;
		this.sourceType = sourceType;
		this.blockPos = blockPos;
		this.identity = slotRef.identity();
	}

	public ItemSourceSlot slotRef() {
		return slotRef;
	}

	public int slot() {
		return identity.slotIndex();
	}

	public SourceType sourceType() {
		return sourceType;
	}

	public BlockPos blockPos() {
		return blockPos;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ItemSourceRef that)) return false;
		return Objects.equals(identity, that.identity);
	}

	@Override
	public int hashCode() {
		return Objects.hash(identity);
	}

	public enum SourceType {
		CONTAINER,
		PLAYER,
		PLAYER_BACKPACK
	}
}

