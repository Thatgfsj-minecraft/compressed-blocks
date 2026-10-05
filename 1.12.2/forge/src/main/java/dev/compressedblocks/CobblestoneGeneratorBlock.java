package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * 刷石机（3 个压缩等级）：每秒产出 1 个对应等级的原石/压缩原石，压入周围容器；
 * 无容器时暂存机器内（一组上限），满了就停滞。
 * 机器外形内缩 1px（下方容器露出可点）；右键直开下方容器。
 * 1.12.2：ITileEntityProvider + neighborChanged 重扫输出方向。
 */
public class CobblestoneGeneratorBlock extends Block implements ITileEntityProvider {
    /** 16³ 内缩 1px：不再把下方容器整个挡在点击盲区里。 */
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(1 / 16.0, 0.0, 1 / 16.0, 15 / 16.0, 1.0, 15 / 16.0);

    private final int tier;

    public CobblestoneGeneratorBlock(int tier) {
        super(Material.ROCK);
        this.tier = tier;
    }

    /** 压缩等级 1-3：产物分别为原石 / 一重压缩原石 / 二重压缩原石。 */
    public int tier() {
        return this.tier;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    /** 邻居方块更新：重扫输出方向（下/上/北/南/西/东优先级，与放置顺序无关）。 */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock,
                                BlockPos fromPos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof CobblestoneGeneratorBlockEntity) {
            ((CobblestoneGeneratorBlockEntity) te).rescanOutput();
        }
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        CobblestoneGeneratorBlockEntity te = new CobblestoneGeneratorBlockEntity();
        return te;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    /** 右键直接打开下方容器；潜行右键保留给放置类操作，无容器时放行原版行为。 */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            BlockPos below = pos.down();
            TileEntity te = world.getTileEntity(below);
            if (te instanceof ScrollingContainerBlockEntity) {
                if (!world.isRemote) {
                    player.openGui(CompressedBlocksForge.ModInstanceHolder.INSTANCE,
                        CompressedBlocksForge.GUI_CHEST, world, below.getX(), below.getY(), below.getZ());
                }
                return true;
            }
        }
        return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public CobblestoneGeneratorBlock setCreativeTab(CreativeTabs tab) {
        super.setCreativeTab(tab);
        return this;
    }
}
