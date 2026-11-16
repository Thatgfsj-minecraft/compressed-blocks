package dev.compressedblocks;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;

/**
 * 压缩熔炉菜单（并行多路）。
 *
 * <p>菜单槽列表按分区顺序排列（与服务端/客户端一致）：0..N-1 输入、N..2N-1 燃料、
 * 2N..3N-1 产出、随后 27 格背包 + 9 格快捷栏。容器索引仍是交错布局（见
 * {@link CompressedFurnaceData}），Slot 构造时显式传入容器索引。</p>
 *
 * <p>布局：1 重为「左列上 3 输入 / 下 3 燃料、右列 3 产出」（面板 176x154）；
 * 2/3/4 重为三列区网格（面板 176x154 / 176x174）。进度与燃烧池经 ContainerData(2N+1)
 * 同步：2i 进度千分比、2i+1 活跃标志、2N 燃烧池千分比。</p>
 */
public class CompressedFurnaceMenu extends AbstractContainerMenu {
    private static final int PANEL_W = 176;
    private static final int BODY_TOP = 17;
    private static final int ROW_H = 20;

    private final Container container;
    private final ContainerData data;
    private final int tier;
    private final int lanes;
    /** 1 重：左列上 3 输入 / 下 3 燃料、右列 3 产出。 */
    private final boolean splitLayout;
    private final Level level;
    private final RecipePropertySet acceptedInputs;

    public CompressedFurnaceMenu(MenuType<CompressedFurnaceMenu> type, int id, Inventory playerInventory,
                                 Container container, ContainerData data, int tier) {
        super(type, id);
        this.tier = tier;
        this.lanes = CompressedFurnaceData.lanesForTier(tier);
        this.splitLayout = tier == 1;
        this.container = container;
        this.data = data;
        this.level = playerInventory.player.level();
        this.acceptedInputs = this.level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT);
        checkContainerSize(container, CompressedFurnaceData.slotCount(this.lanes));
        checkContainerDataCount(data, CompressedFurnaceData.dataCount(this.lanes));
        for (int lane = 0; lane < this.lanes; lane++) {
            int col = lane % 3;
            int row = lane / 3;
            this.addSlot(new InputSlot(container,
                CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_INPUT),
                this.inputX(col), this.inputY(row), this.tier));
            this.addSlot(new FuelSlot(container,
                CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_FUEL),
                this.fuelX(col), this.fuelY(row), this.level));
            this.addSlot(new FurnaceResultSlot(playerInventory.player, container,
                CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_OUTPUT),
                this.outputX(col), this.outputY(row)));
        }
        this.addStandardInventorySlots(playerInventory, 8, this.inventoryY());
        this.addDataSlots(data);
    }

    // ---------------------------------------------------------------- 布局度量

    public int tier() {
        return this.tier;
    }

    public int lanes() {
        return this.lanes;
    }

    public boolean splitLayout() {
        return this.splitLayout;
    }

    /** 面板宽度固定 176（可继续借用原版 generic_54 的顶边/背包区贴图）。 */
    public int imageWidth() {
        return PANEL_W;
    }

    /** 物品区高度：1 重 2 行（左右分区），其余按 R = N/3 行、行距 20。 */
    public int bodyHeight() {
        return this.splitLayout ? 2 * ROW_H : ROW_H * ((this.lanes + 2) / 3);
    }

    public int imageHeight() {
        return BODY_TOP + this.bodyHeight() + 96 + 1;
    }

    public int inventoryLabelY() {
        return BODY_TOP + this.bodyHeight() + 1;
    }

    public int inventoryY() {
        return BODY_TOP + this.bodyHeight() + 14;
    }

    private int inputX(int col) {
        return (this.splitLayout ? 8 : 6) + 18 * col;
    }

    private int inputY(int row) {
        return 18 + (this.splitLayout ? 0 : ROW_H * row);
    }

    private int fuelX(int col) {
        return (this.splitLayout ? 8 : 62) + 18 * col;
    }

    private int fuelY(int row) {
        return this.splitLayout ? 38 : 18 + ROW_H * row;
    }

    private int outputX(int col) {
        return (this.splitLayout ? 116 : 118) + 18 * col;
    }

    private int outputY(int row) {
        return this.splitLayout ? 28 : 18 + ROW_H * row;
    }

    // ---------------------------------------------------------------- 进度读取

    public float cookProgress(int lane) {
        return this.data.get(CompressedFurnaceData.progressIndex(lane)) / 1000.0F;
    }

    public boolean laneActive(int lane) {
        return this.data.get(CompressedFurnaceData.activeIndex(lane)) != 0;
    }

    public float litProgress() {
        return this.data.get(CompressedFurnaceData.litIndex(this.lanes)) / 1000.0F;
    }

    public boolean isLit() {
        return this.data.get(CompressedFurnaceData.litIndex(this.lanes)) > 0;
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int containerEnd = CompressedFurnaceData.slotCount(this.lanes);
        int inventoryEnd = containerEnd + 36;
        if (index < containerEnd) {
            if (!this.moveItemStackTo(stack, containerEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
            if (index >= this.lanes * 2) {
                // 产出槽：触发经验/统计结算
                slot.onQuickCraft(original, stack);
            }
        } else if (this.canSmelt(stack)) {
            if (!this.moveItemStackTo(stack, 0, this.lanes, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.isFuel(stack)) {
            if (!this.moveItemStackTo(stack, this.lanes, this.lanes * 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < containerEnd + 27) {
            if (!this.moveItemStackTo(stack, containerEnd + 27, inventoryEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, containerEnd, containerEnd + 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    private boolean canSmelt(ItemStack stack) {
        return this.acceptedInputs.test(stack)
            && CompressedFurnaceBlockEntity.isItemAllowed(this.tier, stack);
    }

    private boolean isFuel(ItemStack stack) {
        return this.level.fuelValues().isFuel(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    // ---------------------------------------------------------------- 槽位

    /** 输入槽：档位准入（客户端也走静态判定，保证预览正确）。 */
    private static final class InputSlot extends Slot {
        private final int tier;

        private InputSlot(Container container, int slot, int x, int y, int tier) {
            super(container, slot, x, y);
            this.tier = tier;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return CompressedFurnaceBlockEntity.isItemAllowed(this.tier, stack);
        }
    }

    /** 燃料槽：原版燃料判定 + 空桶（湿海绵机制），桶单格上限 1。 */
    private static final class FuelSlot extends Slot {
        private final Level level;

        private FuelSlot(Container container, int slot, int x, int y, Level level) {
            super(container, slot, x, y);
            this.level = level;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.level.fuelValues().isFuel(stack) || stack.is(Items.BUCKET);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stack.is(Items.BUCKET) ? 1 : super.getMaxStackSize(stack);
        }
    }
}
