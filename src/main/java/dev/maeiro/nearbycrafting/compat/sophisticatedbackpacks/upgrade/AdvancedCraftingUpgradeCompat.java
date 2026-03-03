package dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.SBPButtonDefinitions;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeTab;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeWrapper;

public final class AdvancedCraftingUpgradeCompat {
	private static final String SOPHISTICATED_BACKPACKS_MOD_ID = "sophisticatedbackpacks";
	private static final String SOPHISTICATED_CORE_MOD_ID = "sophisticatedcore";

	private static final UpgradeContainerType<CraftingUpgradeWrapper, CraftingUpgradeContainer> ADVANCED_CRAFTING_TYPE =
			new UpgradeContainerType<>(AdvancedCraftingUpgradeContainer::new);

	private AdvancedCraftingUpgradeCompat() {
	}

	public static void onRegisterEvent(RegisterEvent event) {
		if (!isCompatibilityLoaded()) {
			NearbyCrafting.LOGGER.info("[NC-ADV-UPGRADE] SB/Core not loaded - skipping advanced crafting upgrade container/tab registration");
			return;
		}
		if (!event.getRegistryKey().equals(ForgeRegistries.Keys.MENU_TYPES)) {
			return;
		}

		UpgradeContainerRegistry.register(ModItems.ADVANCED_CRAFTING_UPGRADE.getId(), ADVANCED_CRAFTING_TYPE);
		NearbyCrafting.LOGGER.info("[NC-ADV-UPGRADE] Registered advanced crafting upgrade container type for {}", ModItems.ADVANCED_CRAFTING_UPGRADE.getId());

		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				UpgradeGuiManager.registerTab(
						ADVANCED_CRAFTING_TYPE,
						(CraftingUpgradeContainer container, Position position, StorageScreenBase<?> screen) ->
								new CraftingUpgradeTab(container, position, screen, SBPButtonDefinitions.SHIFT_CLICK_TARGET, SBPButtonDefinitions.REFILL_CRAFTING_GRID)
				)
		);
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				NearbyCrafting.LOGGER.info("[NC-ADV-UPGRADE] Registered advanced crafting upgrade tab")
		);
	}

	private static boolean isCompatibilityLoaded() {
		return ModList.get().isLoaded(SOPHISTICATED_BACKPACKS_MOD_ID) && ModList.get().isLoaded(SOPHISTICATED_CORE_MOD_ID);
	}
}
