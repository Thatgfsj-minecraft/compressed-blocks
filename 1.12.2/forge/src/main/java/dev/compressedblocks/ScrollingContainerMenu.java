package dev.compressedblocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 滚动容器菜单（压缩箱子/压缩潜影盒共用）：背后 243 格（27 行×9 列）。
 * 固定映射：菜单槽 i 永远对应容器第 i 格，服务端对滚动零感知——点击/拖拽/右键分半/
 * shift-click 全部走原版协议、覆盖全容器。
 * 滚动是客户端取景窗：setScrollRowLocal 只在客户端被 Screen 调用（重建槽位列表），
 * 不产生任何网络流量。1.12.2 的 Slot x/y 同样 final，沿用 1.16.5 的同序重建方案。
 */
public class ScrollingContainerMenu extends Container {
    public static final int ROWS = 27;
    public static final int COLS = 9;
    public static final int VISIBLE_ROWS = 6;
    public static final int MAX_ROW = ROWS - VISIBLE_ROWS;
    public static final int SIZE = ROWS * COLS;
    /** 玩家背包槽在菜单槽列表里的起始 index（0..SIZE-1 为容器槽）。 */
    public static final int PLAYER_SLOTS_START = SIZE;

    private final ScrollingContainerBlockEntity backing;
    private final boolean keepsContents;
    private final BlockPos pos;
    private final World world;
    private final InventoryPlayer playerInv;
    /** 客户端取景窗首行；服务端恒 0 且不使用。 */
    private int scrollRow;

    public ScrollingContainerMenu(InventoryPlayer playerInv, World world, boolean keepsContents, BlockPos pos) {
        this.playerInv = playerInv;
        this.world = world;
        this.keepsContents = keepsContents;
        this.pos = pos;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        this.backing = te instanceof ScrollingContainerBlockEntity
            ? (ScrollingContainerBlockEntity) te : null;
        if (this.backing != null) {
            this.backing.openInventory(playerInv.player);
        }
        for (int i = 0; i < SIZE; i++) {
            addSlotToContainer(new StorageSlot(this, i, 8 + (i % COLS) * 18, 18 + (i / COLS) * 18, i / COLS));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < COLS; c++) {
                addSlotToContainer(new Slot(playerInv, 9 + r * COLS + c, 8 + c * 18, 140 + r * 18));
            }
        }
        for (int c = 0; c < COLS; c++) {
            addSlotToContainer(new Slot(playerInv, c, 8 + c * 18, 198));
        }
        this.applyScroll();
    }

    /** 当前取景窗首行（仅客户端有意义）。 */
    public int getScrollRow() {
        return this.scrollRow;
    }

    /** 客户端本地滚动（Screen 滚轮/滚动条调用）：重建槽位列表，零网络。 */
    public void setScrollRowLocal(int row) {
        int next = Math.max(0, Math.min(MAX_ROW, row));
        if (next != this.scrollRow) {
            this.scrollRow = next;
            this.applyScroll();
        }
    }

    /**
     * 按取景窗重摆槽位：1.12.2 的 Slot 坐标同样 final，无法原地挪 y——
     * 同序重建整个槽位列表（容器槽 index→格子映射不变，服务端协议层面零感知；
     * scrollRow 只在客户端被 Screen 调用，重建也只发生在客户端）。
     */
    private void applyScroll() {
        this.inventorySlots.clear();
        this.inventoryItemStacks.clear();
        for (int i = 0; i < SIZE; i++) {
            int rel = i / COLS - this.scrollRow;
            int y = (rel >= 0 && rel < VISIBLE_ROWS) ? 18 + rel * 18 : -1000;
            this.inventorySlots.add(new StorageSlot(this, i, 8 + (i % COLS) * 18, y, i / COLS));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < COLS; c++) {
                this.inventorySlots.add(new Slot(this.playerInv, 9 + r * COLS + c, 8 + c * 18, 140 + r * 18));
            }
        }
        for (int c = 0; c < COLS; c++) {
            this.inventorySlots.add(new Slot(this.playerInv, c, 8 + c * 18, 198));
        }
    }

    /** 该行是否在取景窗内（StorageSlot 的 isEnabled 门控）。 */
    public boolean isRowVisible(int row) {
        return row >= this.scrollRow && row < this.scrollRow + VISIBLE_ROWS;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < PLAYER_SLOTS_START) {
            // 容器 → 玩家背包
            if (!this.mergeItemStack(stack, PLAYER_SLOTS_START, this.inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.mergeItemStack(stack, 0, PLAYER_SLOTS_START, false)) {
            // 玩家背包 → 全容器（含藏着的行，与其他大容器 mod 一致）
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return original;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.backing != null) {
            this.backing.closeInventory(player);
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.backing != null && !this.backing.isInvalid()
            && player.getDistanceSq(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
    }

    /** 容器槽：isEnabled 只放行取景窗内的行（原版渲染与点击都按此门控）。 */
    private final class StorageSlot extends Slot {
        private final int row;

        StorageSlot(ScrollingContainerMenu menu, int index, int x, int y, int row) {
            super(menu.backing, index, x, y);
            this.row = row;
        }

        @Override
        public boolean isEnabled() {
            return isRowVisible(this.row);
        }
    }
}
