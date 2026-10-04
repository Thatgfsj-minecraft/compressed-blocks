package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 刷石机方块实体：内部缓冲（一组上限），每 20 tick（1 秒）产出 1 个本等级产物并
 * 尝试压入周围容器（原版 Container），放不下留在缓冲里。
 * 输出方向：下、上、北、南、西、东固定优先级，只喂给第一个命中的容器；
 * 方向只在首扫与邻居方块更新时重扫（不逐帧探测，防卡顿）。
 * 1.16.5 差异：无 IItemHandler 大容器钩子（ITEM_SINK 接口保留但 1.16.5 不注入）。
 */
public class CobblestoneGeneratorBlockEntity extends BlockEntity implements net.minecraft.world.level.block.entity.TickableBlockEntity {
    /** 产出周期：20 tick = 1 秒。 */
    public static final int CYCLE_TICKS = 20;
    /** 六向输出优先级。 */
    static final Direction[] SCAN_ORDER = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private int tier = 1;
    private ItemStack buffer = ItemStack.EMPTY;
    private int progress;
    /** 缓存的输出方向；null = 周围无容器。 */
    private Direction outDir;
    /** 是否已扫过（无容器也不重复扫，邻居更新时重来）。 */
    private boolean scanned;

    public CobblestoneGeneratorBlockEntity() {
        super(CompressedBlocksForge.GENERATOR_TYPE);
    }

    /** 1.16.5 ticker：仅服务端推进（客户端无动画）。 */
    @Override
    public void tick() {
        if (this.level != null && !this.level.isClientSide) {
            serverTick(this.level, this.getBlockPos(), this.getBlockState(), this);
        }
    }

    private ItemStack product() {
        int t = tierOfState();
        if (t == 3) {
            return itemOrCobble(CompressedBlocks.itemByName("2x_cobblestone"));
        }
        if (t == 2) {
            return itemOrCobble(CompressedBlocks.itemByName("1x_cobblestone"));
        }
        return new ItemStack(Items.COBBLESTONE);
    }

    private static ItemStack itemOrCobble(net.minecraft.world.item.Item item) {
        return item == null ? new ItemStack(Items.COBBLESTONE) : new ItemStack(item);
    }

    /** 邻居方块更新时重扫：六向按优先级取第一个容器（与放置顺序无关，下方始终优先上方）。 */
    public void rescanOutput() {
        for (Direction d : SCAN_ORDER) {
            if (isOutputTarget(getBlockPos().relative(d))) {
                this.outDir = d;
                this.scanned = true;
                return;
            }
        }
        this.outDir = null;
        this.scanned = true;
    }

    /** 目标格是否可接收产物：原版 Container。 */
    private boolean isOutputTarget(BlockPos target) {
        return getLevel() != null && getLevel().getBlockEntity(target) instanceof net.minecraft.world.Container;
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
        ItemStack product = this.product();
        if (this.buffer.isEmpty()) {
            this.buffer = product;
            this.setChanged();
        } else if (this.buffer.getItem() == product.getItem()
            && net.minecraft.world.item.ItemStack.tagMatches(this.buffer, product)
            && this.buffer.getCount() < Math.min(this.buffer.getMaxStackSize(), 64)) {
            this.buffer.grow(1);
            this.setChanged();
        }
    }

    /** 把缓冲压入缓存方向上的容器，放不下留在机器内。 */
    private void pushOutput() {
        if (this.buffer.isEmpty() || !(getLevel() instanceof ServerLevel)) {
            return;
        }
        ServerLevel server = (ServerLevel) getLevel();
        if (!this.scanned) {
            this.rescanOutput(); // 放置/加载后首扫
        }
        if (this.outDir == null) {
            return;
        }
        net.minecraft.world.Container container =
            getLevel().getBlockEntity(getBlockPos().relative(this.outDir)) instanceof net.minecraft.world.Container
                ? (net.minecraft.world.Container) getLevel().getBlockEntity(getBlockPos().relative(this.outDir))
                : null;
        if (container == null) {
            this.outDir = null; // 方向失效：等下一次邻居更新再扫
            this.scanned = false;
            return;
        }
        int size = container.getContainerSize();
        for (int i = 0; i < size && !this.buffer.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty() || slot.getItem() != this.buffer.getItem()
                || !net.minecraft.world.item.ItemStack.tagMatches(this.buffer, slot)) {
                continue;
            }
            int cap = Math.max(Math.min(slot.getMaxStackSize(), container.getMaxStackSize()), 64);
            int room = cap - slot.getCount();
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
            this.setChanged();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        super.save(tag);
        if (!this.buffer.isEmpty()) {
            CompoundTag bufferTag = new CompoundTag();
            this.buffer.save(bufferTag);
            tag.put("Buffer", bufferTag);
        }
        tag.putInt("Progress", this.progress);
        tag.putInt("Tier", this.tier);
        return tag;
    }

    @Override
    public void load(BlockState state, CompoundTag tag) {
        super.load(state, tag);
        this.buffer = tag.contains("Buffer") ? ItemStack.of(tag.getCompound("Buffer")) : ItemStack.EMPTY;
        this.progress = Math.max(0, tag.getInt("Progress"));
        this.tier = Math.max(1, tag.getInt("Tier"));
        this.scanned = false; // 加载后首 tick 重扫一次
    }

    /** 产出等级：从方块状态动态取（1.16.5 无可靠的 setBlockState 覆写时机）。 */
    private int tierOfState() {
        BlockState state = this.getBlockState();
        return state.getBlock() instanceof CobblestoneGeneratorBlock
            ? ((CobblestoneGeneratorBlock) state.getBlock()).tier() : this.tier;
    }

    @SuppressWarnings("unused")
    private static void dropBuffer(Level level, BlockPos pos, ItemStack stack) {
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
    }
}
