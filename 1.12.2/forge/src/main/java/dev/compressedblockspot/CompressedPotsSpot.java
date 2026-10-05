package dev.compressedblockspot;

import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedPotBlock;
import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 附属 mod compressedblockspot（1.12.2 与主 mod 同 jar 双 mod，mcmod.info 双条目）：
 * 压缩盆栽 + 压缩漏斗盆栽——外观为加深的主 mod 盆栽，能种**压缩系**作物/树苗/甘蔗
 * （SPOT_UNLOCKED 门控解锁主 mod 盆栽识别逻辑；附属盆栽自身直接复用主 mod 盆栽 BE 逻辑）。
 * 配方：压缩盆栽 = 5× 任意一重压缩方块（船形，compressedblocks:pot_material 标签）；
 * 压缩漏斗盆栽 = 压缩盆栽 + 漏斗（无序）。
 * 1.12.2：附属注册同样走 RegistryEvent（@Mod.EventBusSubscriber(modid=附属 id)）。
 */
@Mod(modid = CompressedPotsSpot.MOD_ID, name = "Compressed Blocks Pots Spot",
    version = CompressedBlocks.VERSION, acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "after:compressedblocks")
public final class CompressedPotsSpot {
    public static final String MOD_ID = "compressedblockspot";

    /** 注册中转引用（事件静态上下文与 @Mod 实例解耦）。 */
    static Block COMPRESSED_POT;
    static Block COMPRESSED_HOPPER_POT;

    public CompressedPotsSpot() {
        // 解锁主 mod 盆栽的压缩系种植识别
        CompressedPotBlock.SPOT_UNLOCKED = true;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 构建附属方块实例（注册走下方事件订阅者）
        COMPRESSED_POT = new SpotPotBlock(false);
        COMPRESSED_POT.setHardness(1.5F);
        COMPRESSED_POT.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
        dev.compressedblocks.forge.BlockAccessors.sound(COMPRESSED_POT, SoundType.STONE);
        COMPRESSED_POT.setTranslationKey(MOD_ID + ".compressed_pot");
        COMPRESSED_POT.setRegistryName(MOD_ID, "compressed_pot");
        COMPRESSED_POT.setCreativeTab(CompressedBlocks.TAB_TOOLS);

        COMPRESSED_HOPPER_POT = new SpotPotBlock(true);
        COMPRESSED_HOPPER_POT.setHardness(1.5F);
        COMPRESSED_HOPPER_POT.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
        dev.compressedblocks.forge.BlockAccessors.sound(COMPRESSED_HOPPER_POT, SoundType.STONE);
        COMPRESSED_HOPPER_POT.setTranslationKey(MOD_ID + ".compressed_hopper_pot");
        COMPRESSED_HOPPER_POT.setRegistryName(MOD_ID, "compressed_hopper_pot");
        COMPRESSED_HOPPER_POT.setCreativeTab(CompressedBlocks.TAB_TOOLS);
    }

    @Mod.EventBusSubscriber(modid = MOD_ID)
    private static final class Registration {
        @SubscribeEvent
        public static void registerBlocks(RegistryEvent.Register<Block> event) {
            if (COMPRESSED_POT != null) {
                event.getRegistry().register(COMPRESSED_POT);
            }
            if (COMPRESSED_HOPPER_POT != null) {
                event.getRegistry().register(COMPRESSED_HOPPER_POT);
            }
        }

        @SubscribeEvent
        public static void registerItems(RegistryEvent.Register<Item> event) {
            registerWithItem(event, COMPRESSED_POT);
            registerWithItem(event, COMPRESSED_HOPPER_POT);
        }

        private static void registerWithItem(RegistryEvent.Register<Item> event, Block block) {
            if (block == null || block.getRegistryName() == null) {
                return;
            }
            ItemBlock item = new ItemBlock(block);
            item.setRegistryName(block.getRegistryName());
            item.setTranslationKey(block.getTranslationKey());
            event.getRegistry().register(item);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
