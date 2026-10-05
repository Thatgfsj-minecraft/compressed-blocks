package dev.compressedblockspot;

import dev.compressedblocks.CompressedPotBlock;

/**
 * 压缩盆栽方块：外观加深（贴图由附属资源提供），逻辑完全复用主 mod 盆栽
 * （土壤/种植/收割/漏斗盆栽自动压箱补种）；
 * 与主 mod 盆栽的唯一差异：SPOT_UNLOCKED 后能种压缩系植物。
 * 1.12.2：附属盆栽无独立创造栏（归入主 mod 压缩工具栏，降级记录）。
 */
public class SpotPotBlock extends CompressedPotBlock {
    public SpotPotBlock(boolean hopper) {
        super(hopper);
    }
}
