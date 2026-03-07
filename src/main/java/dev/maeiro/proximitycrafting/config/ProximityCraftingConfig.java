package dev.maeiro.proximitycrafting.config;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Locale;
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

	public enum SourcePriority {
		CONTAINERS_FIRST,
		PLAYER_FIRST;

		public static SourcePriority fromConfig(String value) {
			try {
				return SourcePriority.valueOf(value.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ex) {
				return CONTAINERS_FIRST;
			}
		}
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
					.defineInRange("scanRadius", 6, 0, Integer.MAX_VALUE);

			minSlotCount = builder
					.comment("Minimum number of slots for a container to be considered as an ingredient source.")
					.defineInRange("minSlotCount", 6, 0, Integer.MAX_VALUE);

			blacklistedBlockEntities = builder
					.comment("Block entity ids excluded from proximity ingredient sources.")
					.define("blockEntityBlacklist", List.of(
							"minecraft:furnace",
							"minecraft:blast_furnace",
							"minecraft:smoker"
					));

			maxShiftCraftIterations = builder
					.comment("Maximum number of recipe units to place in the grid during max-transfer operations.")
					.defineInRange("maxShiftCraftIterations", 64, 1, 4096);

			debugLogging = builder
					.comment("When true, logs debug information for scanning and recipe filling.")
					.define("debugLogging", false);

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
					.define("autoRefillAfterCraft", true);

			includePlayerInventory = builder
					.comment("When true, player inventory slots are included as ingredient sources.")
					.define("includePlayerInventory", true);

			sourcePriority = builder
					.comment("Source priority for ingredient extraction. Allowed values: CONTAINERS_FIRST, PLAYER_FIRST")
					.define("sourcePriority", SourcePriority.CONTAINERS_FIRST.name());

			rememberToggleStates = builder
					.comment("When true, remembers UI toggle states between table openings (Ingredients panel and Craftable Only toggles).")
					.define("rememberToggleStates", true);

			proximityItemsPanelOpen = builder
					.comment("Last remembered state for the Ingredients Panel.")
					.define("proximityItemsPanelOpen", true);

			proximityItemsPanelOffsetX = builder
					.comment("Ingredients panel X offset. Negative moves left, positive moves right.")
					.defineInRange("proximityItemsPanelOffsetX", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

			proximityItemsPanelOffsetY = builder
					.comment("Ingredients panel Y offset. Negative moves up, positive moves down.")
					.defineInRange("proximityItemsPanelOffsetY", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

			jeiCraftableOnlyEnabled = builder
					.comment("Last remembered state for JEI Craftable Only toggle.")
					.define("jeiCraftableOnlyEnabled", false);

			emiCraftableOnlyEnabled = builder
					.comment("Last remembered state for EMI Craftable Only toggle.")
					.define("emiCraftableOnlyEnabled", false);

			debugLogging = builder
					.comment("When true, enables client-side debug/performance logging (PROXC-CLIENT/PROXC-SCROLL). Server logs remain controlled by server debugLogging.")
					.define("debugLogging", false);

			builder.pop();
		}
	}
}


