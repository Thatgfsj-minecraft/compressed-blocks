package dev.compressedblocks;

import net.minecraft.block.BlockReed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 压缩甘蔗（36+1 材料线 × 9 重）：生长与原版一致（含相邻水要求），
 * 只能种在 N 重以上的压缩泥土/沙子上。
 * 1.12.2 的 BlockReed 带 AGE(0..15) 属性（blockstates 需 16 变体，生成器已产出）。
 */
public class CompressedCaneBlock extends BlockReed {
    private final int level;

    public CompressedCaneBlock(int level) {
        this.level = level;
    }

    /** 压缩重数（盆栽生长提速用）。 */
    public int level() {
        return this.level;
    }

    /** 原版甘蔗存活判定镜像：下方同方块续接，否则要求 N 重以上压缩泥土/沙子 + 紧邻水。 */
    @Override
    public boolean canBlockStay(World world, BlockPos pos) {
        IBlockState below = world.getBlockState(pos.down());
        if (below.getBlock() == this) {
            return true;
        }
        Integer dirt = CompressedBlocks.dirtLevel(below);
        Integer sand = CompressedBlocks.sandLevel(below);
        int soil = dirt != null ? dirt : (sand != null ? sand : -1);
        if (soil < this.level) {
            return false;
        }
        // 原版规则：土上种植需要紧邻水
        for (net.minecraft.util.EnumFacing dir : net.minecraft.util.EnumFacing.Plane.HORIZONTAL) {
            if (world.getBlockState(pos.down().offset(dir)).getMaterial() == net.minecraft.block.material.Material.WATER) {
                return true;
            }
        }
        return false;
    }
}
