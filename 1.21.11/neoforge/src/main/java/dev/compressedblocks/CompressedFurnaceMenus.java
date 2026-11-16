package dev.compressedblocks;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * 压缩熔炉的 3 个菜单类型（N = 3 / 6 / 9；4 重复用 N=9）。
 *
 * <p>工厂只拿得到 (id, inventory)，所以客户端用哑 SimpleContainer(3N) + SimpleContainerData(2N+1)
 * 建菜单；真实槽位内容与进度由原版槽位同步与 data slots 同步（与仓库 WoodenHopper/滚动容器同款范式）。
 * tier 决定槽位布局（1 重左右分区，其余 3 列网格），tier4 与 tier3 布局一致。</p>
 */
public final class CompressedFurnaceMenus {
    public static final MenuType<CompressedFurnaceMenu> MENU_1X = new MenuType<>(
        (id, inventory) -> new CompressedFurnaceMenu(forTier(1), id, inventory,
            new SimpleContainer(CompressedFurnaceData.slotCount(3)),
            new SimpleContainerData(CompressedFurnaceData.dataCount(3)), 1),
        FeatureFlags.VANILLA_SET);
    public static final MenuType<CompressedFurnaceMenu> MENU_2X = new MenuType<>(
        (id, inventory) -> new CompressedFurnaceMenu(forTier(2), id, inventory,
            new SimpleContainer(CompressedFurnaceData.slotCount(6)),
            new SimpleContainerData(CompressedFurnaceData.dataCount(6)), 2),
        FeatureFlags.VANILLA_SET);
    public static final MenuType<CompressedFurnaceMenu> MENU_3X = new MenuType<>(
        (id, inventory) -> new CompressedFurnaceMenu(forTier(3), id, inventory,
            new SimpleContainer(CompressedFurnaceData.slotCount(9)),
            new SimpleContainerData(CompressedFurnaceData.dataCount(9)), 3),
        FeatureFlags.VANILLA_SET);

    private CompressedFurnaceMenus() {
    }

    public static MenuType<CompressedFurnaceMenu> forTier(int tier) {
        return switch (tier) {
            case 1 -> MENU_1X;
            case 2 -> MENU_2X;
            default -> MENU_3X;
        };
    }
}
