package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.service.source.ForgeItemSourceSlot;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.List;

public final class ForgePlayerInventorySourceCollector implements PlayerInventorySourceCollector {
	@Override
	public List<ItemSourceRef> collectPlayerInventorySources(Player player, ScanOptions scanOptions) {
		IItemHandler playerInventory = new InvWrapper(player.getInventory());
		List<ItemSourceRef> sources = new ArrayList<>();
		int playerSlots = Math.min(36, playerInventory.getSlots());
		for (int slot = 0; slot < playerSlots; slot++) {
			sources.add(new ItemSourceRef(new ForgeItemSourceSlot(playerInventory, slot), ItemSourceRef.SourceType.PLAYER, null));
		}
		return sources;
	}
}
