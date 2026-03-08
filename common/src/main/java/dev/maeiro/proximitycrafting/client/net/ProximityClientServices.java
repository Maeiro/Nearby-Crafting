package dev.maeiro.proximitycrafting.client.net;

import java.util.Objects;

public final class ProximityClientServices {
	private static volatile ClientRequestSender clientRequestSender;

	private ProximityClientServices() {
	}

	public static void registerClientRequestSender(ClientRequestSender sender) {
		clientRequestSender = Objects.requireNonNull(sender, "sender");
	}

	public static ClientRequestSender getClientRequestSender() {
		ClientRequestSender sender = clientRequestSender;
		if (sender == null) {
			throw new IllegalStateException("ClientRequestSender has not been registered for the active platform");
		}
		return sender;
	}
}
