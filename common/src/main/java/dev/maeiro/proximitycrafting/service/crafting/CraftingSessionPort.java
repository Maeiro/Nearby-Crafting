package dev.maeiro.proximitycrafting.service.crafting;

import dev.maeiro.proximitycrafting.service.source.ItemSourceRef;
import dev.maeiro.proximitycrafting.service.source.SourcePriority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;

public interface CraftingSessionPort {
	void beginCraftGridBulkMutation();

	void endCraftGridBulkMutation();

	void broadcastChanges();

	int debugContextId();

	Level getLevel();

	Player getPlayer();

	BlockPos getTablePos();

	CraftingRecipe getLastPlacedRecipe();

	void setLastPlacedRecipe(CraftingRecipe recipe);

	boolean isIncludePlayerInventory();

	SourcePriority getSourcePriority();

	int getMaxShiftCraftIterations();

	boolean isDebugLoggingEnabled();

	void clearCraftGridToSourcesPlayerOrDrop();

	void setCraftSlotFromSource(int slot, ItemStack stack, ItemSourceRef sourceRef);

	boolean addCraftSlotFromSource(int slot, ItemStack stack, ItemSourceRef sourceRef);

	boolean removeFromCraftSlotToSources(int slot, int count);

	boolean canAcceptCraftSlotStack(int slot, ItemStack stack);

	ItemStack getCraftSlotItem(int slot);

	int getCraftGridMaxStackSize();
}
