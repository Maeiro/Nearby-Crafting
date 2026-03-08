package dev.maeiro.proximitycrafting.service.crafting;

public interface MenuRuntimeHost {
	int containerId();

	boolean debugLoggingEnabled();

	boolean isClientSide();
}
