package dev.compressedblocks;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 木质漏斗菜单：容量随等级变化（基础木质漏斗 1 格，一至三重压缩木质漏斗各 3 格）。
 * 原版没有 1 格 / 3 格的容器界面，{@code DispenserMenu}（固定 3×3）与 {@code HopperMenu}
 * （固定 5 格）都无法表达，因此这里按容量生成槽位，背景由 {@link WoodenHopperScreen} 自绘。
 * 坐标沿用原版容器布局：物品槽 y=18，玩家背包 y=48/66/84，快捷栏 y=106，面板 176×132。
 */
public class WoodenHopperMenu extends AbstractContainerMenu {
    /** 基础木质漏斗容量（造价极低，只留 1 格）。 */
    public static final int BASE_SIZE = 1;
    /** 一至三重压缩木质漏斗统一容量。 */
    public static final int COMPRESSED_SIZE = 3;
    /** 1 格：居中于 176 宽面板。 */
    private static final int[] SLOT_X_BASE = {79};
    /** 3 格：沿用原版发射器的三列坐标。 */
    private static final int[] SLOT_X_COMPRESSED = {62, 80, 98};
    private static final int SLOT_Y = 18;
    private static final int INV_Y = 48;
    private static final int HOTBAR_Y = 106;

    private final Container container;
    private final int size;

    public WoodenHopperMenu(MenuType<WoodenHopperMenu> type, int id, Inventory playerInventory,
                            Container container, int size) {
        super(type, id);
        checkContainerSize(container, size);
        this.container = container;
        this.size = size;
        container.startOpen(playerInventory.player);

        int[] xs = size == BASE_SIZE ? SLOT_X_BASE : SLOT_X_COMPRESSED;
        for (int i = 0; i < size; i++) {
            this.addSlot(new Slot(container, i, xs[i], SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    /** 容器槽数量（1 或 3），供界面绘制背景凹槽用。 */
    public int getContainerSlots() {
        return this.size;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < this.size) {
                // 漏斗 → 玩家背包
                if (!this.moveItemStackTo(stack, this.size, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, this.size, false)) {
                // 玩家背包 → 漏斗
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
