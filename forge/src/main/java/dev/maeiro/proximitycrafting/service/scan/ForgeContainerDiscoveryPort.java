package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ForgeItemSourceSlot;
import dev.maeiro.proximitycrafting.service.source.ItemSourceSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ForgeContainerDiscoveryPort implements ContainerDiscoveryPort {
	@Override
	public Optional<DiscoveredContainer> discoverContainer(Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity == null) {
			return Optional.empty();
		}

		return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
				.filter(handler -> handler.getSlots() > 0)
				.map(handler -> new DiscoveredContainer(
						resolveTypeId(blockEntity),
						blockEntity.getBlockPos().immutable(),
						collectSlots(handler)
				));
	}

	private static String resolveTypeId(BlockEntity blockEntity) {
		ResourceLocation key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
		return key == null ? "" : key.toString();
	}

	private static List<ItemSourceSlot> collectSlots(IItemHandler handler) {
		List<ItemSourceSlot> slots = new ArrayList<>(handler.getSlots());
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			slots.add(new ForgeItemSourceSlot(handler, slot));
		}
		return slots;
	}
}
