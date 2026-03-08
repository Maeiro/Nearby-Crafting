package dev.maeiro.proximitycrafting.block;

import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

public class ProximityCraftingTableBlock extends Block {
	private static final Component CONTAINER_TITLE = Component.translatable("container.proximitycrafting.proximity_crafting_table");

	public ProximityCraftingTableBlock(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		MenuProvider menuProvider = getMenuProvider(state, level, pos);
		if (menuProvider != null && player instanceof ServerPlayer serverPlayer) {
			NetworkHooks.openScreen(serverPlayer, menuProvider, pos);
		}

		return InteractionResult.CONSUME;
	}

	@Nullable
	@Override
	public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider(
				(int containerId, Inventory playerInventory, Player player) -> new ProximityCraftingMenu(containerId, playerInventory, pos),
				CONTAINER_TITLE
		);
	}
}





