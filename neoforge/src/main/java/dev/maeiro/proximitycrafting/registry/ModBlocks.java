package dev.maeiro.proximitycrafting.registry;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.block.ProximityCraftingTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ProximityCrafting.MOD_ID);

	public static final RegistryObject<Block> PROXIMITY_CRAFTING_TABLE = BLOCKS.register(
			ProximityContentDescriptors.PROXIMITY_CRAFTING_TABLE.path(),
			() -> new ProximityCraftingTableBlock(BlockBehaviour.Properties.of().strength(2.5F))
	);

	private ModBlocks() {
	}
}
