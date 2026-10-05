package dev.compressedblocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 滚动容器方块实体（压缩箱子/压缩潜影盒共用）：27 行×9 列 = 243 格。
 * 基于 1.12.2 的 TileEntityLockableLoot（原版带 loot 表的大容器基类，等价 1.16 的
 * RandomizableContainerBlockEntity）：箱子破坏时洒出内容；潜影盒破坏时内容随物品保留。
 * 1.12.2 差异：无开盖动画 BER——方块整块渲染，openInventory/closeInventory 只保留开关音。
 */
public class ScrollingContainerBlockEntity extends TileEntityLockableLoot {
    private final boolean keepsContents;
    private NonNullList<ItemStack> items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);
    private int openCount;

    public ScrollingContainerBlockEntity(boolean keepsContents) {
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

    /** 创造破坏潜影盒后清空内容（防止再掉一份）。 */
    public void clearContentsForCreativeDrop() {
        this.items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);
        this.markDirty();
    }

    // ---- IInventory（TileEntityLockableLoot 的抽象面）

    @Override
    public int getSizeInventory() {
        return this.items.size();
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        return !this.isInvalid()
            && player.getDistanceSq(this.getPos().getX() + 0.5, this.getPos().getY() + 0.5,
                this.getPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() {
        this.items.clear();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    protected void setItems(NonNullList<ItemStack> itemsIn) {
        this.items = itemsIn;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getName() {
        return this.keepsContents ? "container.compressedblocks.compressed_shulker_box"
            : "container.compressedblocks.compressed_chest";
    }

    @Override
    public Container createContainer(net.minecraft.entity.player.InventoryPlayer playerInventory,
                                     net.minecraft.entity.player.EntityPlayer player) {
        return new ScrollingContainerMenu(playerInventory, player.world, this.keepsContents, pos);
    }

    @Override
    public String getGuiID() {
        return "compressedblocks:scrolling_container";
    }

    // ---- 开关音（1.16.5 同款）

    @Override
    public void openInventory(net.minecraft.entity.player.EntityPlayer player) {
        if (this.isInvalid() || (player != null && player.isSpectator())) {
            return;
        }
        if (this.openCount < 0) {
            this.openCount = 0;
        }
        boolean first = ++this.openCount == 1;
        if (first && !this.keepsContents) {
            playChestSound("random.chestopen", 0.5F);
        }
    }

    @Override
    public void closeInventory(net.minecraft.entity.player.EntityPlayer player) {
        if (this.isInvalid()) {
            return;
        }
        this.openCount = Math.max(0, this.openCount - 1);
        if (this.openCount == 0 && !this.keepsContents) {
            playChestSound("random.chestclosed", 0.5F);
        }
    }

    /** 1.12.2 无专用 CHEST_OPEN 常量命名差异：直接走旧版随机音事件名。 */
    private void playChestSound(String sound, float volume) {
        World world = getWorld();
        if (world == null) {
            return;
        }
        BlockPos pos = getPos();
        world.playSound(null, pos, net.minecraft.util.SoundEvent.REGISTRY
            .getObject(new net.minecraft.util.ResourceLocation(sound)), SoundCategory.BLOCKS,
            volume, world.rand.nextFloat() * 0.1F + 0.9F);
    }

    // ---- NBT：箱子破坏即洒，不持久化物品（省 NBT）；潜影盒必须存（内容随物品走）

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (!this.keepsContents) {
            ItemStackHelper.saveAllItems(compound, this.items);
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.items = NonNullList.withSize(ScrollingContainerMenu.SIZE, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(compound, this.items);
    }

    /** 破坏时洒出全部格子（箱子变体；供方块 onBlockDestroyed 调用）。 */
    public void dropAll(World world, BlockPos pos) {
        for (int i = 0; i < this.items.size(); i++) {
            if (!this.items.get(i).isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), this.items.get(i));
            }
        }
        world.updateComparatorOutputLevel(pos, getBlockType());
    }
}
