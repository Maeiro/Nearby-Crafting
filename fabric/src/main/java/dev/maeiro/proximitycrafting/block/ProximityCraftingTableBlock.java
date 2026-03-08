package dev.maeiro.proximitycrafting.block;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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

import org.jetbrains.annotations.Nullable;

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
			MenuRegistry.openExtendedMenu(serverPlayer, new ExtendedMenuProvider() {
				@Override
				public void saveExtraData(FriendlyByteBuf buf) {
					buf.writeBlockPos(pos);
				}

				@Override
				public Component getDisplayName() {
					return menuProvider.getDisplayName();
				}

				@Override
				public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
					return menuProvider.createMenu(containerId, playerInventory, player);
				}
			});
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





