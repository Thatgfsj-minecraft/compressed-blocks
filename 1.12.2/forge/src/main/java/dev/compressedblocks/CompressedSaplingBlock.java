package dev.compressedblocks;

import net.minecraft.block.BlockBush;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * 压缩树苗（6 木 × 9 重）：只能种在等级足够的压缩泥土上；
 * 1.12.2 无 configured feature——随机刻直接跑代码 WorldGenerator（CompressedTreeGen）。
 * 不继承原版 BlockSapling：其 TYPE 枚举属性与 9 级压缩树无法对齐（降级记录：无骨粉加速）。
 */
public class CompressedSaplingBlock extends BlockBush implements IGrowable {
    private final int level;
    private final String wood;

    public CompressedSaplingBlock(int level, String wood) {
        setTickRandomly(true);
        this.level = level;
        this.wood = wood;
    }

    public int level() {
        return this.level;
    }

    /** 只能种在等级足够的压缩泥土上（BlockBush 的种植判定点）。 */
    @Override
    public boolean canSustainBush(IBlockState state) {
        Integer dirtLevel = CompressedBlocks.dirtLevel(state);
        return dirtLevel != null && dirtLevel >= this.level;
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        super.updateTick(world, pos, state, rand);
        if (!world.isRemote) {
            if (world.getLightFromNeighbors(pos.up()) >= 9 && rand.nextInt(7) == 0) {
                grow(world, pos, rand);
            }
        }
    }

    /** 长出同重数压缩树（干 + 球形树冠，见 CompressedTreeGen）。 */
    public void grow(World world, BlockPos pos, Random rand) {
        CompressedTreeGen gen = CompressedTreeGen.of(wood, level);
        if (gen != null) {
            world.setBlockToAir(pos);
            if (!gen.generate(world, rand, pos)) {
                world.setBlockState(pos, getDefaultState(), 3);
            }
        }
    }

    // ---- IGrowable：仅支持自身长树（无骨粉分级加速——降级记录）

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return true;
    }

    @Override
    public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
        grow(world, pos, rand);
    }
}
