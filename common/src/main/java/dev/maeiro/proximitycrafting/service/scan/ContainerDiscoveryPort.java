package dev.maeiro.proximitycrafting.service.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Optional;

public interface ContainerDiscoveryPort {
	Optional<DiscoveredContainer> discoverContainer(Level level, BlockPos pos);
}
