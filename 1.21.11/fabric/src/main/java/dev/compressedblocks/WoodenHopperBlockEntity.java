package dev.compressedblocks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 木质漏斗方块实体：
 * tier 0 为基础木质漏斗，单次传输 1 个，冷却 16 tick（原版速度的一半）；
 * tier 1~3 分别为一至三重压缩木质漏斗，单次传输量为 8 / 32 / 288 个，冷却 8 tick（对应压缩铁漏斗 16/64/576 的一半）；
 * 统一 9 格槽位（3x3 布局），支持红石锁死、比较器输出、上方掉落物吸入与下游推入。
 */
public class WoodenHopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper, WorldlyContainer {
    public static final int CONTAINER_SIZE = 9;
    private static final int[] SLOTS = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final int tier; // 0=base, 1=1x, 2=2x, 3=3x
    private final int transferRate;
    private final int baseCooldown;
    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int cooldownTime = 0;
    private long tickedGameTime = 0;

    public WoodenHopperBlockEntity(BlockPos pos, BlockState state, int tier) {
        super(CompressedBlocks.WOODEN_HOPPER_TYPE[tier], pos, state);
        this.tier = tier;
        this.transferRate = switch (tier) {
            case 0 -> 1;
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 288;
            default -> 1;
        };
        this.baseCooldown = tier == 0 ? 16 : 8;
    }

    public int getTier() {
        return this.tier;
    }

    public int getTransferRate() {
        return this.transferRate;
    }

    public int getBaseCooldown() {
        return this.baseCooldown;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        if (this.tier == 0) {
            return Component.translatable("container.compressedblocks.wooden_hopper");
        }
        return Component.translatable("container.compressedblocks."
            + CompressedBlocks.LEVEL_PREFIX[this.tier - 1] + "_compressed_wooden_hopper");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new DispenserMenu(containerId, playerInventory, this);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return true;
    }

    @Override
    public double getLevelX() {
        return (double) this.worldPosition.getX() + 0.5D;
    }

    @Override
    public double getLevelY() {
        return (double) this.worldPosition.getY() + 0.5D;
    }

    @Override
    public double getLevelZ() {
        return (double) this.worldPosition.getZ() + 0.5D;
    }

    @Override
    public boolean isGridAligned() {
        return true;
    }

    public void setCooldown(int cooldown) {
        this.cooldownTime = cooldown;
    }

    public boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    public boolean isFull() {
        for (ItemStack stack : this.items) {
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, this.items);
        out.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, this.items);
        this.cooldownTime = in.getIntOr("TransferCooldown", 0);
    }

    public static void pushItemsTick(Level level, BlockPos pos, BlockState state,
                                     WoodenHopperBlockEntity hopper) {
        hopper.cooldownTime--;
        hopper.tickedGameTime = level.getGameTime();
        if (hopper.isOnCooldown()) {
            return;
        }
        hopper.setCooldown(0);
        if (!state.getValue(HopperBlock.ENABLED)) {
            return;
        }
        boolean moved = false;
        if (!hopper.isEmpty()) {
            moved = ejectItems(level, pos, state, hopper);
        }
        if (!hopper.isFull()) {
            moved |= suckInItems(level, hopper);
        }
        if (moved) {
            hopper.setCooldown(hopper.getBaseCooldown());
            setChanged(level, pos, state);
        }
    }

    private static boolean ejectItems(Level level, BlockPos pos, BlockState state,
                                      WoodenHopperBlockEntity hopper) {
        Direction facing = state.getValue(HopperBlock.FACING);
        BlockPos targetPos = pos.relative(facing);
        Container target = HopperBlockEntity.getContainerAt(level, targetPos);
        int budget = hopper.transferRate;
        boolean anyMoved = false;

        if (target != null) {
            for (int i = 0; i < hopper.getContainerSize() && budget > 0; i++) {
                ItemStack slotStack = hopper.getItem(i);
                if (slotStack.isEmpty()) {
                    continue;
                }
                int toMove = Math.min(slotStack.getCount(), budget);
                ItemStack moving = slotStack.copyWithCount(toMove);
                ItemStack remainder = HopperBlockEntity.addItem(hopper, target, moving, facing.getOpposite());
                int actuallyMoved = toMove - remainder.getCount();
                if (actuallyMoved > 0) {
                    slotStack.shrink(actuallyMoved);
                    budget -= actuallyMoved;
                    anyMoved = true;
                    if (slotStack.isEmpty()) {
                        hopper.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        } else if (CompressedBlocks.ITEM_SINK != null && CompressedBlocks.ITEM_SINK.accepts(level, targetPos, facing.getOpposite())) {
            for (int i = 0; i < hopper.getContainerSize() && budget > 0; i++) {
                ItemStack slotStack = hopper.getItem(i);
                if (slotStack.isEmpty()) {
                    continue;
                }
                int toMove = Math.min(slotStack.getCount(), budget);
                ItemStack moving = slotStack.copyWithCount(toMove);
                long inserted = CompressedBlocks.ITEM_SINK.insert(level, targetPos, facing.getOpposite(), moving);
                if (inserted > 0) {
                    slotStack.shrink((int) inserted);
                    budget -= (int) inserted;
                    anyMoved = true;
                    if (slotStack.isEmpty()) {
                        hopper.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
        return anyMoved;
    }

    private static boolean suckInItems(Level level, WoodenHopperBlockEntity hopper) {
        BlockPos abovePos = hopper.getBlockPos().above();
        Container source = HopperBlockEntity.getContainerAt(level, abovePos);
        int budget = hopper.transferRate;
        boolean anyMoved = false;

        if (source != null) {
            if (source instanceof WorldlyContainer worldly) {
                int[] slots = worldly.getSlotsForFace(Direction.DOWN);
                for (int slot : slots) {
                    if (budget <= 0) {
                        break;
                    }
                    ItemStack sourceStack = worldly.getItem(slot);
                    if (sourceStack.isEmpty()) {
                        continue;
                    }
                    if (!worldly.canTakeItemThroughFace(slot, sourceStack, Direction.DOWN)) {
                        continue;
                    }
                    int toMove = Math.min(sourceStack.getCount(), budget);
                    ItemStack moving = sourceStack.copyWithCount(toMove);
                    ItemStack remainder = HopperBlockEntity.addItem(source, hopper, moving, null);
                    int actuallyMoved = toMove - remainder.getCount();
                    if (actuallyMoved > 0) {
                        sourceStack.shrink(actuallyMoved);
                        budget -= actuallyMoved;
                        anyMoved = true;
                        if (sourceStack.isEmpty()) {
                            worldly.setItem(slot, ItemStack.EMPTY);
                        }
                        worldly.setChanged();
                    }
                }
            } else {
                int size = source.getContainerSize();
                for (int slot = 0; slot < size && budget > 0; slot++) {
                    ItemStack sourceStack = source.getItem(slot);
                    if (sourceStack.isEmpty()) {
                        continue;
                    }
                    int toMove = Math.min(sourceStack.getCount(), budget);
                    ItemStack moving = sourceStack.copyWithCount(toMove);
                    ItemStack remainder = HopperBlockEntity.addItem(source, hopper, moving, null);
                    int actuallyMoved = toMove - remainder.getCount();
                    if (actuallyMoved > 0) {
                        sourceStack.shrink(actuallyMoved);
                        budget -= actuallyMoved;
                        anyMoved = true;
                        if (sourceStack.isEmpty()) {
                            source.setItem(slot, ItemStack.EMPTY);
                        }
                        source.setChanged();
                    }
                }
            }
        }
        if (budget > 0) {
            List<ItemEntity> items = level.getEntitiesOfClass(
                ItemEntity.class,
                hopper.getSuckAabb(),
                EntitySelector.ENTITY_STILL_ALIVE
            );
            for (ItemEntity itemEntity : items) {
                if (budget <= 0) {
                    break;
                }
                ItemStack entityStack = itemEntity.getItem();
                if (entityStack.isEmpty()) {
                    continue;
                }
                int toMove = Math.min(entityStack.getCount(), budget);
                ItemStack moving = entityStack.copyWithCount(toMove);
                ItemStack remainder = HopperBlockEntity.addItem(null, hopper, moving, null);
                int actuallyMoved = toMove - remainder.getCount();
                if (actuallyMoved > 0) {
                    entityStack.shrink(actuallyMoved);
                    if (entityStack.isEmpty()) {
                        itemEntity.discard();
                    }
                    budget -= actuallyMoved;
                    anyMoved = true;
                }
            }
        }
        return anyMoved;
    }
}
