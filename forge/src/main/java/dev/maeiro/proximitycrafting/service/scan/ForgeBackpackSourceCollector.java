package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.compat.sophisticatedbackpacks.SophisticatedBackpacksSourceCollector;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.List;

public final class ForgeBackpackSourceCollector implements PlayerBackpackSourceCollector {
	private static final String SOPHISTICATED_BACKPACKS_MOD_ID = "sophisticatedbackpacks";

	@Override
	public List<ItemSourceRef> collectPlayerBackpackSources(Player player, ScanOptions scanOptions) {
		if (!ModList.get().isLoaded(SOPHISTICATED_BACKPACKS_MOD_ID)) {
			return List.of();
		}
		try {
			return SophisticatedBackpacksSourceCollector.collect(player);
		} catch (LinkageError | RuntimeException exception) {
			ProximityCrafting.LOGGER.warn("Failed to collect Sophisticated Backpacks sources; skipping backpack sources", exception);
			return List.of();
		}
	}
}
