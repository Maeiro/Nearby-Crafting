package dev.maeiro.nearbycrafting.service.source;

import net.minecraft.core.BlockPos;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Objects;

public class ItemSourceRef {
	private final IItemHandler handler;
	private final int slot;
	private final SourceType sourceType;
	@Nullable
	private final BlockPos blockPos;

	public ItemSourceRef(IItemHandler handler, int slot, SourceType sourceType, @Nullable BlockPos blockPos) {
		this.handler = handler;
		this.slot = slot;
		this.sourceType = sourceType;
		this.blockPos = blockPos;
	}

	public IItemHandler handler() {
		return handler;
	}

	public int slot() {
		return slot;
	}

	public SourceType sourceType() {
		return sourceType;
	}

	@Nullable
	public BlockPos blockPos() {
		return blockPos;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ItemSourceRef that)) return false;
		return slot == that.slot && handler == that.handler;
	}

	@Override
	public int hashCode() {
		return Objects.hash(System.identityHashCode(handler), slot);
	}

	public enum SourceType {
		CONTAINER,
		PLAYER
	}
}

