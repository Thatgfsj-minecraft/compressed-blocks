package dev.compressedblocks.neoforge;

import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedChestRenderer;
import dev.compressedblocks.CompressedPotRenderer;
import dev.compressedblocks.CompressedShulkerRenderer;
import dev.compressedblocks.ScrollingContainerScreen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 客户端：盆栽/箱子/潜影盒 BER + 植物 chunk 渲染层（1.21.11 模型内 render_type 已废弃，须注册）+ 滚动容器界面。 */
@EventBusSubscriber(modid = CompressedBlocks.MOD_ID, value = Dist.CLIENT)
public class CompressedBlocksNeoForgeClient {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CompressedBlocks.POT_TYPE, CompressedPotRenderer::new);
        event.registerBlockEntityRenderer(CompressedBlocks.SHULKER_TYPE, CompressedShulkerRenderer::new);
        event.registerBlockEntityRenderer(CompressedBlocks.CHEST_TYPE, CompressedChestRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CompressedBlocks.CHEST_MENU_TYPE, ScrollingContainerScreen::new);
        event.register(CompressedBlocks.SHULKER_MENU_TYPE, ScrollingContainerScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        for (Block block : CompressedBlocks.SAPLING_BLOCKS) {
            ItemBlockRenderTypes.setRenderLayer(block, ChunkSectionLayer.CUTOUT);
        }
        for (Block block : CompressedBlocks.CANE_BLOCKS) {
            ItemBlockRenderTypes.setRenderLayer(block, ChunkSectionLayer.CUTOUT);
        }
        for (Block block : CompressedBlocks.CROP_BLOCKS) {
            ItemBlockRenderTypes.setRenderLayer(block, ChunkSectionLayer.CUTOUT);
        }
        for (Block block : CompressedBlocks.LEAVES_BLOCKS) {
            ItemBlockRenderTypes.setRenderLayer(block, ChunkSectionLayer.CUTOUT);
        }
    }
}
