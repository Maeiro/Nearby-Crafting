package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.config.ProximityCraftingConfig;
import dev.maeiro.proximitycrafting.menu.ProximityCraftingMenu;
import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;

final class NeoForgeCraftingSessionAdapter implements CraftingSessionPort {
	private final ProximityCraftingMenu menu;

	NeoForgeCraftingSessionAdapter(ProximityCraftingMenu menu) {
		this.menu = menu;
	}

	@Override
	public void beginCraftGridBulkMutation() {
		menu.beginCraftGridBulkMutation();
	}

	@Override
	public void endCraftGridBulkMutation() {
		menu.endCraftGridBulkMutation();
	}

	@Override
	public void broadcastChanges() {
		menu.broadcastChanges();
	}

	@Override
	public int debugContextId() {
		return menu.containerId;
	}

	@Override
	public Level getLevel() {
		return menu.getLevel();
	}

	@Override
	public Player getPlayer() {
		return menu.getPlayer();
	}

	@Override
	public BlockPos getTablePos() {
		return menu.getTablePos();
	}

	@Override
	public CraftingRecipe getLastPlacedRecipe() {
		return menu.getLastPlacedRecipe();
	}

	@Override
	public void setLastPlacedRecipe(CraftingRecipe recipe) {
		menu.setLastPlacedRecipe(recipe);
	}

	@Override
	public boolean isIncludePlayerInventory() {
		return menu.isIncludePlayerInventory();
	}

	@Override
	public SourcePriority getSourcePriority() {
		return menu.getSourcePriority();
	}

	@Override
	public int getMaxShiftCraftIterations() {
		return ProximityCraftingConfig.serverRuntimeSettings().maxShiftCraftIterations();
	}

	@Override
	public boolean isDebugLoggingEnabled() {
		return ProximityCraftingConfig.serverRuntimeSettings().debugLogging();
	}

	@Override
	public void clearCraftGridToSourcesPlayerOrDrop() {
		menu.clearCraftGridToPlayerOrDrop();
	}

	@Override
	public void setCraftSlotFromSource(int slot, ItemStack stack, ItemSourceRef sourceRef) {
		menu.setCraftSlotFromSource(slot, stack, sourceRef);
	}

	@Override
	public boolean addCraftSlotFromSource(int slot, ItemStack stack, ItemSourceRef sourceRef) {
		return menu.addCraftSlotFromSource(slot, stack, sourceRef);
	}

	@Override
	public boolean removeFromCraftSlotToSources(int slot, int count) {
		return menu.removeFromCraftSlotToSources(slot, count);
	}

	@Override
	public boolean canAcceptCraftSlotStack(int slot, ItemStack stack) {
		return menu.canAcceptCraftSlotStack(slot, stack);
	}

	@Override
	public ItemStack getCraftSlotItem(int slot) {
		return menu.getCraftSlots().getItem(slot);
	}

	@Override
	public int getCraftGridMaxStackSize() {
		return menu.getCraftSlots().getMaxStackSize();
	}
}

