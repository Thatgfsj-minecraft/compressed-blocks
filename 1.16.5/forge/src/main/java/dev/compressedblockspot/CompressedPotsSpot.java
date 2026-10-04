package dev.compressedblockspot;

import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedPotBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.core.Registry;
import net.minecraftforge.fml.common.Mod;

/**
 * 附属 mod compressedblockspot（1.16.5 与主 mod 同 jar 双 mod）：
 * 压缩盆栽 + 压缩漏斗盆栽——外观为加深的主 mod 盆栽，能种**压缩系**作物/树苗/甘蔗
 * （SPOT_UNLOCKED 门控解锁主 mod 盆栽识别逻辑；附属盆栽自身直接复用主 mod 盆栽 BE 逻辑）。
 * 配方：压缩盆栽 = 5× 任意一重压缩方块（船形，主 mod #compressedblocks:pot_material 标签）；
 * 压缩漏斗盆栽 = 压缩盆栽 + 漏斗（无序）。
 */
@Mod("compressedblockspot")
public class CompressedPotsSpot {
    public CompressedPotsSpot() {
        // 解锁主 mod 盆栽的压缩系种植识别
        CompressedPotBlock.SPOT_UNLOCKED = true;
        // 压缩盆栽
        Block compressedPot = new SpotPotBlock(BlockBehaviour.Properties
            .of(Material.STONE, MaterialColor.TERRACOTTA_ORANGE)
            .strength(1.5F, CompressedBlocks.STORAGE_BLAST)
            .sound(SoundType.STONE)
            .noOcclusion());
        Registry.register(Registry.BLOCK, CompressedPotsSpot.id("compressed_pot"), compressedPot);
        Registry.register(Registry.ITEM, CompressedPotsSpot.id("compressed_pot"),
            new BlockItem(compressedPot, new Item.Properties().tab(CompressedBlocks.TAB_TOOLS)));
        // 压缩漏斗盆栽
        Block compressedHopperPot = new SpotPotBlock(BlockBehaviour.Properties
            .of(Material.STONE, MaterialColor.TERRACOTTA_ORANGE)
            .strength(1.5F, CompressedBlocks.STORAGE_BLAST)
            .sound(SoundType.STONE)
            .noOcclusion(), true);
        Registry.register(Registry.BLOCK, CompressedPotsSpot.id("compressed_hopper_pot"), compressedHopperPot);
        Registry.register(Registry.ITEM, CompressedPotsSpot.id("compressed_hopper_pot"),
            new BlockItem(compressedHopperPot, new Item.Properties().tab(CompressedBlocks.TAB_TOOLS)));
    }

    public static net.minecraft.resources.ResourceLocation id(String path) {
        return new net.minecraft.resources.ResourceLocation("compressedblockspot", path);
    }
}
