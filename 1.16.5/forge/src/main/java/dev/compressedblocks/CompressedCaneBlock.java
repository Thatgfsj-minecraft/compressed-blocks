package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/** 压缩甘蔗（40 线 × 9 重）：生长与原版一致（含相邻水要求），只能种在 N 重以上的压缩泥土/沙子上。 */
public class CompressedCaneBlock extends SugarCaneBlock {
    private final int level;

    public CompressedCaneBlock(int level, Properties props) {
        super(props);
        this.level = level;
    }

    /** 压缩重数（盆栽生长提速用）。 */
    public int level() {
        return this.level;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.getBlock() == this) {
            return true;
        }
        Integer dirt = CompressedBlocks.dirtLevel(below);
        Integer sand = CompressedBlocks.sandLevel(below);
        int soil = dirt != null ? dirt : (sand != null ? sand : -1);
        if (soil < this.level) {
            return false;
        }
        // 原版规则：土上种植需要紧邻淡水（或霜冰）
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjPos = pos.below().relative(dir);
            if (level.getFluidState(adjPos).is(FluidTags.WATER)
                || level.getBlockState(adjPos).is(Blocks.FROSTED_ICE)) {
                return true;
            }
        }
        return false;
    }
}
