package dev.compressedblocks.neoforge;

import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedPotRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** 客户端：盆栽方块实体渲染器（盆内土壤与作物）。 */
@EventBusSubscriber(modid = CompressedBlocks.MOD_ID, value = Dist.CLIENT)
public class CompressedBlocksNeoForgeClient {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CompressedBlocks.POT_TYPE, CompressedPotRenderer::new);
    }
}
