package dev.maeiro.nearbycrafting.compat.sophisticatedbackpacks.upgrade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeItem;

import java.util.List;

public class AdvancedCraftingUpgradeItem extends UpgradeItemBase<AdvancedCraftingUpgradeWrapper> {
	private static final UpgradeType<AdvancedCraftingUpgradeWrapper> TYPE = new UpgradeType<>(AdvancedCraftingUpgradeWrapper::new);

	public AdvancedCraftingUpgradeItem(IUpgradeCountLimitConfig upgradeCountLimitConfig) {
		super(upgradeCountLimitConfig);
	}

	public static Item createDefaultLimited() {
		IUpgradeCountLimitConfig onePerStorageLimit = new IUpgradeCountLimitConfig() {
			@Override
			public int getMaxUpgradesPerStorage(String storageType, ResourceLocation upgradeRegistryName) {
				return 1;
			}

			@Override
			public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup upgradeGroup) {
				return Integer.MAX_VALUE;
			}
		};
		return new AdvancedCraftingUpgradeItem(onePerStorageLimit);
	}

	@Override
	public UpgradeType<AdvancedCraftingUpgradeWrapper> getType() {
		return TYPE;
	}

	@Override
	public List<IUpgradeItem.UpgradeConflictDefinition> getUpgradeConflicts() {
		return List.of(
				new IUpgradeItem.UpgradeConflictDefinition(
						item -> item instanceof CraftingUpgradeItem,
						0,
						Component.translatable("nearbycrafting.upgrade.advanced_crafting.conflict")
				)
		);
	}
}
