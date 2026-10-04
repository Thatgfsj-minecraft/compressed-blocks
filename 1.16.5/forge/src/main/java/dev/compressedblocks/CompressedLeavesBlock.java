package dev.compressedblocks;

import net.minecraft.world.level.block.LeavesBlock;

/** 压缩树叶（6 木 × 9 重）：原版凋落算法（随机刻距离衰减），透明层由模型 render_type 声明。 */
public class CompressedLeavesBlock extends LeavesBlock {
    public CompressedLeavesBlock(Properties props) {
        super(props);
    }
}
