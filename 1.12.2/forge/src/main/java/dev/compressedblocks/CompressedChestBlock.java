package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 压缩箱子（单一等级）：243 格滚动存储。不能拼成大箱子（右键各开各的菜单）；
 * 有朝向（贴脸放置，正面带锁扣）；破坏时内容洒出（breakBlock，原版 ChestBlock 同款）。
 * 1.12.2 差异：无开盖动画 BER（方块模型整块渲染，降级记录）；openGui 走 IGuiHandler。
 */
public class CompressedChestBlock extends Block implements ITileEntityProvider {
    public static final PropertyDirection FACING = BlockDirectional.FACING;
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(1 / 16.0, 0.0, 1 / 16.0, 15 / 16.0, 1.0, 15 / 16.0);

    private final boolean keepsContents;

    public CompressedChestBlock(boolean keepsContents) {
        // 子类（潜影盒）复用：无状态/有状态的差异由 keepsContents 区分
        super(Material.WOOD);
        this.keepsContents = keepsContents;
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    public boolean keepsContents() {
        return this.keepsContents;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, net.minecraft.world.IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    /** 贴脸放置：面向玩家（1.12.2 放置态钩子签名）。 */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public IBlockState withRotation(IBlockState state, net.minecraft.util.Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, net.minecraft.util.Mirror mirrorIn) {
        return state.withMirror(mirrorIn);
    }

    // ---- 1.12.2 元数据映射：facing 的 2bit

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {FACING});
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        // 1.12.2 stable_39：由索引取朝向（0-5），纵向朝向回落北向
        EnumFacing facing = EnumFacing.byIndex(meta & 7);
        return facing.getAxis() == EnumFacing.Axis.Y
            ? getDefaultState().withProperty(FACING, EnumFacing.NORTH)
            : getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new ScrollingContainerBlockEntity(this.keepsContents);
    }

    /** 整块模型渲染（方块模型由 JSON 决定，禁用原版物品态渲染类型）。 */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    /** 破坏时洒出全部 243 格（箱子变体；潜影盒在 getDrops 里随物品走）。 */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof ScrollingContainerBlockEntity && !((ScrollingContainerBlockEntity) te).keepsContents()) {
            ((ScrollingContainerBlockEntity) te).dropAll(world, pos);
        }
        super.breakBlock(world, pos, state);
    }

    /** 右键打开滚动容器：1.12.2 走 player.openGui（IGuiHandler 按 id 分发）。 */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            int id = this.keepsContents ? CompressedBlocksForge.GUI_SHULKER : CompressedBlocksForge.GUI_CHEST;
            player.openGui(dev.compressedblocks.forge.CompressedBlocksForge.ModInstanceHolder.INSTANCE, id, world,
                pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

}
