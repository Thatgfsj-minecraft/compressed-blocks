package dev.compressedblocks;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

/**
 * 刷石机方块实体：内部缓冲（一组上限），每 20 tick（1 秒）产出 1 个本等级产物并
 * 尝试压入周围容器（原版 IInventory），放不下留在缓冲里。
 * 输出方向：下、上、北、南、西、东固定优先级，只喂给第一个命中的容器；
 * 方向只在首扫与邻居方块更新时重扫（不逐帧探测，防卡顿）。
 * 1.12.2：TileEntity + ITickable（无 BlockEntityTicker/Type 注册体系）。
 */
public class CobblestoneGeneratorBlockEntity extends TileEntity implements ITickable {
    /** 产出周期：20 tick = 1 秒。 */
    public static final int CYCLE_TICKS = 20;
    /** 六向输出优先级。 */
    static final EnumFacing[] SCAN_ORDER = {
        EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    private int tier = 1;
    private ItemStack buffer = ItemStack.EMPTY;
    private int progress;
    /** 缓存的输出方向；null = 周围无容器。 */
    private EnumFacing outDir;
    /** 是否已扫过（无容器也不重复扫，邻居更新时重来）。 */
    private boolean scanned;

    /** 1.12.2 ticker：仅服务端推进（客户端无动画）。 */
    @Override
    public void update() {
        if (this.world != null && !this.world.isRemote) {
            if (++this.progress >= CYCLE_TICKS) {
                this.progress = 0;
                this.produce();
            }
            this.pushOutput();
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
        // 1.12.2 无原石物品（原石是方块，1.13+ 才有独立 Item）：从方块取
        return new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.COBBLESTONE));
    }

    private static ItemStack itemOrCobble(net.minecraft.item.Item item) {
        return item == null ? new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.COBBLESTONE)) : new ItemStack(item);
    }

    /** 邻居方块更新时重扫：六向按优先级取第一个容器（与放置顺序无关，下方始终优先上方）。 */
    public void rescanOutput() {
        for (EnumFacing d : SCAN_ORDER) {
            if (isOutputTarget(getPos().offset(d))) {
                this.outDir = d;
                this.scanned = true;
                return;
            }
        }
        this.outDir = null;
        this.scanned = true;
    }

    /** 目标格是否可接收产物：原版 IInventory。 */
    private boolean isOutputTarget(BlockPos target) {
        return getWorld() != null && getWorld().getTileEntity(target) instanceof IInventory;
    }

    private void produce() {
        ItemStack product = this.product();
        if (this.buffer.isEmpty()) {
            this.buffer = product;
            this.markDirty();
        } else if (this.buffer.getItem() == product.getItem()
            && ItemStack.areItemStackTagsEqual(this.buffer, product)
            && this.buffer.getCount() < Math.min(this.buffer.getMaxStackSize(), 64)) {
            this.buffer.grow(1);
            this.markDirty();
        }
    }

    /** 把缓冲压入缓存方向上的容器，放不下留在机器内。 */
    private void pushOutput() {
        if (this.buffer.isEmpty() || getWorld() == null || getWorld().isRemote) {
            return;
        }
        if (!this.scanned) {
            this.rescanOutput(); // 放置/加载后首扫
        }
        if (this.outDir == null) {
            return;
        }
        TileEntity te = getWorld().getTileEntity(getPos().offset(this.outDir));
        if (!(te instanceof IInventory)) {
            this.outDir = null; // 方向失效：等下一次邻居更新再扫
            this.scanned = false;
            return;
        }
        IInventory container = (IInventory) te;
        int size = container.getSizeInventory();
        for (int i = 0; i < size && !this.buffer.isEmpty(); i++) {
            ItemStack slot = container.getStackInSlot(i);
            if (slot.isEmpty() || slot.getItem() != this.buffer.getItem()
                || !ItemStack.areItemStackTagsEqual(this.buffer, slot)) {
                continue;
            }
            int cap = Math.max(Math.min(slot.getMaxStackSize(), container.getInventoryStackLimit()), 64);
            int room = cap - slot.getCount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, this.buffer.getCount());
            slot.grow(move);
            this.buffer.shrink(move);
        }
        for (int i = 0; i < size && !this.buffer.isEmpty(); i++) {
            if (container.getStackInSlot(i).isEmpty() && container.isItemValidForSlot(i, this.buffer)) {
                container.setInventorySlotContents(i, this.buffer.copy());
                this.buffer.setCount(0);
            }
        }
        if (size > 0) {
            container.markDirty();
        }
        if (!this.buffer.isEmpty()) {
            this.markDirty();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!this.buffer.isEmpty()) {
            NBTTagCompound bufferTag = new NBTTagCompound();
            this.buffer.writeToNBT(bufferTag);
            tag.setTag("Buffer", bufferTag);
        }
        tag.setInteger("Progress", this.progress);
        tag.setInteger("Tier", this.tier);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.buffer = tag.hasKey("Buffer")
            ? new ItemStack(tag.getCompoundTag("Buffer")) : ItemStack.EMPTY;
        this.progress = Math.max(0, tag.getInteger("Progress"));
        this.tier = Math.max(1, tag.getInteger("Tier"));
        this.scanned = false; // 加载后首 tick 重扫一次
    }

    /** 产出等级：从方块状态动态取（避免 setBlockState 时机问题）。 */
    private int tierOfState() {
        if (getWorld() != null) {
            net.minecraft.block.state.IBlockState state = getWorld().getBlockState(getPos());
            if (state.getBlock() instanceof CobblestoneGeneratorBlock) {
                return ((CobblestoneGeneratorBlock) state.getBlock()).tier();
            }
        }
        return this.tier;
    }
}
