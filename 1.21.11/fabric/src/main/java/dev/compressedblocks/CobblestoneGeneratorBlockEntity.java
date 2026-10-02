package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 刷石机方块实体：内部缓冲（一组上限），每 20 tick（1 秒）产出 1 个本等级产物并
 * 尝试压入周围容器（箱子/木桶/漏斗都实现 Container），放不下留在缓冲里。
 * 输出方向：下、上、北、南、西、东固定优先级，只喂给第一个命中的容器；
 * 方向只在放置/加载后的首扫与邻居方块更新时重扫（不逐帧探测，防卡顿）。
 * 产物随压缩等级：1 级=原石，2 级=一重压缩原石，3 级=二重压缩原石。
 */
public class CobblestoneGeneratorBlockEntity extends BlockEntity {
    /** 产出周期：20 tick = 1 秒。 */
    public static final int CYCLE_TICKS = 20;
    /** 六向输出优先级。 */
    static final Direction[] SCAN_ORDER = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private final int tier;
    private ItemStack buffer = ItemStack.EMPTY;
    private int progress;
    /** 缓存的输出方向；null = 周围无容器。 */
    private Direction outDir;
    /** 是否已扫过（无容器也不重复扫，邻居更新时重来）。 */
    private boolean scanned;

    public CobblestoneGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(CompressedBlocks.GENERATOR_TYPE, pos, state);
        this.tier = state.getBlock() instanceof CobblestoneGeneratorBlock gen ? gen.tier() : 1;
    }

    private ItemStack product() {
        return switch (this.tier) {
            case 3 -> itemOrCobble(CompressedBlocks.itemByName("2x_cobblestone"));
            case 2 -> itemOrCobble(CompressedBlocks.itemByName("1x_cobblestone"));
            default -> new ItemStack(Items.COBBLESTONE);
        };
    }

    private static ItemStack itemOrCobble(Item item) {
        return item == null ? new ItemStack(Items.COBBLESTONE) : new ItemStack(item);
    }

    /** 邻居方块更新时重扫：六向按优先级取第一个容器。 */
    public void rescanOutput() {
        for (Direction d : SCAN_ORDER) {
            if (getLevel() != null && getLevel().getBlockEntity(getBlockPos().relative(d)) instanceof Container) {
                this.outDir = d;
                this.scanned = true;
                return;
            }
        }
        this.outDir = null;
        this.scanned = true;
    }

    /** 服务端：每秒产出 1 个并尽量压入输出方向的容器。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  CobblestoneGeneratorBlockEntity be) {
        if (++be.progress >= CYCLE_TICKS) {
            be.progress = 0;
            be.produce();
        }
        be.pushOutput();
    }

    private void produce() {
        ItemStack product = product();
        if (this.buffer.isEmpty()) {
            this.buffer = product;
            setChanged();
        } else if (ItemStack.isSameItemSameComponents(this.buffer, product)
            && this.buffer.getCount() < Math.min(this.buffer.getMaxStackSize(), 64)) {
            this.buffer.grow(1);
            setChanged();
        }
    }

    /** 把缓冲压入缓存方向上的容器，放不下留在机器内。 */
    private void pushOutput() {
        if (this.buffer.isEmpty() || !(getLevel() instanceof ServerLevel server)) {
            return;
        }
        if (!this.scanned) {
            rescanOutput(); // 放置/加载后首扫
        }
        if (this.outDir == null) {
            return;
        }
        if (!(getLevel().getBlockEntity(getBlockPos().relative(this.outDir)) instanceof Container container)) {
            this.outDir = null; // 方向失效：等下一次邻居更新再扫
            this.scanned = false;
            return;
        }
        int size = container.getContainerSize();
        for (int i = 0; i < size && !this.buffer.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(this.buffer, slot)) {
                continue;
            }
            int room = Math.min(slot.getMaxStackSize(), container.getMaxStackSize()) - slot.getCount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, this.buffer.getCount());
            slot.grow(move);
            this.buffer.shrink(move);
        }
        for (int i = 0; i < size && !this.buffer.isEmpty(); i++) {
            if (container.getItem(i).isEmpty() && container.canPlaceItem(i, this.buffer)) {
                container.setItem(i, this.buffer.split(this.buffer.getCount()));
            }
        }
        if (size > 0) {
            container.setChanged();
        }
        if (!this.buffer.isEmpty()) {
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!this.buffer.isEmpty()) {
            out.store("Buffer", ItemStack.CODEC, this.buffer);
        }
        out.putInt("Progress", this.progress);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.buffer = in.read("Buffer", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.progress = Math.max(0, in.getIntOr("Progress", 0));
        this.scanned = false; // 加载后首 tick 重扫一次
    }
}
