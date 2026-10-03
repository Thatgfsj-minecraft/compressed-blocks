package dev.compressedblocks;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 滚动容器菜单（压缩箱子/压缩潜影盒共用）：背后 243 格（27 行×9 列）。
 * 固定映射：菜单槽 i 永远对应容器第 i 格，服务端对滚动零感知——点击/拖拽/右键分半/
 * shift-click 全部走原版协议、覆盖全容器（Sophisticated Storage 同款手法）。
 * 滚动是客户端取景窗：setScrollRowLocal 只在客户端被 Screen 调用（挪槽位 y+可见性），
 * 不产生任何网络流量；全部 279 槽（243+36）随菜单正常同步，客户端天然持有全部内容。
 */
public class ScrollingContainerMenu extends AbstractContainerMenu {
    public static final int ROWS = 27;
    public static final int COLS = 9;
    public static final int VISIBLE_ROWS = 6;
    public static final int MAX_ROW = ROWS - VISIBLE_ROWS;
    public static final int SIZE = ROWS * COLS;
    /** 玩家背包槽在菜单槽列表里的起始 index（0..SIZE-1 为容器槽）。 */
    public static final int PLAYER_SLOTS_START = SIZE;

    private final Container backing0;
    /** 客户端取景窗首行；服务端恒 0 且不使用。 */
    private int scrollRow;

    public ScrollingContainerMenu(MenuType<ScrollingContainerMenu> type, int id, Inventory inv,
                                  Container backing) {
        super(type, id);
        checkContainerSize(backing, SIZE);
        this.backing0 = backing;
        // 潜影盒 BE 覆写计数开盖（blockEvent 同步动画）；客户端哑容器为 default 空实现
        this.backing0.startOpen(inv.player);
        for (int i = 0; i < SIZE; i++) {
            addSlot(new StorageSlot(backing, i, 8 + (i % COLS) * 18, 18 + (i / COLS) * 18, i / COLS));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < COLS; c++) {
                addSlot(new Slot(inv, 9 + r * COLS + c, 8 + c * 18, 139 + r * 18));
            }
        }
        for (int c = 0; c < COLS; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 197));
        }
        this.applyScroll();
    }

    /** 当前取景窗首行（仅客户端有意义）。 */
    public int getScrollRow() {
        return this.scrollRow;
    }

    /** 客户端本地滚动（Screen 滚轮/滚动条调用）：挪窗口槽 y 与可见性，零网络。 */
    public void setScrollRowLocal(int row) {
        int next = Math.max(0, Math.min(MAX_ROW, row));
        if (next != this.scrollRow) {
            this.scrollRow = next;
            this.applyScroll();
        }
    }

    /** 按取景窗重摆容器槽：可见行落进 6 行窗口，隐藏行挪出屏幕（y=-1000）。 */
    private void applyScroll() {
        for (int i = 0; i < SIZE; i++) {
            Slot slot = this.slots.get(i);
            int rel = i / COLS - this.scrollRow;
            slot.y = (rel >= 0 && rel < VISIBLE_ROWS) ? 18 + rel * 18 : -1000;
        }
    }

    /** 该行是否在取景窗内（StorageSlot 的 isActive/isHovering 门控）。 */
    public boolean isRowVisible(int row) {
        return row >= this.scrollRow && row < this.scrollRow + VISIBLE_ROWS;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_SLOTS_START) {
            // 容器 → 玩家背包
            if (!this.moveItemStackTo(stack, PLAYER_SLOTS_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, PLAYER_SLOTS_START, false)) {
            // 玩家背包 → 全容器（含藏着的行，与其他大容器 mod 一致）
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.backing0.stopOpen(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.backing0.stillValid(player);
    }

    /** 容器槽：isActive 只放行取景窗内的行（原版渲染与 getHoveredSlot 点击都按此门控）。 */
    private class StorageSlot extends Slot {
        private final int row;

        StorageSlot(Container container, int index, int x, int y, int row) {
            super(container, index, x, y);
            this.row = row;
        }

        @Override
        public boolean isActive() {
            return isRowVisible(this.row);
        }
    }
}
