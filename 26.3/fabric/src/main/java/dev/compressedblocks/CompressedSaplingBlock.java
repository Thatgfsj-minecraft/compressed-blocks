package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 压缩树苗：只能种在等级足够的压缩泥土上。
 * 树形由 TreeGrower 引用数据包里的压缩树特征（原版形状 + 同等级压缩原木/树叶），
 * 深色橡木/苍白橡木按原版规则需要 2×2 四苗，丛林 1 苗小树、2×2 巨型。
 * 实机验证（2026-10-05 headless 服务端）：9 木树苗随机刻生长全部正常
 * （oak/spruce/mangrove/birch 均观测到 growTree=true 成长为压缩树）。
 */
public class CompressedSaplingBlock extends SaplingBlock {
    private final int level;

    public CompressedSaplingBlock(TreeGrower grower, int level, BlockBehaviour.Properties props) {
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
