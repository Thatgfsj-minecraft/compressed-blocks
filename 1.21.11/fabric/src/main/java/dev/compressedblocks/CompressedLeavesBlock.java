package dev.compressedblocks;

import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** 压缩树叶：走原版凋落算法（distance 1-7 随机刻）；压缩原木在 #logs 标签族里，凋落判定按原版工作。 */
public class CompressedLeavesBlock extends TintedParticleLeavesBlock {
    public CompressedLeavesBlock(BlockBehaviour.Properties props) {
        super(0.01F, props);
    }
}
