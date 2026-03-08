package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public interface PlayerInventorySourceCollector {
	List<ItemSourceRef> collectPlayerInventorySources(Player player, ScanOptions scanOptions);
}
