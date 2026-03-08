package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.service.source.ForgeItemSourceSlot;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ForgeContainerSourceCollector implements ContainerSourceCollector {
	@Override
	public List<ItemSourceRef> collectContainerSources(Level level, BlockPos centerPos, ScanOptions scanOptions) {
		long startNs = System.nanoTime();
		int scanRadius = scanOptions.scanRadius();
		int minSlotCount = scanOptions.minSlotCount();
		Set<BlockEntityType<?>> blacklist = ProximityCraftingConfig.blockEntityBlacklist == null
				? Set.of()
				: new HashSet<>(ProximityCraftingConfig.blockEntityBlacklist);

		int minX = centerPos.getX() - scanRadius;
		int maxX = centerPos.getX() + scanRadius;
		int minY = centerPos.getY() - scanRadius;
		int maxY = centerPos.getY() + scanRadius;
		int minZ = centerPos.getZ() - scanRadius;
		int maxZ = centerPos.getZ() + scanRadius;

		List<BlockEntity> blockEntities = BlockPos.betweenClosedStream(minX, minY, minZ, maxX, maxY, maxZ)
				.map(level::getBlockEntity)
				.filter(Objects::nonNull)
				.filter(blockEntity -> !blacklist.contains(blockEntity.getType()))
				.filter(blockEntity -> blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
						.filter(handler -> handler.getSlots() >= minSlotCount)
						.isPresent())
				.sorted(Comparator.comparingDouble(blockEntity -> blockEntity.getBlockPos().distSqr(centerPos)))
				.collect(Collectors.toList());

		List<ItemSourceRef> sources = new ArrayList<>();
		for (BlockEntity blockEntity : blockEntities) {
			blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler ->
					addHandlerSlots(sources, handler, ItemSourceRef.SourceType.CONTAINER, blockEntity.getBlockPos())
			);
		}

		if (ProximityCraftingConfig.SERVER.debugLogging.get()) {
			int diameter = scanRadius * 2 + 1;
			long scannedPositions = (long) diameter * diameter * diameter;
			double totalMs = (System.nanoTime() - startNs) / 1_000_000.0D;
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] collectContainerSources center={} radius={} scannedPositions={} minSlots={} acceptedHandlers={} containerSlots={} took={}ms",
					centerPos,
					scanRadius,
					scannedPositions,
					minSlotCount,
					blockEntities.size(),
					sources.size(),
					String.format("%.3f", totalMs)
			);
		}
		return sources;
	}

	private static void addHandlerSlots(List<ItemSourceRef> sink, IItemHandler handler, ItemSourceRef.SourceType sourceType, BlockPos blockPos) {
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			sink.add(new ItemSourceRef(new ForgeItemSourceSlot(handler, slot), sourceType, blockPos));
		}
	}
}
