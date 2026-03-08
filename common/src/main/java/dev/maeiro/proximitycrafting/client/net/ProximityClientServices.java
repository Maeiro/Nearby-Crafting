package dev.maeiro.proximitycrafting.client.net;

import dev.maeiro.proximitycrafting.client.runtime.ClientRuntimeHooks;

import java.util.Objects;

public final class ProximityClientServices {
	private static volatile ClientRequestSender clientRequestSender;
	private static volatile ClientRuntimeHooks clientRuntimeHooks;
	private static volatile ClientResponseDispatcher clientResponseDispatcher;

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

	public static void registerClientRuntimeHooks(ClientRuntimeHooks runtimeHooks) {
		ClientRuntimeHooks hooks = Objects.requireNonNull(runtimeHooks, "runtimeHooks");
		clientRuntimeHooks = hooks;
		clientResponseDispatcher = new ClientResponseDispatcher(hooks);
	}

	public static ClientRuntimeHooks getClientRuntimeHooks() {
		ClientRuntimeHooks hooks = clientRuntimeHooks;
		if (hooks == null) {
			throw new IllegalStateException("ClientRuntimeHooks have not been registered for the active platform");
		}
		return hooks;
	}

	public static ClientResponseDispatcher getClientResponseDispatcher() {
		ClientResponseDispatcher dispatcher = clientResponseDispatcher;
		if (dispatcher == null) {
			throw new IllegalStateException("ClientResponseDispatcher has not been initialized for the active platform");
		}
		return dispatcher;
	}
}
