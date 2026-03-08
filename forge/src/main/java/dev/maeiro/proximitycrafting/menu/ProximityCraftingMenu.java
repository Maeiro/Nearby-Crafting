package dev.maeiro.proximitycrafting.menu;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.slot.ProximityResultSlot;
import dev.maeiro.proximitycrafting.networking.RecipeBookSourceSnapshotBuilder;
import dev.maeiro.proximitycrafting.registry.ModBlocks;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import dev.maeiro.proximitycrafting.service.crafting.RecipeBookSourceSessionState;
import dev.maeiro.proximitycrafting.service.crafting.RecipeFillService;
import dev.maeiro.proximitycrafting.service.crafting.TrackedCraftGridPort;
import dev.maeiro.proximitycrafting.service.crafting.TrackedCraftGridSession;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
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
import java.util.List;
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
	private final TrackedCraftGridSession trackedCraftGridSession;
	private final TrackedCraftGridPort trackedCraftGridPort = new MenuTrackedCraftGridPort();
	private final RecipeBookSourceSessionState recipeBookSourceSessionState =
			new RecipeBookSourceSessionState(SERVER_SNAPSHOT_CACHE_TTL_MS, ADJUST_SNAPSHOT_MIN_INTERVAL_MS);
	private boolean resultShiftCraftInProgress;
	private boolean autoRefillAfterCraft;
	private boolean includePlayerInventory = true;
	private SourcePriority sourcePriority = SourcePriority.CONTAINERS_FIRST;

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
		this.trackedCraftGridSession = new TrackedCraftGridSession(this.craftSlots.getContainerSize());

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
		Optional<CraftingRecipe> optional = resolveCraftingResult(menu, level, craftSlots);
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

	private static Optional<CraftingRecipe> resolveCraftingResult(AbstractContainerMenu menu, Level level, CraftingContainer craftSlots) {
		if (menu instanceof ProximityCraftingMenu proximityMenu) {
			Optional<CraftingRecipe> preferredRecipe = proximityMenu.getPreferredTrackedRecipe();
			if (preferredRecipe.isPresent()) {
				return preferredRecipe;
			}
		}
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
	}

	@Override
	public void slotsChanged(Container inventory) {
		if (trackedCraftGridSession.onCraftGridChangedDeferred()) {
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
		for (RecipeBookSourceEntry sourceEntry : recipeBookSourceSessionState.getClientRecipeBookSupplementalSources()) {
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

		Optional<CraftingRecipe> currentRecipeOptional = getPreferredActiveRecipe();
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

	private Optional<CraftingRecipe> getPreferredTrackedRecipe() {
		if (lastPlacedRecipe == null || player.level() == null) {
			return Optional.empty();
		}
		if (!lastPlacedRecipe.matches(craftSlots, player.level())) {
			return Optional.empty();
		}
		return Optional.of(lastPlacedRecipe);
	}

	private Optional<CraftingRecipe> getPreferredActiveRecipe() {
		Optional<CraftingRecipe> trackedRecipe = getPreferredTrackedRecipe();
		if (trackedRecipe.isPresent()) {
			return trackedRecipe;
		}
		return getCurrentCraftingRecipe();
	}

	public boolean hasAnyCraftGridItems() {
		for (int slot = 0; slot < this.craftSlots.getContainerSize(); slot++) {
			if (!this.craftSlots.getItem(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public void clearCraftGridToPlayerOrDrop() {
		if (this.player.level().isClientSide) {
			return;
		}
		long startNs = System.nanoTime();
		TrackedCraftGridSession.ClearResult clearResult = trackedCraftGridSession.clearCraftGridToPlayerOrDrop(trackedCraftGridPort);
		if (isDebugLoggingEnabled() && clearResult.clearedSlots() > 0) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] menu.clearCraftGrid menu={} slots={} returnedToSources={} returnedToInventory={} dropped={} took={}ms",
					this.containerId,
					clearResult.clearedSlots(),
					clearResult.returnedToSources(),
					clearResult.returnedToInventory(),
					clearResult.droppedItems(),
					String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
			);
		}
	}

	public void setCraftSlotFromSource(int slot, ItemStack stack, @Nullable ItemSourceRef sourceRef) {
		trackedCraftGridSession.setCraftSlotFromSource(trackedCraftGridPort, slot, stack, sourceRef);
	}

	public boolean removeFromCraftSlotToSources(int slot, int count) {
		long startNs = System.nanoTime();
		TrackedCraftGridSession.RemoveResult removeResult = trackedCraftGridSession.removeFromCraftSlotToSources(trackedCraftGridPort, slot, count);
		if (!removeResult.changed()) {
			return false;
		}
		if (isDebugLoggingEnabled()) {
			ProximityCrafting.LOGGER.info(
					"[PROXC-PERF] menu.removeFromCraftSlotToSources menu={} slot={} removeCount={} hadTrackedSources={} took={}ms",
					this.containerId,
					slot,
					removeResult.removedCount(),
					removeResult.hadTrackedSources(),
					String.format("%.3f", (System.nanoTime() - startNs) / 1_000_000.0D)
			);
		}
		return true;
	}

	public boolean canAcceptCraftSlotStack(int slot, ItemStack stack) {
		return trackedCraftGridSession.canAcceptCraftSlotStack(trackedCraftGridPort, slot, stack);
	}

	public boolean addCraftSlotFromSource(int slot, ItemStack stack, @Nullable ItemSourceRef sourceRef) {
		return trackedCraftGridSession.addCraftSlotFromSource(trackedCraftGridPort, slot, stack, sourceRef);
	}

	public boolean isSourceTrackingMutationActive() {
		return trackedCraftGridSession.isSourceTrackingMutationActive();
	}

	public void clearCraftSlotSource(int slot) {
		trackedCraftGridSession.onContainerSet(slot);
	}

	private void clearAllCraftSlotSources() {
		trackedCraftGridSession.onContainerCleared();
	}

	public void beginCraftGridBulkMutation() {
		trackedCraftGridSession.beginBulkMutation();
	}

	public void endCraftGridBulkMutation() {
		endCraftGridBulkMutation(true);
	}

	public void endCraftGridBulkMutation(boolean flushIfPending) {
		trackedCraftGridSession.endBulkMutation(trackedCraftGridPort, flushIfPending);
	}

	public void clearPendingCraftSlotChanges() {
		trackedCraftGridSession.clearPendingCraftSlotChanges();
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

	public SourcePriority getSourcePriority() {
		return sourcePriority;
	}

	public void setClientPreferences(boolean autoRefillAfterCraft, boolean includePlayerInventory, SourcePriority sourcePriority) {
		boolean includeChanged = this.includePlayerInventory != includePlayerInventory;
		boolean priorityChanged = this.sourcePriority != (sourcePriority == null
				? SourcePriority.CONTAINERS_FIRST
				: sourcePriority);
		this.autoRefillAfterCraft = autoRefillAfterCraft;
		this.includePlayerInventory = includePlayerInventory;
		this.sourcePriority = sourcePriority == null
				? SourcePriority.CONTAINERS_FIRST
				: sourcePriority;
		if (includeChanged || priorityChanged) {
			invalidateServerRecipeBookSnapshotCache();
		}
	}

	public boolean setClientRecipeBookSupplementalSources(List<RecipeBookSourceEntry> sourceEntries) {
		return recipeBookSourceSessionState.setClientRecipeBookSupplementalSources(sourceEntries);
	}

	public List<RecipeBookSourceEntry> getClientRecipeBookSupplementalSources() {
		return recipeBookSourceSessionState.getClientRecipeBookSupplementalSources();
	}

	public List<RecipeBookSourceEntry> getServerRecipeBookSnapshot(boolean preferCache, String reason) {
		if (player.level().isClientSide) {
			return List.of();
		}

		long nowMs = System.currentTimeMillis();
		RecipeBookSourceSessionState.SnapshotResult snapshotResult = recipeBookSourceSessionState.getServerRecipeBookSnapshot(
				preferCache,
				nowMs,
				() -> RecipeBookSourceSnapshotBuilder.build(this)
		);
		if (isDebugLoggingEnabled()) {
			if (snapshotResult.cacheHit()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] snapshot.cache.hit menu={} ageMs={} reason={} entries={}",
						this.containerId,
						snapshotResult.cacheAgeMs(),
						reason,
						snapshotResult.entries().size()
				);
			} else {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] snapshot.cache.rebuild menu={} reason={} entries={} ttlMs={}",
						this.containerId,
						reason,
						snapshotResult.entries().size(),
						SERVER_SNAPSHOT_CACHE_TTL_MS
				);
			}
		}
		return snapshotResult.entries();
	}

	public void invalidateServerRecipeBookSnapshotCache() {
		recipeBookSourceSessionState.invalidateServerRecipeBookSnapshotCache();
	}

	public boolean shouldSendSnapshotForAdjust() {
		long nowMs = System.currentTimeMillis();
		RecipeBookSourceSessionState.AdjustSnapshotDecision decision = recipeBookSourceSessionState.shouldSendSnapshotForAdjust(nowMs);
		if (!decision.shouldSend()) {
			if (isDebugLoggingEnabled()) {
				ProximityCrafting.LOGGER.info(
						"[PROXC-PERF] snapshot.adjust.skip menu={} elapsedMs={} minIntervalMs={}",
						this.containerId,
						decision.elapsedMs(),
						decision.minIntervalMs()
				);
			}
			return false;
		}
		return true;
	}

	private void prewarmServerRecipeBookSnapshotIfNeeded() {
		if (player.level().isClientSide || !recipeBookSourceSessionState.consumeServerSnapshotPrewarmPending()) {
			return;
		}
		getServerRecipeBookSnapshot(false, "menu_open_prewarm");
	}

	private static boolean isDebugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.SERVER.debugLogging.get();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private class MenuTrackedCraftGridPort implements TrackedCraftGridPort {
		@Override
		public Player getPlayer() {
			return player;
		}

		@Override
		public int debugContextId() {
			return containerId;
		}

		@Override
		public ItemStack getCraftGridItem(int slot) {
			return craftSlots.getItem(slot);
		}

		@Override
		public void setCraftGridItem(int slot, ItemStack stack) {
			craftSlots.setItem(slot, stack);
		}

		@Override
		public void markCraftGridChanged() {
			craftSlots.setChanged();
		}

		@Override
		public int getCraftGridSize() {
			return craftSlots.getContainerSize();
		}

		@Override
		public int getCraftGridMaxStackSize() {
			return craftSlots.getMaxStackSize();
		}

		@Override
		public void flushCraftingGridChange() {
			access.execute((level, pos) -> slotChangedCraftingGrid(ProximityCraftingMenu.this, level, player, craftSlots, resultSlots));
		}
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
			menu.trackedCraftGridSession.onContainerRemove(slot, removed.getCount());
			return removed;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack removed = super.removeItemNoUpdate(slot);
			menu.trackedCraftGridSession.onContainerRemove(slot, removed.getCount());
			return removed;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			super.setItem(slot, stack);
			menu.trackedCraftGridSession.onContainerSet(slot);
		}

		@Override
		public void clearContent() {
			super.clearContent();
			menu.trackedCraftGridSession.onContainerCleared();
		}
	}
}



