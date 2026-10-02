package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 滚动容器方块实体（压缩箱子/压缩潜影盒共用）：27 行×9 列 = 243 格。
 * 箱子破坏时洒出内容；潜影盒破坏时内容随物品保留（copy_components 战利品表，
 * BaseContainerBlockEntity 自带 DataComponents.CONTAINER 组件映射）。
 */
public class ScrollingContainerBlockEntity extends BaseContainerBlockEntity {
    private final boolean keepsContents;
    private NonNullList<ItemStack> items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);

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
        super.preRemoveSideEffects(pos, state);
        if (!this.keepsContents && this.level != null) {
            Containers.dropContents(this.level, pos, this);
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
