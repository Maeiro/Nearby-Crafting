package dev.maeiro.nearbycrafting.client.compat.emi;

import dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade.AdvancedCraftingUpgradeContainer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AdvancedBackpackScreenHelper {
	private AdvancedBackpackScreenHelper() {
	}

	public static Optional<BackpackContainer> getBackpackMenu(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return Optional.empty();
		}
		if (!(containerScreen.getMenu() instanceof BackpackContainer backpackMenu)) {
			return Optional.empty();
		}
		return Optional.of(backpackMenu);
	}

	public static boolean isAdvancedBackpackScreen(Screen screen) {
		return getBackpackMenu(screen)
				.flatMap(AdvancedBackpackScreenHelper::getAdvancedContainer)
				.isPresent();
	}

	public static Optional<AdvancedCraftingUpgradeContainer> getAdvancedContainer(BackpackContainer menu) {
		Optional<UpgradeContainerBase<?, ?>> openContainerOptional = menu.getOpenContainer();
		if (openContainerOptional.isEmpty()) {
			return Optional.empty();
		}
		if (!(openContainerOptional.get() instanceof AdvancedCraftingUpgradeContainer advancedContainer)) {
			return Optional.empty();
		}
		return Optional.of(advancedContainer);
	}

	public static List<ItemStack> getCraftMatrixStacks(Screen screen) {
		return getBackpackMenu(screen)
				.flatMap(AdvancedBackpackScreenHelper::getAdvancedContainer)
				.map(advancedContainer -> {
					Container craftMatrix = advancedContainer.getCraftMatrix();
					List<ItemStack> stacks = new ArrayList<>(craftMatrix.getContainerSize());
					for (int slot = 0; slot < craftMatrix.getContainerSize(); slot++) {
						stacks.add(craftMatrix.getItem(slot).copy());
					}
					return stacks;
				})
				.orElse(List.of());
	}
}
