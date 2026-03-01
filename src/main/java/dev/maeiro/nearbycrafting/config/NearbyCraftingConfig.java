package dev.maeiro.nearbycrafting.config;

import dev.maeiro.nearbycrafting.NearbyCrafting;
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

public class NearbyCraftingConfig {
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

	private NearbyCraftingConfig() {
	}

	public static void onConfigChanged(final ModConfigEvent event) {
		if (!event.getConfig().getModId().equals(NearbyCrafting.MOD_ID)) {
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
						NearbyCrafting.LOGGER.warn("Ignoring unknown block entity in blacklist: {}", resourceLocation);
					}
					return exists;
				})
				.map(((ForgeRegistry<BlockEntityType<?>>) ForgeRegistries.BLOCK_ENTITY_TYPES)::getValue)
				.collect(Collectors.toList());
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
			builder.push("nearbyCrafting");

			scanRadius = builder
					.comment("Radius in blocks to scan for nearby item handlers around the nearby crafting table.")
					.defineInRange("scanRadius", 6, 0, Integer.MAX_VALUE);

			minSlotCount = builder
					.comment("Minimum number of slots for a container to be considered as an ingredient source.")
					.defineInRange("minSlotCount", 6, 0, Integer.MAX_VALUE);

			blacklistedBlockEntities = builder
					.comment("Block entity ids excluded from nearby ingredient sources.")
					.define("blockEntityBlacklist", List.of(
							"minecraft:furnace",
							"minecraft:blast_furnace",
							"minecraft:smoker"
					));

			maxShiftCraftIterations = builder
					.comment("Maximum number of repeated crafts for craft-all operations.")
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

		Client(ForgeConfigSpec.Builder builder) {
			builder.push("nearbyCrafting");

			autoRefillAfterCraft = builder
					.comment("When true, automatically refills the crafting grid after taking a crafted item.")
					.define("autoRefillAfterCraft", false);

			includePlayerInventory = builder
					.comment("When true, player inventory slots are included as ingredient sources.")
					.define("includePlayerInventory", true);

			sourcePriority = builder
					.comment("Source priority for ingredient extraction. Allowed values: CONTAINERS_FIRST, PLAYER_FIRST")
					.define("sourcePriority", SourcePriority.CONTAINERS_FIRST.name());

			builder.pop();
		}
	}
}
