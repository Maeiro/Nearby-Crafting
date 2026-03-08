package dev.maeiro.proximitycrafting.config;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ClientPreferences;
import dev.maeiro.proximitycrafting.config.ClientUiState;
import dev.maeiro.proximitycrafting.config.ProximityConfigDefaults;
import dev.maeiro.proximitycrafting.config.ServerRuntimeSettings;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.stream.Collectors;

public class ProximityCraftingConfig {
	public static final Server SERVER;
	public static final ForgeConfigSpec SERVER_SPEC;
	public static final Client CLIENT;
	public static final ForgeConfigSpec CLIENT_SPEC;

	public static List<BlockEntityType<?>> blockEntityBlacklist;

	static {
		Pair<Server, ForgeConfigSpec> serverSpecPair = new ForgeConfigSpec.Builder().configure(Server::new);
		SERVER = serverSpecPair.getLeft();
		SERVER_SPEC = serverSpecPair.getRight();

		Pair<Client, ForgeConfigSpec> clientSpecPair = new ForgeConfigSpec.Builder().configure(Client::new);
		CLIENT = clientSpecPair.getLeft();
		CLIENT_SPEC = clientSpecPair.getRight();
	}

	private ProximityCraftingConfig() {
	}

	public static void onConfigChanged(final ModConfigEvent event) {
		if (!event.getConfig().getModId().equals(ProximityCrafting.MOD_ID)) {
			return;
		}
		if (event.getConfig().getSpec() != SERVER_SPEC) {
			return;
		}

		blockEntityBlacklist = SERVER.blacklistedBlockEntities.get()
				.stream()
				.map(ResourceLocation::new)
				.filter(resourceLocation -> {
					boolean exists = ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(resourceLocation);
					if (!exists) {
						ProximityCrafting.LOGGER.warn("Ignoring unknown block entity in blacklist: {}", resourceLocation);
					}
					return exists;
				})
				.map(((ForgeRegistry<BlockEntityType<?>>) ForgeRegistries.BLOCK_ENTITY_TYPES)::getValue)
				.collect(Collectors.toList());
	}

	public static boolean isServerDebugLoggingEnabled() {
		try {
			return SERVER.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public static boolean isClientDebugLoggingEnabled() {
		try {
			return CLIENT.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public static ServerRuntimeSettings serverRuntimeSettings() {
		try {
			return ServerRuntimeSettings.of(
					SERVER.scanRadius.get(),
					SERVER.minSlotCount.get(),
					SERVER.maxShiftCraftIterations.get(),
					SERVER.debugLogging.get(),
					SERVER.blacklistedBlockEntities.get()
			);
		} catch (RuntimeException exception) {
			return ServerRuntimeSettings.defaults();
		}
	}

	public static ClientPreferences clientPreferences() {
		try {
			return ClientPreferences.fromConfigValues(
					CLIENT.autoRefillAfterCraft.get(),
					CLIENT.includePlayerInventory.get(),
					CLIENT.sourcePriority.get()
			);
		} catch (RuntimeException exception) {
			return ClientPreferences.defaults();
		}
	}

	public static void setClientPreferences(ClientPreferences preferences) {
		ClientPreferences resolved = preferences == null ? ClientPreferences.defaults() : preferences;
		CLIENT.autoRefillAfterCraft.set(resolved.autoRefillAfterCraft());
		CLIENT.includePlayerInventory.set(resolved.includePlayerInventory());
		CLIENT.sourcePriority.set(resolved.sourcePriorityValue());
	}

	public static ClientUiState clientUiState() {
		try {
			return new ClientUiState(
					CLIENT.rememberToggleStates.get(),
					CLIENT.proximityItemsPanelOpen.get(),
					CLIENT.proximityItemsPanelOffsetX.get(),
					CLIENT.proximityItemsPanelOffsetY.get(),
					CLIENT.jeiCraftableOnlyEnabled.get(),
					CLIENT.emiCraftableOnlyEnabled.get(),
					CLIENT.debugLogging.get()
			);
		} catch (RuntimeException exception) {
			return ClientUiState.defaults();
		}
	}

	public static void setClientUiState(ClientUiState uiState) {
		ClientUiState resolved = uiState == null ? ClientUiState.defaults() : uiState;
		CLIENT.rememberToggleStates.set(resolved.rememberToggleStates());
		CLIENT.proximityItemsPanelOpen.set(resolved.ingredientsPanelOpen());
		CLIENT.proximityItemsPanelOffsetX.set(resolved.ingredientsPanelOffsetX());
		CLIENT.proximityItemsPanelOffsetY.set(resolved.ingredientsPanelOffsetY());
		CLIENT.jeiCraftableOnlyEnabled.set(resolved.jeiCraftableOnlyEnabled());
		CLIENT.emiCraftableOnlyEnabled.set(resolved.emiCraftableOnlyEnabled());
		CLIENT.debugLogging.set(resolved.debugLogging());
	}

	public static class Server {
		public final ForgeConfigSpec.IntValue scanRadius;
		public final ForgeConfigSpec.IntValue minSlotCount;
		public final ForgeConfigSpec.ConfigValue<List<String>> blacklistedBlockEntities;
		public final ForgeConfigSpec.IntValue maxShiftCraftIterations;
		public final ForgeConfigSpec.BooleanValue debugLogging;

		Server(ForgeConfigSpec.Builder builder) {
			builder.push("proximityCrafting");

			scanRadius = builder
					.comment("Radius in blocks to scan for proximity item handlers around the proximity crafting table.")
					.defineInRange("scanRadius", ProximityConfigDefaults.SERVER_SCAN_RADIUS, 0, Integer.MAX_VALUE);

			minSlotCount = builder
					.comment("Minimum number of slots for a container to be considered as an ingredient source.")
					.defineInRange("minSlotCount", ProximityConfigDefaults.SERVER_MIN_SLOT_COUNT, 0, Integer.MAX_VALUE);

			blacklistedBlockEntities = builder
					.comment("Block entity ids excluded from proximity ingredient sources.")
					.define("blockEntityBlacklist", ProximityConfigDefaults.SERVER_BLOCK_ENTITY_BLACKLIST);

			maxShiftCraftIterations = builder
					.comment("Maximum number of recipe units to place in the grid during max-transfer operations.")
					.defineInRange("maxShiftCraftIterations", ProximityConfigDefaults.SERVER_MAX_SHIFT_CRAFT_ITERATIONS, 1, 4096);

			debugLogging = builder
					.comment("When true, logs debug information for scanning and recipe filling.")
					.define("debugLogging", ProximityConfigDefaults.SERVER_DEBUG_LOGGING);

			builder.pop();
		}
	}

	public static class Client {
		public final ForgeConfigSpec.BooleanValue autoRefillAfterCraft;
		public final ForgeConfigSpec.BooleanValue includePlayerInventory;
		public final ForgeConfigSpec.ConfigValue<String> sourcePriority;
		public final ForgeConfigSpec.BooleanValue rememberToggleStates;
		public final ForgeConfigSpec.BooleanValue proximityItemsPanelOpen;
		public final ForgeConfigSpec.IntValue proximityItemsPanelOffsetX;
		public final ForgeConfigSpec.IntValue proximityItemsPanelOffsetY;
		public final ForgeConfigSpec.BooleanValue jeiCraftableOnlyEnabled;
		public final ForgeConfigSpec.BooleanValue emiCraftableOnlyEnabled;
		public final ForgeConfigSpec.BooleanValue debugLogging;

		Client(ForgeConfigSpec.Builder builder) {
			builder.push("proximityCrafting");

			autoRefillAfterCraft = builder
					.comment("When true, automatically refills the crafting grid after taking a crafted item.")
					.define("autoRefillAfterCraft", ProximityConfigDefaults.CLIENT_AUTO_REFILL_AFTER_CRAFT);

			includePlayerInventory = builder
					.comment("When true, player inventory slots are included as ingredient sources.")
					.define("includePlayerInventory", ProximityConfigDefaults.CLIENT_INCLUDE_PLAYER_INVENTORY);

			sourcePriority = builder
					.comment("Source priority for ingredient extraction. Allowed values: CONTAINERS_FIRST, PLAYER_FIRST")
					.define("sourcePriority", ProximityConfigDefaults.CLIENT_SOURCE_PRIORITY.name());

			rememberToggleStates = builder
					.comment("When true, remembers UI toggle states between table openings (Ingredients panel and Craftable Only toggles).")
					.define("rememberToggleStates", ProximityConfigDefaults.CLIENT_REMEMBER_TOGGLE_STATES);

			proximityItemsPanelOpen = builder
					.comment("Last remembered state for the Ingredients panel.")
					.define("proximityItemsPanelOpen", ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OPEN);

			proximityItemsPanelOffsetX = builder
					.comment("Ingredients panel X offset. Negative moves left, positive moves right.")
					.defineInRange("proximityItemsPanelOffsetX", ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OFFSET_X, Integer.MIN_VALUE, Integer.MAX_VALUE);

			proximityItemsPanelOffsetY = builder
					.comment("Ingredients panel Y offset. Negative moves up, positive moves down.")
					.defineInRange("proximityItemsPanelOffsetY", ProximityConfigDefaults.CLIENT_INGREDIENTS_PANEL_OFFSET_Y, Integer.MIN_VALUE, Integer.MAX_VALUE);

			jeiCraftableOnlyEnabled = builder
					.comment("Last remembered state for JEI Craftable Only toggle.")
					.define("jeiCraftableOnlyEnabled", ProximityConfigDefaults.CLIENT_JEI_CRAFTABLE_ONLY_ENABLED);

			emiCraftableOnlyEnabled = builder
					.comment("Last remembered state for EMI Craftable Only toggle.")
					.define("emiCraftableOnlyEnabled", ProximityConfigDefaults.CLIENT_EMI_CRAFTABLE_ONLY_ENABLED);

			debugLogging = builder
					.comment("When true, enables client-side debug/performance logging (PROXC-CLIENT/PROXC-SCROLL). Server logs remain controlled by server debugLogging.")
					.define("debugLogging", ProximityConfigDefaults.CLIENT_DEBUG_LOGGING);

			builder.pop();
		}
	}
}


