package dev.maeiro.nearbycrafting.registry;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.block.NearbyCraftingTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, NearbyCrafting.MOD_ID);

	public static final RegistryObject<Block> NEARBY_CRAFTING_TABLE = BLOCKS.register(
			"nearby_crafting_table",
			() -> new NearbyCraftingTableBlock(BlockBehaviour.Properties.of().strength(2.5F))
	);

	private ModBlocks() {
	}
}

