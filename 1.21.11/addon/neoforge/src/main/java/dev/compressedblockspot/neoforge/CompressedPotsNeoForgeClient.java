package dev.compressedblockspot.neoforge;

import dev.compressedblockspot.CompressedPotsAddon;
import dev.compressedblockspot.SpotPotRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CompressedPotsAddon.MOD_ID, value = Dist.CLIENT)
public class CompressedPotsNeoForgeClient {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CompressedPotsAddon.POT_TYPE, SpotPotRenderer::new);
    }
}
