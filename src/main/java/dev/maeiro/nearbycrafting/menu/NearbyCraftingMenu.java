package dev.maeiro.nearbycrafting.menu;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import dev.maeiro.nearbycrafting.menu.slot.NearbyResultSlot;
import dev.maeiro.nearbycrafting.registry.ModBlocks;
import dev.maeiro.nearbycrafting.registry.ModMenuTypes;
import dev.maeiro.nearbycrafting.service.crafting.FillResult;
import dev.maeiro.nearbycrafting.service.crafting.RecipeFillService;
import dev.maeiro.nearbycrafting.service.source.ItemSourceRef;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NearbyCraftingMenu extends RecipeBookMenu<CraftingContainer> {
	public static final int RESULT_SLOT = 0;
	private static final int CRAFT_SLOT_START = 1;
	private static final int CRAFT_SLOT_END = 10;
	private static final int INV_SLOT_START = 10;
	private static final int INV_SLOT_END = 37;
	private static final int HOTBAR_SLOT_START = 37;
	private static final int HOTBAR_SLOT_END = 46;

	private final CraftingContainer craftSlots;
	private final ResultContainer resultSlots = new ResultContainer();
	private final ContainerLevelAccess access;
	private final Player player;
	private final BlockPos tablePos;
	private final Map<ItemSourceRef, Integer>[] craftSlotSourceLedger = createSourceLedger();
	private boolean sourceTrackingMutationActive;
	private boolean resultShiftCraftInProgress;
	private boolean autoRefillAfterCraft;
	private boolean includePlayerInventory = true;
	private NearbyCraftingConfig.SourcePriority sourcePriority = NearbyCraftingConfig.SourcePriority.CONTAINERS_FIRST;
	private List<RecipeBookSourceEntry> clientRecipeBookSupplementalSources = List.of();

	private CraftingRecipe lastPlacedRecipe;

	public NearbyCraftingMenu(int containerId, Inventory playerInventory, BlockPos tablePos) {
		this(ModMenuTypes.NEARBY_CRAFTING_MENU.get(), containerId, playerInventory, tablePos);
	}

	public NearbyCraftingMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, BlockPos tablePos) {
		super(menuType, containerId);
		this.player = playerInventory.player;
		this.tablePos = tablePos.immutable();
		this.access = ContainerLevelAccess.create(playerInventory.player.level(), tablePos);
		this.craftSlots = new SourceTrackingCraftingContainer(this, 3, 3);

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
		fillSupplementalRecipeBookSources(itemHelper);
	}

	public void fillSupplementalRecipeBookSources(StackedContents itemHelper) {
		for (RecipeBookSourceEntry sourceEntry : clientRecipeBookSupplementalSources) {
			if (sourceEntry.count() <= 0 || sourceEntry.stack().isEmpty()) {
				continue;
			}
			int remaining = sourceEntry.count();
			while (remaining > 0) {
				ItemStack stackChunk = sourceEntry.stack().copy();
				int chunkSize = Math.min(remaining, stackChunk.getMaxStackSize());
				stackChunk.setCount(chunkSize);
				itemHelper.accountStack(stackChunk);
				remaining -= chunkSize;
			}
		}
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
		if (player instanceof ServerPlayer) {
			this.access.execute((level, pos) -> {
				if (!level.isClientSide) {
					this.clearCraftGridToPlayerOrDrop();
				}
			});
		}
		super.removed(player);
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

			boolean shiftCraftResultTake = index == RESULT_SLOT;
			if (shiftCraftResultTake) {
				this.resultShiftCraftInProgress = true;
			}
			try {
				slot.onTake(player, stackInSlot);
			} finally {
				if (shiftCraftResultTake) {
					this.resultShiftCraftInProgress = false;
				}
			}
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

	public FillResult adjustRecipeLoad(int steps) {
		NearbyCrafting.LOGGER.info(
				"[NC-SCROLL] menu adjustRecipeLoad steps={} menu={} hasLastRecipe={}",
				steps,
				this.containerId,
				this.lastPlacedRecipe != null
		);
		if (steps == 0) {
			return FillResult.success("nearbycrafting.feedback.filled", 0);
		}

		Optional<CraftingRecipe> currentRecipeOptional = getCurrentCraftingRecipe();
		CraftingRecipe activeRecipe = currentRecipeOptional.orElse(lastPlacedRecipe);
		if (activeRecipe == null) {
			NearbyCrafting.LOGGER.info("[NC-SCROLL] menu no active recipe to adjust");
			return FillResult.failure("nearbycrafting.feedback.no_recipe_selected");
		}

		setLastPlacedRecipe(activeRecipe);
		int appliedSteps = 0;
		FillResult lastResult = FillResult.failure("nearbycrafting.feedback.fill_failed");
		int direction = steps > 0 ? 1 : -1;

		for (int i = 0; i < Math.abs(steps); i++) {
			FillResult stepResult = direction > 0
					? RecipeFillService.addSingleCraft(this, activeRecipe)
					: RecipeFillService.removeSingleCraft(this, activeRecipe);
			if (!stepResult.success()) {
				NearbyCrafting.LOGGER.info(
						"[NC-SCROLL] menu stopped at iteration={} reason={}",
						i,
						stepResult.messageKey()
				);
				lastResult = stepResult;
				break;
			}
			appliedSteps++;
		}

		if (appliedSteps > 0) {
			String messageKey = direction > 0
					? "nearbycrafting.feedback.scroll_increase"
					: "nearbycrafting.feedback.scroll_decrease";
			return FillResult.success(messageKey, appliedSteps);
		}

		return lastResult;
	}

	private Optional<CraftingRecipe> getCurrentCraftingRecipe() {
		if (player.level() == null) {
			return Optional.empty();
		}
		return player.level()
				.getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, craftSlots, player.level());
	}

	public void clearCraftGridToPlayerOrDrop() {
		if (this.player.level().isClientSide) {
			return;
		}

		for (int slot = 0; slot < this.craftSlots.getContainerSize(); slot++) {
			ItemStack stack = this.craftSlots.getItem(slot);
			if (stack.isEmpty()) {
				clearCraftSlotSource(slot);
				continue;
			}

			ItemStack remaining = stack.copy();
			Map<ItemSourceRef, Integer> sourceAllocations = craftSlotSourceLedger[slot];
			for (Map.Entry<ItemSourceRef, Integer> allocation : sourceAllocations.entrySet()) {
				if (remaining.isEmpty()) {
					break;
				}

				ItemSourceRef sourceRef = allocation.getKey();
				int targetAmount = Math.min(allocation.getValue(), remaining.getCount());
				if (targetAmount <= 0) {
					continue;
				}

				try {
					ItemStack toReturn = remaining.copy();
					toReturn.setCount(targetAmount);
					ItemStack notInserted = sourceRef.handler().insertItem(sourceRef.slot(), toReturn, false);
					int inserted = targetAmount - notInserted.getCount();
					if (inserted > 0) {
						remaining.shrink(inserted);
					}
				} catch (RuntimeException exception) {
					NearbyCrafting.LOGGER.warn(
							"Failed to return crafting item to source {}:{}; fallback to player inventory",
							sourceRef.sourceType(),
							sourceRef.slot(),
							exception
					);
				}
			}

			boolean inserted = this.player.getInventory().add(remaining);
			if (!inserted && !remaining.isEmpty()) {
				this.player.drop(remaining, false);
			}

			int slotIndex = slot;
			runWithSourceTrackingMutation(() -> this.craftSlots.setItem(slotIndex, ItemStack.EMPTY));
			clearCraftSlotSource(slotIndex);
		}
		this.craftSlots.setChanged();
	}

	public void setCraftSlotFromSource(int slot, ItemStack stack, @Nullable ItemSourceRef sourceRef) {
		int slotIndex = slot;
		ItemStack storedStack = stack.copy();
		runWithSourceTrackingMutation(() -> this.craftSlots.setItem(slotIndex, storedStack));
		clearCraftSlotSource(slotIndex);
		if (!storedStack.isEmpty() && sourceRef != null) {
			addCraftSlotSource(slotIndex, sourceRef, storedStack.getCount());
		}
	}

	public boolean removeFromCraftSlotToSources(int slot, int count) {
		if (slot < 0 || slot >= craftSlots.getContainerSize() || count <= 0) {
			return false;
		}

		ItemStack current = craftSlots.getItem(slot);
		if (current.isEmpty()) {
			return false;
		}

		int amountToRemove = Math.min(count, current.getCount());
		if (amountToRemove <= 0) {
			return false;
		}

		ItemStack removed = current.copy();
		removed.setCount(amountToRemove);
		returnStackToSourcesOrPlayer(slot, removed);

		ItemStack updated = current.copy();
		updated.shrink(amountToRemove);
		int slotIndex = slot;
		runWithSourceTrackingMutation(() -> craftSlots.setItem(slotIndex, updated.isEmpty() ? ItemStack.EMPTY : updated));
		consumeCraftSlotSource(slotIndex, amountToRemove);
		craftSlots.setChanged();
		return true;
	}

	private void returnStackToSourcesOrPlayer(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		ItemStack remaining = stack.copy();
		Map<ItemSourceRef, Integer> sourceAllocations = craftSlotSourceLedger[slot];
		for (Map.Entry<ItemSourceRef, Integer> allocation : sourceAllocations.entrySet()) {
			if (remaining.isEmpty()) {
				break;
			}

			ItemSourceRef sourceRef = allocation.getKey();
			int targetAmount = Math.min(allocation.getValue(), remaining.getCount());
			if (targetAmount <= 0) {
				continue;
			}

			try {
				ItemStack toReturn = remaining.copy();
				toReturn.setCount(targetAmount);
				ItemStack notInserted = sourceRef.handler().insertItem(sourceRef.slot(), toReturn, false);
				int inserted = targetAmount - notInserted.getCount();
				if (inserted > 0) {
					remaining.shrink(inserted);
				}
			} catch (RuntimeException exception) {
				NearbyCrafting.LOGGER.warn(
						"Failed to return crafting item to source {}:{}; fallback to player inventory",
						sourceRef.sourceType(),
						sourceRef.slot(),
						exception
				);
			}
		}

		boolean inserted = this.player.getInventory().add(remaining);
		if (!inserted && !remaining.isEmpty()) {
			this.player.drop(remaining, false);
		}
	}

	public boolean canAcceptCraftSlotStack(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		ItemStack current = this.craftSlots.getItem(slot);
		if (current.isEmpty()) {
			return stack.getCount() <= getSlotStackLimit(stack);
		}
		if (!ItemStack.isSameItemSameTags(current, stack)) {
			return false;
		}
		return current.getCount() + stack.getCount() <= getSlotStackLimit(current);
	}

	public boolean addCraftSlotFromSource(int slot, ItemStack stack, @Nullable ItemSourceRef sourceRef) {
		if (stack.isEmpty()) {
			return true;
		}
		if (!canAcceptCraftSlotStack(slot, stack)) {
			return false;
		}

		int slotIndex = slot;
		ItemStack current = this.craftSlots.getItem(slotIndex);
		ItemStack updated = current.isEmpty() ? stack.copy() : current.copy();
		if (!current.isEmpty()) {
			updated.grow(stack.getCount());
		}

		runWithSourceTrackingMutation(() -> this.craftSlots.setItem(slotIndex, updated));
		if (sourceRef != null) {
			addCraftSlotSource(slotIndex, sourceRef, stack.getCount());
		}
		return true;
	}

	private int getSlotStackLimit(ItemStack stack) {
		return Math.min(this.craftSlots.getMaxStackSize(), stack.getMaxStackSize());
	}

	private void addCraftSlotSource(int slot, ItemSourceRef sourceRef, int count) {
		if (slot < 0 || slot >= craftSlotSourceLedger.length || sourceRef == null || count <= 0) {
			return;
		}
		craftSlotSourceLedger[slot].merge(sourceRef, count, Integer::sum);
	}

	private void consumeCraftSlotSource(int slot, int count) {
		if (slot < 0 || slot >= craftSlotSourceLedger.length || count <= 0) {
			return;
		}
		Map<ItemSourceRef, Integer> slotLedger = craftSlotSourceLedger[slot];
		if (slotLedger.isEmpty()) {
			return;
		}

		int remaining = count;
		var iterator = slotLedger.entrySet().iterator();
		while (iterator.hasNext() && remaining > 0) {
			Map.Entry<ItemSourceRef, Integer> entry = iterator.next();
			int tracked = entry.getValue();
			if (tracked <= remaining) {
				remaining -= tracked;
				iterator.remove();
			} else {
				entry.setValue(tracked - remaining);
				remaining = 0;
			}
		}
	}

	public boolean isSourceTrackingMutationActive() {
		return sourceTrackingMutationActive;
	}

	public void clearCraftSlotSource(int slot) {
		if (slot >= 0 && slot < craftSlotSourceLedger.length) {
			craftSlotSourceLedger[slot].clear();
		}
	}

	private void clearAllCraftSlotSources() {
		for (Map<ItemSourceRef, Integer> slotLedger : craftSlotSourceLedger) {
			slotLedger.clear();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<ItemSourceRef, Integer>[] createSourceLedger() {
		Map<ItemSourceRef, Integer>[] ledger = (Map<ItemSourceRef, Integer>[]) new Map[9];
		for (int i = 0; i < ledger.length; i++) {
			ledger[i] = new LinkedHashMap<>();
		}
		return ledger;
	}

	private void runWithSourceTrackingMutation(Runnable runnable) {
		sourceTrackingMutationActive = true;
		try {
			runnable.run();
		} finally {
			sourceTrackingMutationActive = false;
		}
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

	public boolean isAutoRefillAfterCraft() {
		return autoRefillAfterCraft;
	}

	public boolean isResultShiftCraftInProgress() {
		return resultShiftCraftInProgress;
	}

	public boolean isIncludePlayerInventory() {
		return includePlayerInventory;
	}

	public NearbyCraftingConfig.SourcePriority getSourcePriority() {
		return sourcePriority;
	}

	public void setClientPreferences(boolean autoRefillAfterCraft, boolean includePlayerInventory, NearbyCraftingConfig.SourcePriority sourcePriority) {
		this.autoRefillAfterCraft = autoRefillAfterCraft;
		this.includePlayerInventory = includePlayerInventory;
		this.sourcePriority = sourcePriority == null
				? NearbyCraftingConfig.SourcePriority.CONTAINERS_FIRST
				: sourcePriority;
	}

	public void setClientRecipeBookSupplementalSources(List<RecipeBookSourceEntry> sourceEntries) {
		if (sourceEntries == null || sourceEntries.isEmpty()) {
			this.clientRecipeBookSupplementalSources = List.of();
			return;
		}

		List<RecipeBookSourceEntry> sanitized = new ArrayList<>(sourceEntries.size());
		for (RecipeBookSourceEntry sourceEntry : sourceEntries) {
			if (sourceEntry == null || sourceEntry.count() <= 0 || sourceEntry.stack().isEmpty()) {
				continue;
			}
			ItemStack normalized = sourceEntry.stack().copy();
			normalized.setCount(1);
			sanitized.add(new RecipeBookSourceEntry(normalized, sourceEntry.count()));
		}
		this.clientRecipeBookSupplementalSources = List.copyOf(sanitized);
	}

	public List<RecipeBookSourceEntry> getClientRecipeBookSupplementalSources() {
		return clientRecipeBookSupplementalSources;
	}

	public record RecipeBookSourceEntry(ItemStack stack, int count) {
	}

	private static class SourceTrackingCraftingContainer extends TransientCraftingContainer {
		private final NearbyCraftingMenu menu;

		private SourceTrackingCraftingContainer(NearbyCraftingMenu menu, int width, int height) {
			super(menu, width, height);
			this.menu = menu;
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			ItemStack removed = super.removeItem(slot, amount);
			if (!removed.isEmpty() && !menu.isSourceTrackingMutationActive()) {
				menu.consumeCraftSlotSource(slot, removed.getCount());
			}
			return removed;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack removed = super.removeItemNoUpdate(slot);
			if (!removed.isEmpty() && !menu.isSourceTrackingMutationActive()) {
				menu.consumeCraftSlotSource(slot, removed.getCount());
			}
			return removed;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			super.setItem(slot, stack);
			if (!menu.isSourceTrackingMutationActive()) {
				menu.clearCraftSlotSource(slot);
			}
		}

		@Override
		public void clearContent() {
			super.clearContent();
			if (!menu.isSourceTrackingMutationActive()) {
				menu.clearAllCraftSlotSources();
			}
		}
	}
}
