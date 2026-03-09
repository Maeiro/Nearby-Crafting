package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class ForgeBackpackSourceCollector implements PlayerBackpackSourceCollector {
	@Override
	public List<ItemSourceRef> collectPlayerBackpackSources(Player player, ScanOptions scanOptions) {
		return List.of();
	}
}
