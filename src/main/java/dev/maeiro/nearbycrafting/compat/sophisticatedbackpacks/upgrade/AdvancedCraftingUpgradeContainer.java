package dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade;

import dev.maeiro.nearbycrafting.NearbyCrafting;
import dev.maeiro.nearbycrafting.config.NearbyCraftingConfig;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeWrapper;

public class AdvancedCraftingUpgradeContainer extends CraftingUpgradeContainer {
	public AdvancedCraftingUpgradeContainer(
			Player player,
			int upgradeContainerId,
			CraftingUpgradeWrapper upgradeWrapper,
			UpgradeContainerType<CraftingUpgradeWrapper, CraftingUpgradeContainer> type
	) {
		super(player, upgradeContainerId, upgradeWrapper, type);
	}

	@Override
	public void onInit() {
		super.onInit();
		if (!player.level().isClientSide && upgradeWrapper instanceof AdvancedCraftingUpgradeWrapper advancedWrapper) {
			advancedWrapper.setScanAnchor(player.blockPosition());
			if (NearbyCraftingConfig.SERVER.debugLogging.get()) {
				NearbyCrafting.LOGGER.info(
						"[NC-ADV-UPGRADE] container init player={} anchor={} wrapper={}",
						player.getGameProfile().getName(),
						player.blockPosition(),
						upgradeWrapper.getClass().getName()
				);
			}
		}
	}
}
