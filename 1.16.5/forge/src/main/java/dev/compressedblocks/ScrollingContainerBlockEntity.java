package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 滚动容器方块实体（压缩箱子/压缩潜影盒共用）：27 行×9 列 = 243 格。
 * 箱子破坏时洒出内容（onRemove）；潜影盒破坏时内容随物品保留（getDrops 直接构建）。
 * 1.16.5 差异：无开盖动画 BER——方块整块渲染，startOpen 只保留开关音。
 */
public class ScrollingContainerBlockEntity extends RandomizableContainerBlockEntity {
    private final boolean keepsContents;
    private NonNullList<ItemStack> items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);
    private int openCount;

    public ScrollingContainerBlockEntity(BlockEntityType<?> type, boolean keepsContents) {
        super(type);
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

    /** 创造破坏潜影盒后清空内容（防止 getDrops 再掉一份）。 */
    public void clearContentsForCreativeDrop() {
        this.items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);
        this.setChanged();
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
        return new net.minecraft.network.chat.TranslatableComponent("container.compressedblocks."
            + (this.keepsContents ? "compressed_shulker_box" : "compressed_chest"));
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new ScrollingContainerMenu(
            this.keepsContents ? CompressedBlocksForge.SHULKER_MENU_TYPE : CompressedBlocksForge.CHEST_MENU_TYPE,
            id, inv, this);
    }

    /** 箱子变体的开/关音（原版 ChestBlockEntity 同款音量音高）。 */
    @Override
    public void startOpen(net.minecraft.world.entity.player.Player player) {
        if (this.isRemoved() || (player != null && player.isSpectator())) {
            return;
        }
        if (this.openCount < 0) {
            this.openCount = 0;
        }
        boolean first = ++this.openCount == 1;
        if (first && !this.keepsContents) {
            this.level.playSound(null, this.worldPosition, SoundEvents.CHEST_OPEN,
                SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public void stopOpen(net.minecraft.world.entity.player.Player player) {
        if (this.isRemoved()) {
            return;
        }
        this.openCount = Math.max(0, this.openCount - 1);
        if (this.openCount == 0 && !this.keepsContents) {
            this.level.playSound(null, this.worldPosition, SoundEvents.CHEST_CLOSE,
                SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        super.save(tag);
        if (!this.keepsContents) {
            // 箱子：破坏即洒，不持久化物品（省 NBT）；潜影盒必须存（内容随物品走）
            ContainerHelper.saveAllItems(tag, this.items);
        }
        return tag;
    }

    @Override
    public void load(BlockState state, CompoundTag tag) {
        super.load(state, tag);
        this.items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
    }
}
