package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * 压缩潜影盒（单一等级）：243 格滚动存储，无朝向整体方块。
 * 内容随物品保留——镜像原版潜影盒：破坏（生存/爆炸/创造）时构建带内容的物品
 * （BlockEntityTag.Items，放置时原版 ItemBlock 自动写回方块实体），内容不落地。
 * 1.12.2：无开盖动画 BER（整块渲染，降级记录）。
 */
public class CompressedShulkerBlock extends Block implements ITileEntityProvider {
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(1 / 16.0, 0.0, 1 / 16.0, 15 / 16.0, 1.0, 15 / 16.0);

    public CompressedShulkerBlock() {
        super(Material.ROCK);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new ScrollingContainerBlockEntity(true);
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    /** 创造模式破坏：也掉落带内容的物品（原版潜影盒同款）。 */
    @Override
    public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (!world.isRemote && player.isCreative()) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof ScrollingContainerBlockEntity) {
                ItemStack box = boxWithContents((ScrollingContainerBlockEntity) te);
                if (!box.isEmpty()) {
                    net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(
                        world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, box);
                    drop.setDefaultPickupDelay();
                    world.spawnEntity(drop);
                    // 清空方块实体：防 breakBlock/getDrops 再掉一份
                    ((ScrollingContainerBlockEntity) te).clearContentsForCreativeDrop();
                }
            }
        }
        super.onBlockHarvested(world, pos, state, player);
    }

    /** 生存/爆炸破坏：直接构建带内容物品（不走战利品表掉自身）。 */
    @Override
    public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        NonNullList<ItemStack> drops = NonNullList.create();
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof ScrollingContainerBlockEntity) {
            ItemStack box = boxWithContents((ScrollingContainerBlockEntity) te);
            if (!box.isEmpty()) {
                drops.add(box);
            }
        }
        return drops;
    }

    /** 构建带 243 格内容的物品（BlockEntityTag → 放置时原版自动写回）。 */
    private ItemStack boxWithContents(ScrollingContainerBlockEntity be) {
        Item item = CompressedBlocks.itemByName("compressed_shulker_box");
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack box = new ItemStack(item);
        NBTTagCompound tag = new NBTTagCompound();
        net.minecraft.inventory.ItemStackHelper.saveAllItems(tag, be.allItems(), false);
        net.minecraft.nbt.NBTTagCompound blockEntityTag = new net.minecraft.nbt.NBTTagCompound();
        blockEntityTag.setTag("Items", tag.getTag("Items"));
        box.setTagInfo("BlockEntityTag", blockEntityTag);
        return box;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(dev.compressedblocks.forge.CompressedBlocksForge.ModInstanceHolder.INSTANCE,
                CompressedBlocksForge.GUI_SHULKER, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

}
