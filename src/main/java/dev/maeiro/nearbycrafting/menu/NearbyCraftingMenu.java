package dev.maeiro.nearbycrafting.menu;

import dev.maeiro.nearbycrafting.menu.slot.NearbyResultSlot;
import dev.maeiro.nearbycrafting.registry.ModBlocks;
import dev.maeiro.nearbycrafting.registry.ModMenuTypes;
import dev.maeiro.nearbycrafting.service.crafting.FillResult;
import dev.maeiro.nearbycrafting.service.crafting.RecipeFillService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

public class NearbyCraftingMenu extends RecipeBookMenu<CraftingContainer> {
	public static final int RESULT_SLOT = 0;
	private static final int CRAFT_SLOT_START = 1;
	private static final int CRAFT_SLOT_END = 10;
	private static final int INV_SLOT_START = 10;
	private static final int INV_SLOT_END = 37;
	private static final int HOTBAR_SLOT_START = 37;
	private static final int HOTBAR_SLOT_END = 46;

	private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
	private final ResultContainer resultSlots = new ResultContainer();
	private final ContainerLevelAccess access;
	private final Player player;
	private final BlockPos tablePos;

	private CraftingRecipe lastPlacedRecipe;

	public NearbyCraftingMenu(int containerId, Inventory playerInventory, BlockPos tablePos) {
		this(ModMenuTypes.NEARBY_CRAFTING_MENU.get(), containerId, playerInventory, tablePos);
	}

	public NearbyCraftingMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, BlockPos tablePos) {
		super(menuType, containerId);
		this.player = playerInventory.player;
		this.tablePos = tablePos.immutable();
		this.access = ContainerLevelAccess.create(playerInventory.player.level(), tablePos);

		this.addSlot(new NearbyResultSlot(this, playerInventory.player, this.craftSlots, this.resultSlots, 0, 124, 35));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				this.addSlot(new Slot(this.craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
			}
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	public static void slotChangedCraftingGrid(AbstractContainerMenu menu, Level level, Player player, CraftingContainer craftSlots, ResultContainer resultSlots) {
		if (level.isClientSide) {
			return;
		}

		ServerPlayer serverPlayer = (ServerPlayer) player;
		ItemStack result = ItemStack.EMPTY;
		Optional<CraftingRecipe> optional = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
		if (optional.isPresent()) {
			CraftingRecipe recipe = optional.get();
			if (resultSlots.setRecipeUsed(level, serverPlayer, recipe)) {
				ItemStack assembled = recipe.assemble(craftSlots, level.registryAccess());
				if (assembled.isItemEnabled(level.enabledFeatures())) {
					result = assembled;
				}
			}
		}

		resultSlots.setItem(0, result);
		menu.setRemoteSlot(0, result);
		serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, result));
	}

	@Override
	public void slotsChanged(Container inventory) {
		this.access.execute((level, pos) -> slotChangedCraftingGrid(this, level, this.player, this.craftSlots, this.resultSlots));
	}

	@Override
	public void fillCraftSlotsStackedContents(StackedContents itemHelper) {
		this.craftSlots.fillStackedContents(itemHelper);
	}

	@Override
	public void clearCraftingContent() {
		this.craftSlots.clearContent();
		this.resultSlots.clearContent();
	}

	@Override
	public boolean recipeMatches(Recipe<? super CraftingContainer> recipe) {
		return recipe.matches(this.craftSlots, this.player.level());
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.access.execute((level, pos) -> this.clearContainer(player, this.craftSlots));
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.NEARBY_CRAFTING_TABLE.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stackInSlot = slot.getItem();
			moved = stackInSlot.copy();
			if (index == RESULT_SLOT) {
				this.access.execute((level, pos) -> stackInSlot.getItem().onCraftedBy(stackInSlot, level, player));
				if (!this.moveItemStackTo(stackInSlot, INV_SLOT_START, HOTBAR_SLOT_END, true)) {
					return ItemStack.EMPTY;
				}
				slot.onQuickCraft(stackInSlot, moved);
			} else if (index >= INV_SLOT_START && index < HOTBAR_SLOT_END) {
				if (!this.moveItemStackTo(stackInSlot, CRAFT_SLOT_START, CRAFT_SLOT_END, false)) {
					if (index < INV_SLOT_END) {
						if (!this.moveItemStackTo(stackInSlot, HOTBAR_SLOT_START, HOTBAR_SLOT_END, false)) {
							return ItemStack.EMPTY;
						}
					} else if (!this.moveItemStackTo(stackInSlot, INV_SLOT_START, INV_SLOT_END, false)) {
						return ItemStack.EMPTY;
					}
				}
			} else if (!this.moveItemStackTo(stackInSlot, INV_SLOT_START, HOTBAR_SLOT_END, false)) {
				return ItemStack.EMPTY;
			}

			if (stackInSlot.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}

			if (stackInSlot.getCount() == moved.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(player, stackInSlot);
			if (index == RESULT_SLOT) {
				player.drop(stackInSlot, false);
			}
		}

		return moved;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public int getResultSlotIndex() {
		return RESULT_SLOT;
	}

	@Override
	public int getGridWidth() {
		return this.craftSlots.getWidth();
	}

	@Override
	public int getGridHeight() {
		return this.craftSlots.getHeight();
	}

	@Override
	public int getSize() {
		return 10;
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return RecipeBookType.CRAFTING;
	}

	@Override
	public boolean shouldMoveToInventory(int slotIndex) {
		return slotIndex != getResultSlotIndex();
	}

	@Override
	public void handlePlacement(boolean placeAll, Recipe<?> recipe, ServerPlayer player) {
		if (recipe instanceof CraftingRecipe craftingRecipe) {
			FillResult fillResult = RecipeFillService.fillFromRecipe(this, craftingRecipe, placeAll);
			if (!fillResult.success()) {
				player.displayClientMessage(net.minecraft.network.chat.Component.translatable(fillResult.messageKey()), true);
			}
			return;
		}
		super.handlePlacement(placeAll, recipe, player);
	}

	public FillResult fillRecipeById(ResourceLocation recipeId, boolean craftAll) {
		Optional<? extends Recipe<?>> optionalRecipe = this.player.level().getRecipeManager().byKey(recipeId);
		if (optionalRecipe.isEmpty()) {
			return FillResult.failure("nearbycrafting.feedback.recipe_not_found");
		}

		Recipe<?> recipe = optionalRecipe.get();
		if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
			return FillResult.failure("nearbycrafting.feedback.invalid_recipe_type");
		}

		return RecipeFillService.fillFromRecipe(this, craftingRecipe, craftAll);
	}

	public void clearCraftGridToPlayerOrDrop() {
		for (int slot = 0; slot < this.craftSlots.getContainerSize(); slot++) {
			ItemStack stack = this.craftSlots.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			ItemStack remaining = stack.copy();
			boolean inserted = this.player.getInventory().add(remaining);
			if (!inserted && !remaining.isEmpty()) {
				this.player.drop(remaining, false);
			}
			this.craftSlots.setItem(slot, ItemStack.EMPTY);
		}
		this.craftSlots.setChanged();
	}

	public CraftingContainer getCraftSlots() {
		return craftSlots;
	}

	public Level getLevel() {
		return player.level();
	}

	public Player getPlayer() {
		return player;
	}

	public BlockPos getTablePos() {
		return tablePos;
	}

	public CraftingRecipe getLastPlacedRecipe() {
		return lastPlacedRecipe;
	}

	public void setLastPlacedRecipe(CraftingRecipe lastPlacedRecipe) {
		this.lastPlacedRecipe = lastPlacedRecipe;
	}
}
