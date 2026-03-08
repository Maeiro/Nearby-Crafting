package dev.maeiro.proximitycrafting.registry;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import net.minecraft.resources.ResourceLocation;

public record ProximityId(String path) {
	public ResourceLocation location() {
		return ProximityCrafting.id(path);
	}
}
