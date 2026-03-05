package dev.maeiro.proximitycrafting.menu;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.slot.ProximityResultSlot;
import dev.maeiro.proximitycrafting.networking.RecipeBookSourceSnapshotBuilder;
import dev.maeiro.proximitycrafting.registry.ModBlocks;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import dev.maeiro.proximitycrafting.service.crafting.RecipeFillService;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
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

public class ProximityCraftingMenu extends RecipeBookMenu<CraftingContainer> {
	public static final int RESULT_SLOT = 0;
	private static final long SERVER_SNAPSHOT_CACHE_TTL_MS = 3000L;
	private static final long ADJUST_SNAPSHOT_MIN_INTERVAL_MS = 250L;
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
	private int suppressCraftSlotChangedDepth;
	private boolean craftSlotChangesPending;
	private boolean resultShiftCraftInProgress;
	private boolean autoRefillAfterCraft;
	private boolean includePlayerInventory = true;
	private ProximityCraftingConfig.SourcePriority sourcePriority = ProximityCraftingConfig.SourcePriority.CONTAINERS_FIRST;
	private List<RecipeBookSourceEntry> clientRecipeBookSupplementalSources = List.of();
	private List<RecipeBookSourceEntry> serverRecipeBookSnapshotCache = List.of();
	private long serverRecipeBookSnapshotCacheBuiltAtMs = 0L;
	private boolean serverRecipeBookSnapshotCacheValid = false;
	private boolean serverRecipeBookSnapshotPrewarmPending = true;
	private long lastAdjustSnapshotSentAtMs = 0L;

	private CraftingRecipe lastPlacedRecipe;

	public ProximityCraftingMenu(int containerId, Inventory playerInventory, BlockPos tablePos) {
		this(ModMenuTypes.PROXIMITY_CRAFTING_MENU.get(), containerId, playerInventory, tablePos);
	}

	public ProximityCraftingMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, BlockPos tablePos) {
		super(menuType, containerId);
		this.player = playerInventory.player;
		this.tablePos = tablePos.immutable();
		this.access = ContainerLevelAccess.create(playerInventory.player.level(), tablePos);
		this.craftSlots = new SourceTrackingCraftingContainer(this, 3, 3);

		this.addSlot(new ProximityResultSlot(this, playerInventory.player, this.craftSlots, this.resultSlots, 0, 124, 35));

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
		if (suppressCraftSlotChangedDepth > 0) {
			craftSlotChangesPending = true;
			return;
		}
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
		return stillValid(this.access, player, ModBlocks.PROXIMITY_CRAFTING_TABLE.get());
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
	public void broadcastChanges() {
		super.broadcastChanges();
		prewarmServerRecipeBookSnapshotIfNeeded();
	}

	@Override
	public void handlePlacement(boolean placeAll, Recipe<?> recipe, ServerPlayer player) {
		if (recipe instanceof CraftingRecipe craftingRecipe) {
			FillResult fillResult = RecipeFillService.fillFromRecipe(this, craftingRecipe, placeAll);
			if (!fillResult.success()) {
				player.displayClientMessage(net.minecraft.network.chat.Component.translatable(fillResult.messageKey()), true);
			} else if (fillResult.craftedAmount() > 0) {
				invalidateServerRecipeBookSnapshotCache();
			}
			return;
		}
		super.handlePlacement(placeAll, recipe, player);
	}

	public FillResult fillRecipeById(ResourceLocation recipeId, boolean craftAll) {
		Optional<? extends Recipe<?>> optionalRecipe = this.player.level().getRecipeManager().byKey(recipeId);
		if (optionalRecipe.isEmpty()) {
			return FillResult.failure("proximitycrafting.feedback.recipe_not_found");
		}

		Recipe<?> recipe = optionalRecipe.get();
		if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
			return FillResult.failure("proximitycrafting.feedback.invalid_recipe_type");
		}

		FillResult fillResult = RecipeFillService.fillFromRecipe(this, craftingRecipe, craftAll);
		if (fillResult.success() && fillResult.craftedAmount() > 0) {
			invalidateServerRecipeBookSnapshotCache();
		}
		return fillResult;
	}

	public FillResult adjustRecipeLoad(int steps) {
		long startNs = System.nanoTime();
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] menu adjustRecipeLoad steps={} menu={} hasLastRecipe={}",
					steps,
					this.containerId,
					this.lastPlacedRecipe != null
			);
		}
		if (steps == 0) {
			return FillResult.success("proximitycrafting.feedback.filled", 0);
		}

		Optional<CraftingRecipe> currentRecipeOptional = getCurrentCraftingRecipe();
		CraftingRecipe activeRecipe = currentRecipeOptional.orElse(lastPlacedRecipe);
		if (activeRecipe == null) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info("[PROXC-SCROLL] menu no active recipe to adjust");
			}
			return FillResult.failure("proximitycrafting.feedback.no_recipe_selected");
		}

		setLastPlacedRecipe(activeRecipe);
		int direction = steps > 0 ? 1 : -1;
		int requestedSteps = Math.abs(steps);
		FillResult batchResult = direction > 0
				? RecipeFillService.addCrafts(this, activeRecipe, requestedSteps)
				: RecipeFillService.removeCrafts(this, activeRecipe, requestedSteps);
		int appliedSteps = batchResult.success() ? batchResult.craftedAmount() : 0;

		if (appliedSteps > 0) {
			invalidateServerRecipeBookSnapshotCache();
			String messageKey = direction > 0
					? "proximitycrafting.feedback.scroll_increase"
					: "proximitycrafting.feedback.scroll_decrease";
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] menu.adjustRecipeLoad.success menu={} steps={} applied={} recipe={} took={}ms",
						this.containerId,
						steps,
						appliedSteps,
						activeRecipe.getId(),
						String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
				);
			}
			return FillResult.success(messageKey, appliedSteps);
		}

		if (isDebugLoggingEnabled() && batchResult.success()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-SCROLL] menu adjust had no effect requested={} recipe={} reason=no_applied_steps",
					requestedSteps,
					activeRecipe.getId()
			);
		}
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] menu.adjustRecipeLoad.fail menu={} steps={} applied={} recipe={} reason={} took={}ms",
					this.containerId,
					steps,
					appliedSteps,
					activeRecipe.getId(),
					batchResult.messageKey(),
					String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
			);
		}

		return batchResult;
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
		long startNs = System.nanoTime();
		int clearedSlots = 0;
		int returnedToSources = 0;
		int returnedToInventory = 0;
		int droppedItems = 0;
		boolean anySlotChanged = false;

		beginCraftGridBulkMutation();
		try {
			for (int slot = 0; slot < this.craftSlots.getContainerSize(); slot++) {
				ItemStack stack = this.craftSlots.getItem(slot);
				Map<ItemSourceRef, Integer> sourceAllocations = craftSlotSourceLedger[slot];
				if (stack.isEmpty()) {
					if (!sourceAllocations.isEmpty()) {
						clearCraftSlotSource(slot);
					}
					continue;
				}
				clearedSlots++;

				ItemStack remaining = stack.copy();
				if (!sourceAllocations.isEmpty()) {
					returnedToSources += returnStackToTrackedSources(sourceAllocations, remaining);
				}

				int remainingBeforeInventory = remaining.getCount();
				this.player.getInventory().add(remaining);
				returnedToInventory += Math.max(0, remainingBeforeInventory - remaining.getCount());
				if (!remaining.isEmpty()) {
					droppedItems += remaining.getCount();
					this.player.drop(remaining, false);
				}

				int slotIndex = slot;
				runWithSourceTrackingMutation(() -> this.craftSlots.setItem(slotIndex, ItemStack.EMPTY));
				clearCraftSlotSource(slotIndex);
				anySlotChanged = true;
			}
		} finally {
			endCraftGridBulkMutation();
		}
		if (anySlotChanged) {
			this.craftSlots.setChanged();
		}
		if (isDebugLoggingEnabled() && clearedSlots > 0) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] menu.clearCraftGrid menu={} slots={} returnedToSources={} returnedToInventory={} dropped={} took={}ms",
					this.containerId,
					clearedSlots,
					returnedToSources,
					returnedToInventory,
					droppedItems,
					String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
			);
		}
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
		long startNs = System.nanoTime();

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
		Map<ItemSourceRef, Integer> sourceAllocations = craftSlotSourceLedger[slot];
		boolean hadTrackedSources = !sourceAllocations.isEmpty();
		if (hadTrackedSources) {
			returnStackToSourcesOrPlayer(slot, removed);
		} else {
			this.player.getInventory().add(removed);
			if (!removed.isEmpty()) {
				this.player.drop(removed, false);
			}
		}

		ItemStack updated = current.copy();
		updated.shrink(amountToRemove);
		int slotIndex = slot;
		runWithSourceTrackingMutation(() -> craftSlots.setItem(slotIndex, updated.isEmpty() ? ItemStack.EMPTY : updated));
		if (hadTrackedSources) {
			consumeCraftSlotSource(slotIndex, amountToRemove);
		}
		craftSlots.setChanged();
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] menu.removeFromCraftSlotToSources menu={} slot={} removeCount={} hadTrackedSources={} took={}ms",
					this.containerId,
					slot,
					amountToRemove,
					hadTrackedSources,
					String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
			);
		}
		return true;
	}

	private void returnStackToSourcesOrPlayer(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		ItemStack remaining = stack.copy();
		Map<ItemSourceRef, Integer> sourceAllocations = craftSlotSourceLedger[slot];
		if (!sourceAllocations.isEmpty()) {
			returnStackToTrackedSources(sourceAllocations, remaining);
		}

		boolean inserted = this.player.getInventory().add(remaining);
		if (!inserted && !remaining.isEmpty()) {
			this.player.drop(remaining, false);
		}
	}

	private int returnStackToTrackedSources(Map<ItemSourceRef, Integer> sourceAllocations, ItemStack remaining) {
		if (remaining.isEmpty() || sourceAllocations.isEmpty()) {
			return 0;
		}

		int returnedAmount = 0;
		if (sourceAllocations.size() == 1) {
			Map.Entry<ItemSourceRef, Integer> allocation = sourceAllocations.entrySet().iterator().next();
			ItemSourceRef sourceRef = allocation.getKey();
			int targetAmount = Math.min(allocation.getValue(), remaining.getCount());
			if (targetAmount > 0) {
				try {
					ItemStack toReturn = remaining.copy();
					toReturn.setCount(targetAmount);
					ItemStack notInserted = sourceRef.handler().insertItem(sourceRef.slot(), toReturn, false);
					int inserted = targetAmount - notInserted.getCount();
					if (inserted > 0) {
						remaining.shrink(inserted);
						returnedAmount += inserted;
					}
				} catch (RuntimeException exception) {
					ProximityCrafting.LOGGER.warn(
							"Failed to return crafting item to source {}:{}; fallback to player inventory",
							sourceRef.sourceType(),
							sourceRef.slot(),
							exception
					);
				}
			}
			return returnedAmount;
		}

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
					returnedAmount += inserted;
				}
			} catch (RuntimeException exception) {
				ProximityCrafting.LOGGER.warn(
						"Failed to return crafting item to source {}:{}; fallback to player inventory",
						sourceRef.sourceType(),
						sourceRef.slot(),
						exception
				);
			}
		}
		return returnedAmount;
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

	public void beginCraftGridBulkMutation() {
		suppressCraftSlotChangedDepth++;
	}

	public void endCraftGridBulkMutation() {
		endCraftGridBulkMutation(true);
	}

	public void endCraftGridBulkMutation(boolean flushIfPending) {
		if (suppressCraftSlotChangedDepth <= 0) {
			return;
		}
		suppressCraftSlotChangedDepth--;
		if (flushIfPending && suppressCraftSlotChangedDepth == 0 && craftSlotChangesPending) {
			craftSlotChangesPending = false;
			this.access.execute((level, pos) -> slotChangedCraftingGrid(this, level, this.player, this.craftSlots, this.resultSlots));
		}
	}

	public void clearPendingCraftSlotChanges() {
		craftSlotChangesPending = false;
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

	public ProximityCraftingConfig.SourcePriority getSourcePriority() {
		return sourcePriority;
	}

	public void setClientPreferences(boolean autoRefillAfterCraft, boolean includePlayerInventory, ProximityCraftingConfig.SourcePriority sourcePriority) {
		boolean includeChanged = this.includePlayerInventory != includePlayerInventory;
		boolean priorityChanged = this.sourcePriority != (sourcePriority == null
				? ProximityCraftingConfig.SourcePriority.CONTAINERS_FIRST
				: sourcePriority);
		this.autoRefillAfterCraft = autoRefillAfterCraft;
		this.includePlayerInventory = includePlayerInventory;
		this.sourcePriority = sourcePriority == null
				? ProximityCraftingConfig.SourcePriority.CONTAINERS_FIRST
				: sourcePriority;
		if (includeChanged || priorityChanged) {
			invalidateServerRecipeBookSnapshotCache();
		}
	}

	public boolean setClientRecipeBookSupplementalSources(List<RecipeBookSourceEntry> sourceEntries) {
		if (sourceEntries == null || sourceEntries.isEmpty()) {
			boolean changed = !this.clientRecipeBookSupplementalSources.isEmpty();
			this.clientRecipeBookSupplementalSources = List.of();
			return changed;
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
		List<RecipeBookSourceEntry> normalizedSources = List.copyOf(sanitized);
		boolean changed = !areRecipeBookSourceListsEqual(this.clientRecipeBookSupplementalSources, normalizedSources);
		this.clientRecipeBookSupplementalSources = normalizedSources;
		return changed;
	}

	public List<RecipeBookSourceEntry> getClientRecipeBookSupplementalSources() {
		return clientRecipeBookSupplementalSources;
	}

	public List<RecipeBookSourceEntry> getServerRecipeBookSnapshot(boolean preferCache, String reason) {
		if (player.level().isClientSide) {
			return List.of();
		}

		long nowMs = System.currentTimeMillis();
		if (preferCache
				&& serverRecipeBookSnapshotCacheValid
				&& (nowMs - serverRecipeBookSnapshotCacheBuiltAtMs) <= SERVER_SNAPSHOT_CACHE_TTL_MS) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] snapshot.cache.hit menu={} ageMs={} reason={} entries={}",
						this.containerId,
						nowMs - serverRecipeBookSnapshotCacheBuiltAtMs,
						reason,
						serverRecipeBookSnapshotCache.size()
				);
			}
			return serverRecipeBookSnapshotCache;
		}

		List<RecipeBookSourceEntry> rebuilt = RecipeBookSourceSnapshotBuilder.build(this);
		serverRecipeBookSnapshotCache = List.copyOf(rebuilt);
		serverRecipeBookSnapshotCacheBuiltAtMs = nowMs;
		serverRecipeBookSnapshotCacheValid = true;
		serverRecipeBookSnapshotPrewarmPending = false;
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] snapshot.cache.rebuild menu={} reason={} entries={} ttlMs={}",
					this.containerId,
					reason,
					serverRecipeBookSnapshotCache.size(),
					SERVER_SNAPSHOT_CACHE_TTL_MS
			);
		}
		return serverRecipeBookSnapshotCache;
	}

	public void invalidateServerRecipeBookSnapshotCache() {
		serverRecipeBookSnapshotCacheValid = false;
	}

	public boolean shouldSendSnapshotForAdjust() {
		long nowMs = System.currentTimeMillis();
		if (lastAdjustSnapshotSentAtMs != 0L && (nowMs - lastAdjustSnapshotSentAtMs) < ADJUST_SNAPSHOT_MIN_INTERVAL_MS) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] snapshot.adjust.skip menu={} elapsedMs={} minIntervalMs={}",
						this.containerId,
						nowMs - lastAdjustSnapshotSentAtMs,
						ADJUST_SNAPSHOT_MIN_INTERVAL_MS
				);
			}
			return false;
		}
		lastAdjustSnapshotSentAtMs = nowMs;
		return true;
	}

	private void prewarmServerRecipeBookSnapshotIfNeeded() {
		if (!serverRecipeBookSnapshotPrewarmPending || player.level().isClientSide) {
			return;
		}
		serverRecipeBookSnapshotPrewarmPending = false;
		getServerRecipeBookSnapshot(false, "menu_open_prewarm");
	}

	private static boolean areRecipeBookSourceListsEqual(List<RecipeBookSourceEntry> left, List<RecipeBookSourceEntry> right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null || left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			RecipeBookSourceEntry leftEntry = left.get(i);
			RecipeBookSourceEntry rightEntry = right.get(i);
			if (leftEntry.count() != rightEntry.count()) {
				return false;
			}
			if (!ItemStack.isSameItemSameTags(leftEntry.stack(), rightEntry.stack())) {
				return false;
			}
		}
		return true;
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public record RecipeBookSourceEntry(ItemStack stack, int count) {
	}

	private static class SourceTrackingCraftingContainer extends TransientCraftingContainer {
		private final ProximityCraftingMenu menu;

		private SourceTrackingCraftingContainer(ProximityCraftingMenu menu, int width, int height) {
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



