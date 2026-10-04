package dev.compressedblockspot;

import dev.compressedblocks.CompressedPotBlock;
import net.minecraft.world.level.block.Block;

/**
 * 压缩盆栽方块：外观加深（贴图由附属资源提供），逻辑完全复用主 mod 盆栽
 * （土壤/种植/收割/漏斗盆栽自动压箱补种）；
 * 与主 mod 盆栽的唯一差异：SPOT_UNLOCKED 后能种压缩系植物。
 */
public class SpotPotBlock extends CompressedPotBlock {
    public SpotPotBlock(Properties props) {
        super(props);
    }

    public SpotPotBlock(Properties props, boolean hopper) {
        super(props, hopper);
    }
}
