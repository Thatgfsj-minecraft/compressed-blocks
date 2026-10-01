package dev.compressedblocks.fabric;

import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedPotRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** 客户端：盆栽方块实体渲染器（盆内土壤与作物）。 */
public class CompressedBlocksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(CompressedBlocks.POT_TYPE, CompressedPotRenderer::new);
    }
}
