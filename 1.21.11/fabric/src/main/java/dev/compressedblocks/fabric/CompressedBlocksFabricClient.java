package dev.compressedblocks.fabric;

import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedChestRenderer;
import dev.compressedblocks.CompressedFurnaceMenus;
import dev.compressedblocks.CompressedFurnaceScreen;
import dev.compressedblocks.CompressedPotRenderer;
import dev.compressedblocks.CompressedShulkerRenderer;
import dev.compressedblocks.ScrollingContainerScreen;
import dev.compressedblocks.WoodenHopperScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;

/** 客户端：盆栽/箱子/潜影盒 BER + 植物 chunk 渲染层（1.21.11 模型内 render_type 已废弃，须注册）+ 滚动容器界面。 */
public class CompressedBlocksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(CompressedBlocks.POT_TYPE, CompressedPotRenderer::new);
        BlockEntityRenderers.register(CompressedBlocks.SHULKER_TYPE, CompressedShulkerRenderer::new);
        BlockEntityRenderers.register(CompressedBlocks.CHEST_TYPE, CompressedChestRenderer::new);
        MenuScreens.register(CompressedBlocks.CHEST_MENU_TYPE, ScrollingContainerScreen::new);
        MenuScreens.register(CompressedBlocks.SHULKER_MENU_TYPE, ScrollingContainerScreen::new);
        MenuScreens.register(CompressedBlocks.WOODEN_HOPPER_MENU_TYPE, WoodenHopperScreen::new);
        MenuScreens.register(CompressedBlocks.COMPRESSED_WOODEN_HOPPER_MENU_TYPE, WoodenHopperScreen::new);
        MenuScreens.register(CompressedFurnaceMenus.MENU_1X, CompressedFurnaceScreen::new);
        MenuScreens.register(CompressedFurnaceMenus.MENU_2X, CompressedFurnaceScreen::new);
        MenuScreens.register(CompressedFurnaceMenus.MENU_3X, CompressedFurnaceScreen::new);
        for (Block block : CompressedBlocks.SAPLING_BLOCKS) {
            BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, block);
        }
        for (Block block : CompressedBlocks.CANE_BLOCKS) {
            BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, block);
        }
        for (Block block : CompressedBlocks.CROP_BLOCKS) {
            BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, block);
        }
        for (Block block : CompressedBlocks.LEAVES_BLOCKS) {
            BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, block);
        }
    }
}
