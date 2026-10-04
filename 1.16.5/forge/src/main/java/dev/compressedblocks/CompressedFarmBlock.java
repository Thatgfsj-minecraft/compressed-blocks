package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 压缩耕地（9 级）：外观同原版（15/16 高）。无干湿状态、不踩踏退化；挖掉掉对应等级压缩泥土。 */
public class CompressedFarmBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);
    private final int level;

    public CompressedFarmBlock(int level, Properties props) {
        super(props);
        this.level = level;
    }

    public int level() {
        return this.level;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
