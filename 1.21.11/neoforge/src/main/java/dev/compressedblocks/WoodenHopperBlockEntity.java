package dev.compressedblocks;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 木质漏斗方块实体：
 * tier 0 为基础木质漏斗，单次传输 1 个，冷却 16 tick（原版速度的一半），容量 1 格；
 * tier 1~3 分别为一至三重压缩木质漏斗，单次传输量为 8 / 32 / 288 个，冷却 8 tick（对应压缩铁漏斗 16/64/576 的一半），
 * 容量统一 3 格。
 * 容量刻意低于原版铁漏斗（5 格）：木质漏斗造价远低于铁漏斗，仅靠速度减半不足以制衡，故同时限制缓冲深度。
 * 支持红石锁死、比较器输出、上方掉落物吸入与下游推入。
 */
public class WoodenHopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper, WorldlyContainer {
    /** 基础木质漏斗容量。 */
    public static final int BASE_CONTAINER_SIZE = 1;
    /** 压缩木质漏斗（1x/2x/3x）统一容量。 */
    public static final int COMPRESSED_CONTAINER_SIZE = 3;
    private final int tier; // 0=base, 1=1x, 2=2x, 3=3x
    private final int transferRate;
    private final int baseCooldown;
    private final int size;
    private final int[] slots;
    /** 构造期即有非 null 值，避免 super() 期间的容器查询触发 NPE。 */
    private NonNullList<ItemStack> items = NonNullList.withSize(BASE_CONTAINER_SIZE, ItemStack.EMPTY);
    /** 加载期读出的老存档溢出（slot >= 当前容量），首 tick 或破坏前迁移，绝不静默丢弃。 */
    private NonNullList<ItemStack> pendingOverflow = NonNullList.create();
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
        this.size = tier == 0 ? BASE_CONTAINER_SIZE : COMPRESSED_CONTAINER_SIZE;
        this.slots = new int[this.size];
        for (int i = 0; i < this.size; i++) {
            this.slots[i] = i;
        }
        this.items = NonNullList.withSize(this.size, ItemStack.EMPTY);
    }

    /** 当前容量（1 或 3），供界面对齐与外部查询。 */
    public int getStorageSize() {
        return this.size;
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
        return this.size;
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
        MenuType<WoodenHopperMenu> type = this.tier == 0
            ? CompressedBlocks.WOODEN_HOPPER_MENU_TYPE
            : CompressedBlocks.COMPRESSED_WOODEN_HOPPER_MENU_TYPE;
        return new WoodenHopperMenu(type, containerId, playerInventory, this, this.size);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return this.slots;
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
        // 可视槽 + 尚未迁移的 pending 一起写回 Items（pending 占用 slot >= 当前容量）。
        // 这样即使区块在首 tick 之前被保存/卸载，老存档的溢出也不会丢；下次加载会重新读进 pending，
        // flush 幂等，不会重复掉落。
        ValueOutput.TypedOutputList<ItemStackWithSlot> saved =
            out.list(ContainerHelper.TAG_ITEMS, ItemStackWithSlot.CODEC);
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack stack = this.items.get(i);
            if (!stack.isEmpty()) {
                saved.add(new ItemStackWithSlot(i, stack));
            }
        }
        for (int i = 0; i < this.pendingOverflow.size(); i++) {
            ItemStack stack = this.pendingOverflow.get(i);
            if (!stack.isEmpty()) {
                saved.add(new ItemStackWithSlot(this.size + i, stack));
            }
        }
        out.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        // 老存档（9 格时代）先完整读进内存：9 格只存在于 0.3.4 开发窗口（11-14 21:20 ～ 11-15 12:57），
        // 0.3.3 及更早没有木漏斗；不能写成"0.3.4 及更早统一 9 格"。
        // BlockEntity.loadStatic 内 this.level 仍为 null，
        // 加载期绝不能访问世界或掉落物品（旧实现靠 this.level != null 判断，条件恒假 → 静默丢失）。
        // 按原始 Items 列表的 Slot 分发：slot < 容量进可视槽，slot >= 容量（含 >= 9 的异常数据）
        // 一律进 pending，空槽忽略；TreeMap 让重复 slot 后者覆盖、顺序稳定，避免手改 NBT 复制物品。
        NonNullList<ItemStack> visible = NonNullList.withSize(this.size, ItemStack.EMPTY);
        NonNullList<ItemStack> pending = NonNullList.create();
        Map<Integer, ItemStack> bySlot = new TreeMap<>();
        for (ItemStackWithSlot entry : in.listOrEmpty(ContainerHelper.TAG_ITEMS, ItemStackWithSlot.CODEC)) {
            if (entry == null || entry.stack().isEmpty()) {
                continue;
            }
            bySlot.put(entry.slot(), entry.stack());
        }
        for (Map.Entry<Integer, ItemStack> entry : bySlot.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < this.size) {
                visible.set(slot, entry.getValue());
            } else if (slot >= this.size) {
                pending.add(entry.getValue());
            }
            // 负 slot 非法，直接忽略（与 ContainerHelper 的 isValidInContainer 语义一致）
        }
        this.items = visible;
        this.pendingOverflow = pending;
        this.cooldownTime = in.getIntOr("TransferCooldown", 0);
    }

    /**
     * 迁移老存档溢出：先把 pending 按堆叠上限并入可视槽，塞不下的余量交给 sink。
     * 迁移后清空 pending 并标脏；pending 为空时直接返回，因此可重复调用（幂等）。
     * sink 与 {@link Level} 解耦，便于自检用假 sink 验证掉落内容。
     */
    void flushOverflow(Consumer<ItemStack> sink) {
        if (this.pendingOverflow.isEmpty()) {
            return;
        }
        List<ItemStack> pending = this.pendingOverflow;
        this.pendingOverflow = NonNullList.create();
        for (ItemStack overflow : pending) {
            if (overflow.isEmpty()) {
                continue;
            }
            ItemStack remainder = HopperBlockEntity.addItem(null, this, overflow, null);
            if (!remainder.isEmpty()) {
                sink.accept(remainder);
            }
        }
        this.setChanged();
    }

    /** 世界侧迁移入口：可见槽塞不下的溢出掉落在漏斗上方（与原实现落点一致）。 */
    void flushLegacyOverflow(Level level) {
        flushOverflow(stack -> Block.popResource(level, this.worldPosition.above(), stack));
    }

    /** 自检用：当前待迁移的溢出堆数。 */
    int pendingOverflowCount() {
        int count = 0;
        for (ItemStack stack : this.pendingOverflow) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static void pushItemsTick(Level level, BlockPos pos, BlockState state,
                                     WoodenHopperBlockEntity hopper) {
        // 老存档缩容迁移：首 tick 立即处理，先于冷却与红石锁判定，避免被锁住的漏斗永久丢失
        hopper.flushLegacyOverflow(level);
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
            List<ItemEntity> items = HopperBlockEntity.getItemsAtAndAbove(level, hopper);
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
