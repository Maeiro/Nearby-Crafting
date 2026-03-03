package dev.maeiro.nearbycrafting.networking;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NearbyCraftingNetwork {
	private static final String PROTOCOL_VERSION = "4";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(NearbyCrafting.MOD_ID, "main"),
			() -> PROTOCOL_VERSION,
			PROTOCOL_VERSION::equals,
			PROTOCOL_VERSION::equals
	);

	private NearbyCraftingNetwork() {
	}

	public static void register() {
		int id = 0;
		CHANNEL.registerMessage(
				id++,
				C2SRequestRecipeFill.class,
				C2SRequestRecipeFill::encode,
				C2SRequestRecipeFill::new,
				C2SRequestRecipeFill::handle
		);
		CHANNEL.registerMessage(
				id++,
				C2SAdjustRecipeLoad.class,
				C2SAdjustRecipeLoad::encode,
				C2SAdjustRecipeLoad::new,
				C2SAdjustRecipeLoad::handle
		);
		CHANNEL.registerMessage(
				id++,
				C2SUpdateClientPreferences.class,
				C2SUpdateClientPreferences::encode,
				C2SUpdateClientPreferences::new,
				C2SUpdateClientPreferences::handle
		);
		CHANNEL.registerMessage(
				id++,
				C2SRequestRecipeBookSources.class,
				C2SRequestRecipeBookSources::encode,
				C2SRequestRecipeBookSources::new,
				C2SRequestRecipeBookSources::handle
		);
		CHANNEL.registerMessage(
				id++,
				S2CRecipeFillFeedback.class,
				S2CRecipeFillFeedback::encode,
				S2CRecipeFillFeedback::new,
				S2CRecipeFillFeedback::handle
		);
		CHANNEL.registerMessage(
				id,
				S2CRecipeBookSourceSnapshot.class,
				S2CRecipeBookSourceSnapshot::encode,
				S2CRecipeBookSourceSnapshot::new,
				S2CRecipeBookSourceSnapshot::handle
		);
	}
}
