package dev.compressedblocks;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 滚动容器菜单（压缩箱子/压缩潜影盒共用）：背后 243 格（27 行×9 列），窗口只映射 6 行。
 * 滚动走原版容器按钮包（讲台翻页同机制）：0=上滚一行、1=下滚一行、≥100 跳到指定行；
 * scrollRow 经 DataSlot 同步给客户端画滚动条。滚动时服务端重建槽位映射并整包广播。
 * 客户端菜单用哑容器构造（同原版 ChestMenu 网络构造，无需 BlockPos 过网络），
 * 槽位按索引显示服务端同步内容；Screen 滚轮时先本地预测滚动再发包，服务端确认后一致。
 */
public class ScrollingContainerMenu extends AbstractContainerMenu {
    public static final int ROWS = 27;
    public static final int COLS = 9;
    public static final int VISIBLE_ROWS = 6;
    public static final int WINDOW_SLOTS = VISIBLE_ROWS * COLS;
    public static final int MAX_ROW = ROWS - VISIBLE_ROWS;
    public static final int SIZE = ROWS * COLS;

    private final Container backing0;
    private final Inventory playerInv;
    private final DataSlot scrollRow = DataSlot.standalone();

    public ScrollingContainerMenu(MenuType<ScrollingContainerMenu> type, int id, Inventory inv,
                                  Container backing) {
        super(type, id);
        checkContainerSize(backing, SIZE);
        this.backing0 = backing;
        this.playerInv = inv;
        addDataSlot(this.scrollRow);
        this.layoutSlots();
    }

    /** 当前滚动到的首行（DataSlot，服务端权威，自动同步）。 */
    public int scrollRow() {
        return this.scrollRow.get();
    }

    /** 重建槽位表：54 窗口槽（映射滚动区间）+ 36 背包槽。 */
    private void layoutSlots() {
        this.slots.clear();
        int base = this.scrollRow.get() * COLS;
        for (int i = 0; i < WINDOW_SLOTS; i++) {
            addSlot(new Slot(this.backing0, base + i, 8 + (i % COLS) * 18, 17 + (i / COLS) * 18));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < COLS; c++) {
                addSlot(new Slot(this.playerInv, 9 + r * COLS + c, 8 + c * 18, 139 + r * 18));
            }
        }
        for (int c = 0; c < COLS; c++) {
            addSlot(new Slot(this.playerInv, c, 8 + c * 18, 197));
        }
    }

    private void scrollTo(int row) {
        int next = Math.max(0, Math.min(MAX_ROW, row));
        if (next != this.scrollRow.get()) {
            this.scrollRow.set(next);
            this.layoutSlots();
            this.broadcastFullState();
        }
    }

    /** 客户端本地预测（Screen 滚轮/滚动条时调用），服务端确认后内容同步一致。 */
    public void clientScroll(int button) {
        if (button >= 100) {
            this.clientScrollTo(button - 100);
            return;
        }
        int row = this.scrollRow.get();
        int next = Math.max(0, Math.min(MAX_ROW, row + (button == 0 ? -1 : 1)));
        if (next != row) {
            this.scrollRow.set(next);
            this.layoutSlots();
        }
    }

    public void clientScrollTo(int row) {
        int next = Math.max(0, Math.min(MAX_ROW, row));
        if (next != this.scrollRow.get()) {
            this.scrollRow.set(next);
            this.layoutSlots();
        }
    }

    /** 服务端滚动入口：按钮包 0/1 逐行，≥100 跳行。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 100) {
            scrollTo(id - 100);
        } else {
            scrollTo(this.scrollRow.get() + (id == 0 ? -1 : 1));
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < WINDOW_SLOTS) {
            if (!this.moveItemStackTo(stack, WINDOW_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, WINDOW_SLOTS, false)) {
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
    public boolean stillValid(Player player) {
        return this.backing0.stillValid(player);
    }
}
