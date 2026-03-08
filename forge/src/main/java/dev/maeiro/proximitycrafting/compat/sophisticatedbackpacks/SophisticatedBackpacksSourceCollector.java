package dev.maeiro.proximitycrafting.compat.sophisticatedbackpacks;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.service.source.ForgeItemSourceSlot;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class SophisticatedBackpacksSourceCollector {
	private SophisticatedBackpacksSourceCollector() {
	}

	public static List<ItemSourceRef> collect(Player player) {
		List<ItemSourceRef> sources = new ArrayList<>();
		Set<IItemHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

		PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventoryHandlerName, identifier, slot) -> {
			collectFromBackpackStack(sources, seenHandlers, backpack, inventoryHandlerName, identifier, slot);
			return false;
		});

		return sources;
	}

	private static void collectFromBackpackStack(List<ItemSourceRef> sink, Set<IItemHandler> seenHandlers, ItemStack backpackStack, String inventoryHandlerName, String identifier, int slot) {
		if (backpackStack.isEmpty()) {
			return;
		}

		try {
			backpackStack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance()).ifPresent(wrapper -> {
				IItemHandler handler = wrapper.getInventoryForInputOutput();
				if (handler == null || !seenHandlers.add(handler)) {
					return;
				}
				addHandlerSlots(sink, handler);
			});
		} catch (RuntimeException exception) {
			ProximityCrafting.LOGGER.warn(
					"Failed to collect sophisticated backpack sources from {}:{}:{}; skipping this backpack",
					inventoryHandlerName,
					identifier,
					slot,
					exception
			);
		}
	}

	private static void addHandlerSlots(List<ItemSourceRef> sink, IItemHandler handler) {
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			sink.add(new ItemSourceRef(new ForgeItemSourceSlot(handler, slot), ItemSourceRef.SourceType.PLAYER_BACKPACK, null));
		}
	}
}


