package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 滚动容器方块实体（压缩箱子/压缩潜影盒共用）：27 行×9 列 = 243 格。
 * 箱子破坏时洒出内容；潜影盒破坏时内容随物品保留（getDrops 直接构建）。
 * 两种变体都带原版同款开盖动画：openCount 经 blockEvent 同步到客户端，
 * 客户端 ticker 以 10 tick 开/关推进 lidProgress；箱子另有原版开/关音。
 */
public class ScrollingContainerBlockEntity extends BaseContainerBlockEntity {
    /** blockEvent 类型：同步 openCount（原版 ShulkerBoxBlockEntity.EVENT_SET_OPEN_COUNT 同款）。 */
    private static final int EVENT_SET_OPEN_COUNT = 1;

    /** 开盖状态机。 */
    private enum LidStatus { CLOSED, OPENING, OPENED, CLOSING }

    private final boolean keepsContents;
    private NonNullList<ItemStack> items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);

    private int openCount;
    private float lidProgress;
    private float lidProgressOld;
    private LidStatus lidStatus = LidStatus.CLOSED;

    public ScrollingContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                         boolean keepsContents) {
        super(type, pos, state);
        this.keepsContents = keepsContents;
    }

    /** 潜影盒变体：破坏时内容保留在物品里；箱子变体：破坏时洒出。 */
    public boolean keepsContents() {
        return this.keepsContents;
    }

    /** 供掉落构建读取全部 243 格（潜影盒内容随物品保留）。 */
    public NonNullList<ItemStack> allItems() {
        return this.items;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
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
        return Component.translatable("block.compressedblocks."
            + (this.keepsContents ? "compressed_shulker_box" : "compressed_chest"));
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new ScrollingContainerMenu(
            this.keepsContents ? CompressedBlocks.SHULKER_MENU_TYPE : CompressedBlocks.CHEST_MENU_TYPE,
            id, inv, this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // 1.21.11 原版默认实现会对任何 Container 无条件洒内容（并清空槽位）；
        // 潜影盒变体不能调 super——内容已由 getDrops 随物品保留（镜像原版 ShulkerBoxBlockEntity 空覆写）。
        // 箱子变体：super 本身完成洒落，无需手动 dropContents。
        if (!this.keepsContents) {
            super.preRemoveSideEffects(pos, state);
        }
    }

    // ---------------------------------------------------------- 开盖动画（箱子/潜影盒共用）

    /** 插值后的开盖进度（0=合 1=开），BER 每帧读取。 */
    public float getLidProgress(float partialTick) {
        return Mth.lerp(partialTick, this.lidProgressOld, this.lidProgress);
    }

    /** 客户端 ticker：推进开/关动画（原版 10 tick 开满）。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  ScrollingContainerBlockEntity be) {
        be.updateLidAnimation();
    }

    private void updateLidAnimation() {
        this.lidProgressOld = this.lidProgress;
        switch (this.lidStatus) {
            case OPENING -> {
                this.lidProgress += 0.1F;
                if (this.lidProgress >= 1.0F) {
                    this.lidProgress = 1.0F;
                    this.lidStatus = LidStatus.OPENED;
                }
            }
            case CLOSING -> {
                this.lidProgress -= 0.1F;
                if (this.lidProgress <= 0.0F) {
                    this.lidProgress = 0.0F;
                    this.lidStatus = LidStatus.CLOSED;
                }
            }
            case OPENED -> this.lidProgress = 1.0F;
            default -> this.lidProgress = 0.0F;
        }
    }

    @Override
    public boolean triggerEvent(int type, int data) {
        if (type == EVENT_SET_OPEN_COUNT) {
            this.openCount = data;
            this.lidStatus = data == 0 ? LidStatus.CLOSING : LidStatus.OPENING;
            return true;
        }
        return super.triggerEvent(type, data);
    }

    /** 箱子变体的开/关音（原版 ChestBlockEntity 同款音量音高；潜影盒维持原状）。 */
    private void playChestSound(boolean open) {
        if (this.keepsContents || !(getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        level.playSound(null, this.worldPosition,
            open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE,
            SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (this.isRemoved()) {
            return;
        }
        if (user.getLivingEntity() != null && user.getLivingEntity().isSpectator()) {
            return;
        }
        if (this.openCount < 0) {
            this.openCount = 0;
        }
        this.openCount++;
        boolean first = this.openCount == 1;
        if (this.level != null) {
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(),
                EVENT_SET_OPEN_COUNT, this.openCount);
        }
        if (first) {
            playChestSound(true);
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (this.isRemoved()) {
            return;
        }
        this.openCount--;
        if (this.level != null) {
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(),
                EVENT_SET_OPEN_COUNT, this.openCount);
        }
        if (this.openCount == 0) {
            playChestSound(false);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, this.items);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, this.items);
    }
}
