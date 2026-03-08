package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface MenuSnapshotTransport {
	void sendRecipeBookSourceSnapshot(ServerPlayer player, int containerId, List<RecipeBookSourceEntry> entries);
}
