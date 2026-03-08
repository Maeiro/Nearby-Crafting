package dev.maeiro.proximitycrafting.registry;

import dev.maeiro.proximitycrafting.block.ProximityCraftingTableBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final Block PROXIMITY_CRAFTING_TABLE = Registry.register(
			BuiltInRegistries.BLOCK,
			ProximityContentDescriptors.PROXIMITY_CRAFTING_TABLE.location(),
			new ProximityCraftingTableBlock(BlockBehaviour.Properties.of().strength(2.5F))
	);

	private ModBlocks() {
	}

	public static void init() {
	}
}
