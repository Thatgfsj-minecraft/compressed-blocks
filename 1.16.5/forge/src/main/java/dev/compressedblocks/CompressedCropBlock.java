package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

/** 压缩作物（4 种 × 3 级封顶）：只能种在等级足够的压缩耕地上，生长速度公式与原版一致。 */
public class CompressedCropBlock extends CropBlock {
    private final int level;
    // 种子物品在方块之后构建（1.16.5 BlockItem 需要方块引用），后挂进来
    private Item seedItem;

    public CompressedCropBlock(int level, Properties props) {
        super(props);
        this.level = level;
    }

    /** 后挂种子物品（getBaseSeedId 供原版骨粉/掉落逻辑使用）。 */
    public void setSeedItem(Item seedItem) {
        this.seedItem = seedItem;
    }

    public int level() {
        return this.level;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getBlock() instanceof CompressedFarmBlock
            && ((CompressedFarmBlock) state.getBlock()).level() >= this.level;
    }

    @Override
    public Item getBaseSeedId() {
        return this.seedItem;
    }

    /** 原版 randomTick 镜像（耕地判定换成压缩耕地），速度公式见 growthSpeed。 */
    @Override
    public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level,
                           BlockPos pos, java.util.Random random) {
        if (level.getRawBrightness(pos, 0) >= 9) {
            int age = this.getAge(state);
            if (age < this.getMaxAge()
                && random.nextInt((int) (25.0F / this.growthSpeed(state, level, pos)) + 1) == 0) {
                level.setBlock(pos, this.getStateForAge(age + 1), 2);
            }
        }
    }

    /** 原版 getGrowthSpeed 公式，耕地判定换成压缩耕地（任一等级）。 */
    private float growthSpeed(BlockState state, BlockGetter level, BlockPos pos) {
        float f = 1.0F;
        BlockPos below = pos.below();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float f1 = 0.0F;
                if (level.getBlockState(below.offset(i, 0, j)).getBlock() instanceof CompressedFarmBlock) {
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
        boolean row = level.getBlockState(west).is(state.getBlock())
            || level.getBlockState(east).is(state.getBlock());
        boolean column = level.getBlockState(north).is(state.getBlock())
            || level.getBlockState(south).is(state.getBlock());
        if (row && column) {
            f /= 2.0F;
        } else {
            boolean diagonal = level.getBlockState(west.north()).is(state.getBlock())
                || level.getBlockState(east.north()).is(state.getBlock())
                || level.getBlockState(east.south()).is(state.getBlock())
                || level.getBlockState(west.south()).is(state.getBlock());
            if (diagonal) {
                f /= 2.0F;
            }
        }
        return f;
    }
}
