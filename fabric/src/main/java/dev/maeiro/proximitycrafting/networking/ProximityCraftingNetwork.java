package dev.maeiro.proximitycrafting.networking;

import dev.architectury.networking.NetworkChannel;
import dev.maeiro.proximitycrafting.registry.ProximityBootstrapDescriptors;

public final class ProximityCraftingNetwork {
	public static final NetworkChannel CHANNEL = NetworkChannel.create(
			ProximityBootstrapDescriptors.NETWORK_CHANNEL.location()
	);

	private ProximityCraftingNetwork() {
	}

	public static void register() {
		CHANNEL.register(
				C2SRequestRecipeFill.class,
				C2SRequestRecipeFill::encode,
				C2SRequestRecipeFill::new,
				C2SRequestRecipeFill::handle
		);
		CHANNEL.register(
				C2SAdjustRecipeLoad.class,
				C2SAdjustRecipeLoad::encode,
				C2SAdjustRecipeLoad::new,
				C2SAdjustRecipeLoad::handle
		);
		CHANNEL.register(
				C2SClearCraftGrid.class,
				C2SClearCraftGrid::encode,
				C2SClearCraftGrid::new,
				C2SClearCraftGrid::handle
		);
		CHANNEL.register(
				C2SUpdateClientPreferences.class,
				C2SUpdateClientPreferences::encode,
				C2SUpdateClientPreferences::new,
				C2SUpdateClientPreferences::handle
		);
		CHANNEL.register(
				C2SRequestRecipeBookSources.class,
				C2SRequestRecipeBookSources::encode,
				C2SRequestRecipeBookSources::new,
				C2SRequestRecipeBookSources::handle
		);
		CHANNEL.register(
				S2CRecipeFillFeedback.class,
				S2CRecipeFillFeedback::encode,
				S2CRecipeFillFeedback::new,
				S2CRecipeFillFeedback::handle
		);
		CHANNEL.register(
				S2CRecipeBookSourceSnapshot.class,
				S2CRecipeBookSourceSnapshot::encode,
				S2CRecipeBookSourceSnapshot::new,
				S2CRecipeBookSourceSnapshot::handle
		);
	}
}
