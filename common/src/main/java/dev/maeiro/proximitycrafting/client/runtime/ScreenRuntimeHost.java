package dev.maeiro.proximitycrafting.client.runtime;

import dev.maeiro.proximitycrafting.client.net.ClientRequestSender;
import dev.maeiro.proximitycrafting.client.session.ClientRecipeSessionState;

public interface ScreenRuntimeHost {
	int containerId();

	ClientRequestSender requestSender();

	ClientRecipeSessionState sessionState();

	boolean debugLoggingEnabled();
}

