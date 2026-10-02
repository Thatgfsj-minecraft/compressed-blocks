package dev.compressedblocks;

import net.minecraft.core.BlockPos;
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
 * 尝试压入下方容器（箱子/木桶/漏斗都实现 Container），放不下留在缓冲里。
 * 产物随压缩等级：1 级=原石，2 级=一重压缩原石，3 级=二重压缩原石。
 */
public class CobblestoneGeneratorBlockEntity extends BlockEntity {
    /** 产出周期：20 tick = 1 秒。 */
    public static final int CYCLE_TICKS = 20;

    private final int tier;
    private ItemStack buffer = ItemStack.EMPTY;
    private int progress;

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

    /** 服务端：每秒产出 1 个并尽量压入下方容器。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  CobblestoneGeneratorBlockEntity be) {
        if (++be.progress >= CYCLE_TICKS) {
            be.progress = 0;
            be.produce();
        }
        be.pushToBelow();
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

    /** 把缓冲压入下方容器，放不下留在机器内。 */
    private void pushToBelow() {
        if (this.buffer.isEmpty()
            || !(getLevel() instanceof ServerLevel server)
            || !(getLevel().getBlockEntity(getBlockPos().below()) instanceof Container container)) {
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
    }
}
