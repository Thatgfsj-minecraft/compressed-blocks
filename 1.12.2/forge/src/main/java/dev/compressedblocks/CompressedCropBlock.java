package dev.compressedblocks;

import net.minecraft.block.BlockCrops;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * 压缩作物（4 种 × 3 级封顶）：只能种在等级足够的压缩耕地上，生长速度公式与原版一致。
 * 1.12.2 的 BlockCrops 为抽象类（getCrop/getSeed 必须实现），AGE 属性 0-7 与 1.16 相同。
 */
public class CompressedCropBlock extends BlockCrops {
    private final int level;
    /** 种子物品在方块之后构建（1.12.2 同 1.16.5，先建方块后挂引用）。 */
    private Item seedItem;

    public CompressedCropBlock(int level) {
        this.level = level;
    }

    /** 后挂种子物品（掉落与盆栽收割用）。 */
    public void setSeedItem(Item seedItem) {
        this.seedItem = seedItem;
    }

    public int level() {
        return this.level;
    }

    /** 只能种在等级足够的压缩耕地上。 */
    @Override
    protected boolean canSustainBush(IBlockState state) {
        return state.getBlock() instanceof CompressedFarmBlock
            && ((CompressedFarmBlock) state.getBlock()).level() >= this.level;
    }

    @Override
    public Item getSeed() {
        return this.seedItem != null ? this.seedItem : Item.getItemFromBlock(this);
    }

    @Override
    public Item getCrop() {
        return this.seedItem != null ? this.seedItem : Item.getItemFromBlock(this);
    }

    /** 原版 randomTick 镜像（耕地判定换成压缩耕地），速度公式见 growthSpeed。 */
    @Override
    public void randomTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.getLightFromNeighbors(pos.up()) >= 9) {
            int age = this.getAge(state);
            if (age < this.getMaxAge()
                && random.nextInt((int) (25.0F / this.growthSpeed(state, world, pos)) + 1) == 0) {
                world.setBlockState(pos, this.withAge(age + 1), 2);
            }
        }
    }

    /** 原版 getGrowthSpeed 公式，耕地判定换成压缩耕地（任一等级）。 */
    private float growthSpeed(IBlockState state, IBlockAccess level, BlockPos pos) {
        float f = 1.0F;
        BlockPos below = pos.down();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float f1 = 0.0F;
                if (level.getBlockState(below.add(i, 0, j)).getBlock() instanceof CompressedFarmBlock) {
                    f1 = 1.0F;
                }
                if (i != 0 || j != 0) {
                    f1 /= 4.0F;
                }
                f += f1;
            }
        }
        BlockPos north = pos.north();
        BlockPos south = pos.south();
        BlockPos west = pos.west();
        BlockPos east = pos.east();
        boolean row = level.getBlockState(west).getBlock() == this
            || level.getBlockState(east).getBlock() == this;
        boolean column = level.getBlockState(north).getBlock() == this
            || level.getBlockState(south).getBlock() == this;
        if (row && column) {
            f /= 2.0F;
        } else {
            boolean diagonal = level.getBlockState(west.north()).getBlock() == this
                || level.getBlockState(east.north()).getBlock() == this
                || level.getBlockState(east.south()).getBlock() == this
                || level.getBlockState(west.south()).getBlock() == this;
            if (diagonal) {
                f /= 2.0F;
            }
        }
        return f;
    }
}
