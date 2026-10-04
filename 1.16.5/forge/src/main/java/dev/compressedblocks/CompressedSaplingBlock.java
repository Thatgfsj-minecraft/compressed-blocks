package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockState;

/** 压缩树苗（6 木 × 9 重）：只能种在等级足够的压缩泥土上；树形走代码注册的压缩树特征。 */
public class CompressedSaplingBlock extends SaplingBlock {
    private final int level;

    public CompressedSaplingBlock(AbstractTreeGrower grower, int level, Properties props) {
        super(grower, props);
        this.level = level;
    }

    public int level() {
        return this.level;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        Integer dirtLevel = CompressedBlocks.dirtLevel(state);
        return dirtLevel != null && dirtLevel >= this.level;
    }
}
