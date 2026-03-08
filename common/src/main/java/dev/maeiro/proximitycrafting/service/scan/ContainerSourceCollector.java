package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public interface ContainerSourceCollector {
	List<ItemSourceRef> collectContainerSources(Level level, BlockPos centerPos, ScanOptions scanOptions);
}
