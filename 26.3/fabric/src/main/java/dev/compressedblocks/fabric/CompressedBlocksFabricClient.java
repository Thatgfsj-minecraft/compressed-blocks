package dev.compressedblocks.fabric;

import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedChestRenderer;
import dev.compressedblocks.CompressedPotRenderer;
import dev.compressedblocks.CompressedShulkerRenderer;
import dev.compressedblocks.ScrollingContainerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * 客户端：盆栽/箱子/潜影盒 BER + 滚动容器界面。
 * 26.3：fabric-api 的 BlockRenderLayerMap 已随新渲染管线移除——chunk 剖面只剩
 * SOLID/CUTOUT/TRANSLUCENT 三层，按材质透明度自动判定；植物的镂空贴图
 * （树苗/甘蔗/作物/树叶）无需再显式注册渲染层。
 */
public class CompressedBlocksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(CompressedBlocks.POT_TYPE, CompressedPotRenderer::new);
        BlockEntityRenderers.register(CompressedBlocks.SHULKER_TYPE, CompressedShulkerRenderer::new);
        BlockEntityRenderers.register(CompressedBlocks.CHEST_TYPE, CompressedChestRenderer::new);
        MenuScreens.register(CompressedBlocks.CHEST_MENU_TYPE, ScrollingContainerScreen::new);
        MenuScreens.register(CompressedBlocks.SHULKER_MENU_TYPE, ScrollingContainerScreen::new);
    }
}
