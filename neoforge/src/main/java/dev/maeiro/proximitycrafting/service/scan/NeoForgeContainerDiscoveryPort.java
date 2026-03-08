package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.NeoForgeItemSourceSlot;
import dev.maeiro.proximitycrafting.service.source.ItemSourceSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NeoForgeContainerDiscoveryPort implements ContainerDiscoveryPort {
	@Override
	public Optional<DiscoveredContainer> discoverContainer(Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof Container container)) {
			return Optional.empty();
		}

		List<ItemSourceSlot> slots = new ArrayList<>(container.getContainerSize());
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			slots.add(new NeoForgeItemSourceSlot(container, slot));
		}
		if (slots.isEmpty()) {
			return Optional.empty();
		}

		ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
		return Optional.of(new DiscoveredContainer(
				key == null ? "" : key.toString(),
				blockEntity.getBlockPos().immutable(),
				slots
		));
	}
}
