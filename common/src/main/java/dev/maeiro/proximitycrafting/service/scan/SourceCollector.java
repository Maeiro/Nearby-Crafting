package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public interface SourceCollector {
	List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions);
}
