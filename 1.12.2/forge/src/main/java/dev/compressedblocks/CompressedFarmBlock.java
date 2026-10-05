package dev.compressedblocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * 压缩耕地（9 级）：外观同原版（15/16 高）。无干湿状态、不踩踏退化；
 * 挖掉掉对应等级压缩泥土（战利品表实现）。无方块属性 → blockstate 用 "normal" 变体。
 */
public class CompressedFarmBlock extends Block {
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.9375, 1.0);
    private final int level;

    public CompressedFarmBlock(int level) {
        super(Material.GROUND);
        this.level = level;
    }

    public int level() {
        return this.level;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }
}
