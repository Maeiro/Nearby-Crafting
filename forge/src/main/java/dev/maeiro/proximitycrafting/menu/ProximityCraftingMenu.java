package dev.maeiro.proximitycrafting.menu;

import dev.maeiro.proximitycrafting.ProximityCrafting;
import dev.maeiro.proximitycrafting.config.ClientPreferences;
import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.slot.ProximityResultSlot;
import dev.maeiro.proximitycrafting.networking.ProximityCraftingNetwork;
import dev.maeiro.proximitycrafting.networking.S2CRecipeBookSourceSnapshot;
import dev.maeiro.proximitycrafting.registry.ModBlocks;
import dev.maeiro.proximitycrafting.registry.ModMenuTypes;
import dev.maeiro.proximitycrafting.networking.payload.RecipeBookSourceEntry;
import dev.maeiro.proximitycrafting.networking.request.ServerMenuRequestHost;
import dev.maeiro.proximitycrafting.service.crafting.CraftingResultOperations;
import dev.maeiro.proximitycrafting.service.crafting.CraftingResultPort;
import dev.maeiro.proximitycrafting.service.crafting.FillResult;
import dev.maeiro.proximitycrafting.service.crafting.MenuRuntimeController;
import dev.maeiro.proximitycrafting.service.crafting.MenuSnapshotTransport;
import dev.maeiro.proximitycrafting.service.crafting.RecipeFillService;
import dev.maeiro.proximitycrafting.service.crafting.RecipeBookSnapshotSourcePort;
import dev.maeiro.proximitycrafting.service.crafting.ResultTakeOperations;
import dev.maeiro.proximitycrafting.service.crafting.ResultTakeOutcome;
import dev.maeiro.proximitycrafting.service.crafting.ResultTakePort;
import dev.maeiro.proximitycrafting.service.crafting.TrackedCraftGridPort;
import dev.maeiro.proximitycrafting.service.crafting.TrackedCraftGridSession;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
import dev.maeiro.proximitycrafting.service.scan.ForgeScanOptionsFactory;
import dev.maeiro.proximitycrafting.service.scan.ProximityInventoryScanner;
import dev.maeiro.proximitycrafting.service.scan.ScanOptions;
import dev.maeiro.proximitycrafting.service.scan.SourceCollectionResult;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

public class ProximityCraftingMenu extends RecipeBookMenu<CraftingContainer> implements ServerMenuRequestHost {
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
	private final MenuRuntimeController menuRuntimeController =
			new MenuRuntimeController(SERVER_SNAPSHOT_CACHE_TTL_MS, ADJUST_SNAPSHOT_MIN_INTERVAL_MS);
	private final TrackedCraftGridPort trackedCraftGridPort = new MenuTrackedCraftGridPort();
	private final CraftingResultPort craftingResultPort = new MenuCraftingResultPort();
	private final ResultTakePort resultTakePort = new MenuResultTakePort();
	private final MenuSnapshotTransport snapshotTransport = new PlatformMenuSnapshotTransport();
	private final RecipeBookSnapshotSourcePort snapshotSourcePort = new MenuRecipeBookSnapshotSourcePort();
	private boolean resultShiftCraftInProgress;
	private ClientPreferences clientPreferences = ClientPreferences.defaults();

	private CraftingRecipe lastPlacedRecipe;

	public ProximityCraftingMenu(int containerId, Inventory playerInventory, BlockPos tablePos) {
		this(ModMenuTypes.PROXIMITY_CRAFTING_MENU.get(), containerId, playerInventory, tablePos);
	}

	public ProximityCraftingMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, BlockPos tablePos) {
		super(menuType, containerId);
		this.player = playerInventory.player;
		this.tablePos = tablePos.immutable();
		this.access = ContainerLevelAccess.create(playerInventory.player.level(), tablePos);
		this.trackedCraftGridSession = new TrackedCraftGridSession(9);
		this.craftSlots = new SourceTrackingCraftingContainer(this, this.trackedCraftGridSession, 3, 3);

		this.addSlot(new ProximityResultSlot(this::handleResultSlotTake, playerInventory.player, this.craftSlots, this.resultSlots, 0, 124, 35));

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

	@Override
	public void slotsChanged(Container inventory) {
		menuRuntimeController.onSlotsChanged(trackedCraftGridSession, this::refreshCraftingResult);
	}

	@Override
	public void fillCraftSlotsStackedContents(StackedContents itemHelper) {
		this.craftSlots.fillStackedContents(itemHelper);
		menuRuntimeController.fillSupplementalRecipeBookSources(itemHelper);
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
		menuRuntimeController.onBroadcastChanges(this, snapshotSourcePort);
	}

	@Override
	public void handlePlacement(boolean placeAll, Recipe<?> recipe, ServerPlayer player) {
		if (recipe instanceof CraftingRecipe craftingRecipe) {
			FillResult fillResult = RecipeFillService.fillFromRecipe(this, craftingRecipe, placeAll);
			if (!fillResult.success()) {
				player.displayClientMessage(net.minecraft.network.chat.Component.translatable(fillResult.messageKey()), true);
			} else if (fillResult.craftedAmount() > 0) {
				menuRuntimeController.onSnapshotInputsChanged();
			}
			return;
		}
		super.handlePlacement(placeAll, recipe, player);
	}

	public FillResult fillRecipeById(ResourceLocation recipeId, boolean craftAll) {
		FillResult fillResult = RecipeFillService.fillRecipeById(this, recipeId, craftAll);
		if (fillResult.success() && fillResult.craftedAmount() > 0) {
			menuRuntimeController.onSnapshotInputsChanged();
		}
		return fillResult;
	}

	public FillResult adjustRecipeLoad(int steps) {
		FillResult batchResult = RecipeFillService.adjustRecipeLoad(this, steps);
		int appliedSteps = batchResult.success() ? batchResult.craftedAmount() : 0;
		if (appliedSteps > 0) {
			menuRuntimeController.onSnapshotInputsChanged();
		}
		return batchResult;
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
		if (debugLoggingEnabled() && clearResult.clearedSlots() > 0) {
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
		if (debugLoggingEnabled()) {
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

	@Override
	public int containerId() {
		return this.containerId;
	}

	@Override
	public boolean debugLoggingEnabled() {
		try {
			return ProximityCraftingConfig.serverRuntimeSettings().debugLogging();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	@Override
	public boolean isClientSide() {
		return player.level().isClientSide;
	}

	public CraftingRecipe getLastPlacedRecipe() {
		return lastPlacedRecipe;
	}

	public void setLastPlacedRecipe(CraftingRecipe lastPlacedRecipe) {
		this.lastPlacedRecipe = lastPlacedRecipe;
	}

	public boolean isAutoRefillAfterCraft() {
		return clientPreferences.autoRefillAfterCraft();
	}

	public boolean isResultShiftCraftInProgress() {
		return resultShiftCraftInProgress;
	}

	public void handleResultSlotTake(ServerPlayer serverPlayer) {
		ResultTakeOutcome outcome = ResultTakeOperations.afterResultTaken(resultTakePort);
		menuRuntimeController.onResultTaken(this, snapshotTransport, serverPlayer, outcome, snapshotSourcePort);
	}

	public boolean isIncludePlayerInventory() {
		return clientPreferences.includePlayerInventory();
	}

	public SourcePriority getSourcePriority() {
		return clientPreferences.sourcePriority();
	}

	public void setClientPreferences(ClientPreferences preferences) {
		ClientPreferences resolvedPreferences = preferences == null ? ClientPreferences.defaults() : preferences;
		menuRuntimeController.onClientPreferencesChanged(this.clientPreferences, resolvedPreferences);
		this.clientPreferences = resolvedPreferences;
	}

	public void setClientPreferences(boolean autoRefillAfterCraft, boolean includePlayerInventory, SourcePriority sourcePriority) {
		setClientPreferences(ClientPreferences.of(autoRefillAfterCraft, includePlayerInventory, sourcePriority));
	}

	public boolean setClientRecipeBookSupplementalSources(List<RecipeBookSourceEntry> sourceEntries) {
		return menuRuntimeController.setClientRecipeBookSupplementalSources(sourceEntries);
	}

	public List<RecipeBookSourceEntry> getClientRecipeBookSupplementalSources() {
		return menuRuntimeController.getClientRecipeBookSupplementalSources();
	}

	public List<RecipeBookSourceEntry> getServerRecipeBookSnapshot(boolean preferCache, String reason) {
		return menuRuntimeController.getServerRecipeBookSnapshot(this, preferCache, reason, snapshotSourcePort);
	}

	public void invalidateServerRecipeBookSnapshotCache() {
		menuRuntimeController.invalidateServerRecipeBookSnapshotCache();
	}

	public boolean shouldSendSnapshotForAdjust() {
		return menuRuntimeController.shouldSendSnapshotForAdjust(this);
	}

	public void sendRecipeBookSourceSnapshot(ServerPlayer serverPlayer, boolean preferCache, String reason) {
		menuRuntimeController.sendRecipeBookSourceSnapshot(this, snapshotTransport, serverPlayer, preferCache, reason, snapshotSourcePort);
	}

	private void refreshCraftingResult() {
		this.access.execute((level, pos) -> CraftingResultOperations.updateCraftingResult(craftingResultPort));
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
			refreshCraftingResult();
		}
	}

	private class MenuCraftingResultPort implements CraftingResultPort {
		@Override
		public AbstractContainerMenu getMenu() {
			return ProximityCraftingMenu.this;
		}

		@Override
		public Level getLevel() {
			return player.level();
		}

		@Override
		public Player getPlayer() {
			return player;
		}

		@Override
		public CraftingContainer getCraftSlots() {
			return craftSlots;
		}

		@Override
		public ResultContainer getResultSlots() {
			return resultSlots;
		}

		@Override
		public CraftingRecipe getTrackedRecipe() {
			return lastPlacedRecipe;
		}
	}

	private class MenuResultTakePort implements ResultTakePort {
		@Override
		public boolean isAutoRefillAfterCraft() {
			return clientPreferences.autoRefillAfterCraft();
		}

		@Override
		public boolean isResultShiftCraftInProgress() {
			return resultShiftCraftInProgress;
		}

		@Override
		public FillResult refillLastRecipe() {
			return RecipeFillService.refillLastRecipe(ProximityCraftingMenu.this);
		}
	}

	private class PlatformMenuSnapshotTransport implements MenuSnapshotTransport {
		@Override
		public void sendRecipeBookSourceSnapshot(ServerPlayer serverPlayer, int containerId, List<RecipeBookSourceEntry> entries) {
			ProximityCraftingNetwork.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> serverPlayer),
					new S2CRecipeBookSourceSnapshot(containerId, entries)
			);
		}
	}

	private class MenuRecipeBookSnapshotSourcePort implements RecipeBookSnapshotSourcePort {
		@Override
		public int debugContextId() {
			return containerId;
		}

		@Override
		public boolean debugLoggingEnabled() {
			return ProximityCraftingMenu.this.debugLoggingEnabled();
		}

		@Override
		public List<ItemSourceRef> collectRecipeBookSourceRefs() {
			ScanOptions scanOptions = ForgeScanOptionsFactory.fromMenu(ProximityCraftingMenu.this);
			SourceCollectionResult result = ProximityInventoryScanner.collectSourceResult(
					getLevel(),
					getTablePos(),
					getPlayer(),
					scanOptions
			);
			return result.recipeBookSupplementalSources();
		}
	}
}

