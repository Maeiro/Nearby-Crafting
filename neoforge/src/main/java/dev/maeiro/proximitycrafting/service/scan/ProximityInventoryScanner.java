package dev.maeiro.proximitycrafting.service.scan;

import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.config.ServerRuntimeSettings;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ProximityInventoryScanner implements SourceCollector {
	private static final SourceScanRuntime RUNTIME = new SourceScanRuntime(
			new NeoForgeContainerDiscoveryPort(),
			new NeoForgePlayerInventorySourceCollector(),
			new NeoForgeBackpackSourceCollector()
	);
	public static final ProximityInventoryScanner INSTANCE = new ProximityInventoryScanner();

	private ProximityInventoryScanner() {
	}

	public static List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player) {
		return collectSources(level, centerPos, player, true, SourcePriority.CONTAINERS_FIRST);
	}

	public static List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player, boolean includePlayerInventory, SourcePriority priority) {
		return RUNTIME.collectSources(level, centerPos, player, runtimeSettings(), includePlayerInventory, priority);
	}

	public static List<ItemSourceRef> collectSourcesWithOptions(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions) {
		return RUNTIME.collectSources(level, centerPos, player, scanOptions, runtimeSettings().debugLogging());
	}

	@Override
	public List<ItemSourceRef> collectSources(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions) {
		return collectSourcesWithOptions(level, centerPos, player, scanOptions);
	}

	public static SourceCollectionResult collectSourceResult(Level level, BlockPos centerPos, Player player, ScanOptions scanOptions) {
		return RUNTIME.collectSourceResult(level, centerPos, player, scanOptions);
	}

	public static List<ItemSourceRef> collectPlayerSources(Player player, boolean includePlayerInventory) {
		return RUNTIME.collectPlayerSources(player, runtimeSettings(), includePlayerInventory, SourcePriority.CONTAINERS_FIRST);
	}

	public static List<ItemSourceRef> collectPlayerSources(Player player, ScanOptions scanOptions) {
		return RUNTIME.collectPlayerSources(player, scanOptions);
	}

	private static ServerRuntimeSettings runtimeSettings() {
		return ProximityCraftingConfig.serverRuntimeSettings();
	}
}
