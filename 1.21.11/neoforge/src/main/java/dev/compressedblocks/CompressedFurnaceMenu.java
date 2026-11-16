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
 * <p>版面（面板宽固定 176，行数 R = N/3 = 1/2/3）：</p>
 * <pre>
 * 左上：输入区 3 列 × R 行
 * 左中：燃烧进度条（整机共享燃烧池）
 * 左下：燃料区 3 列 × R 行（从左上到右下消耗）
 * 右侧：产出区 3 列 × R 行（类似箱子，自动按同类堆叠；垂直居中于燃烧条）
 * </pre>
 * <p>1 重只有 1 行、2 重 2 行、3/4 重 3 行，低档位不绘制多余行，面板高度随行数变化。
 * 进度与燃烧池经 ContainerData(2N+1) 同步：2i 进度千分比、2i+1 活跃标志、2N 燃烧池千分比。</p>
 */
public class CompressedFurnaceMenu extends AbstractContainerMenu {
    private static final int PANEL_W = 176;
    private static final int BODY_TOP = 17;
    /** 行距：槽位 18 + 2px 每路进度条余量。 */
    private static final int ROW_PITCH = 20;
    /** 左侧（输入/燃料）3 个槽位列的起点。 */
    private static final int LEFT_X = 8;
    /** 右侧产出 3 个槽位列的起点。 */
    private static final int OUT_X = 118;
    /** 首行槽位 y（相对面板）。 */
    private static final int TOP_Y = 18;
    /** 输入区与燃料区之间的间距：放燃烧剩余时间文字。 */
    private static final int BURN_INFO_GAP = 10;

    private final Container container;
    private final ContainerData data;
    private final int tier;
    private final int lanes;
    /** 输入/燃料/产出各自的网格行数（1/2/3）。 */
    private final int rows;
    private final Level level;
    private final RecipePropertySet acceptedInputs;

    public CompressedFurnaceMenu(MenuType<CompressedFurnaceMenu> type, int id, Inventory playerInventory,
                                 Container container, ContainerData data, int tier) {
        super(type, id);
        this.tier = tier;
        this.lanes = CompressedFurnaceData.lanesForTier(tier);
        this.rows = (this.lanes + 2) / 3;
        this.container = container;
        this.data = data;
        this.level = playerInventory.player.level();
        this.acceptedInputs = this.level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT);
        checkContainerSize(container, CompressedFurnaceData.slotCount(this.lanes));
        checkContainerDataCount(data, CompressedFurnaceData.dataCount(this.lanes));
        // 菜单槽列表必须按"输入区 → 燃料区 → 产出区"分区添加（quickMoveStack 与界面都按这个区间取槽），
        // 而容器索引仍是交错布局（input=3i / fuel=3i+1 / output=3i+2），所以 Slot 构造时显式传容器索引。
        for (int lane = 0; lane < this.lanes; lane++) {
            int col = lane % 3;
            int row = lane / 3;
            this.addSlot(new InputSlot(container,
                CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_INPUT),
                this.inputX(col), this.inputY(row), this.tier));
        }
        for (int lane = 0; lane < this.lanes; lane++) {
            int col = lane % 3;
            int row = lane / 3;
            this.addSlot(new FuelSlot(container,
                CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_FUEL),
                this.fuelX(col), this.fuelY(row), this.level));
        }
        for (int lane = 0; lane < this.lanes; lane++) {
            int col = lane % 3;
            int row = lane / 3;
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

    public int rows() {
        return this.rows;
    }

    /** 面板宽度固定 176（借用原版 generic_54 的顶边/背包区贴图）。 */
    public int imageWidth() {
        return PANEL_W;
    }

    /** 物品区高度：输入 R 行 + 燃烧条 + 燃料 R 行 + 底部留白。 */
    public int bodyHeight() {
        return (TOP_Y + ROW_PITCH * this.rows * 2 + BURN_INFO_GAP + 8) - BODY_TOP;
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

    /** 左侧区块（A 输入区 / C 燃料区）宽度：3 个槽列。 */
    public int leftBlockWidth() {
        return 54;
    }

    /** 燃烧剩余时间文字的水平中心（相对面板）：A/C 区块居中。 */
    public int burnInfoCenterX() {
        return LEFT_X + 27;
    }

    public int burnInfoY() {
        return TOP_Y + ROW_PITCH * this.rows - 1;
    }

    /** 产出箭头：水平居中于 A/C 区块与 D 产出区之间的空隙。 */
    public int arrowX() {
        return (LEFT_X + this.leftBlockWidth() + OUT_X) / 2 - 5;
    }

    /** 产出箭头：垂直中心 = A 顶 到 C 底 的正中。 */
    public int arrowCenterY() {
        return TOP_Y + ROW_PITCH * this.rows + 4;
    }

    private int inputX(int col) {
        return LEFT_X + 18 * col;
    }

    private int inputY(int row) {
        return TOP_Y + ROW_PITCH * row;
    }

    private int fuelX(int col) {
        return LEFT_X + 18 * col;
    }

    private int fuelY(int row) {
        return TOP_Y + ROW_PITCH * this.rows + BURN_INFO_GAP + ROW_PITCH * row;
    }

    private int outputX(int col) {
        return OUT_X + 18 * col;
    }

    private int outputY(int row) {
        // 产出区垂直居中于燃烧条
        return TOP_Y + ROW_PITCH * this.rows + 3 - (ROW_PITCH * this.rows) / 2 + ROW_PITCH * row;
    }

    // ---------------------------------------------------------------- 进度读取

    public float cookProgress(int lane) {
        return this.data.get(CompressedFurnaceData.progressIndex(lane)) / 1000.0F;
    }

    public boolean laneActive(int lane) {
        return this.data.get(CompressedFurnaceData.activeIndex(lane)) != 0;
    }

    /** 燃烧池剩余 tick。 */
    public int litTicks() {
        return this.data.get(CompressedFurnaceData.litIndex(this.lanes));
    }

    /** 燃烧池剩余秒数（界面显示 "xx.xs"）。 */
    public float litSeconds() {
        return this.litTicks() / 20.0F;
    }

    public boolean isLit() {
        return this.litTicks() > 0;
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
