package dev.maeiro.proximitycrafting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.resources.ResourceLocation;

public class ProximityCrafting {
	public static final String MOD_ID = "proximitycrafting";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	public static void init() {
		// Common bootstrap placeholder for Architectury modules.
	}
}


