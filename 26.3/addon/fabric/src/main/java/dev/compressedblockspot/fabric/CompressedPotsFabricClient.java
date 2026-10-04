package dev.compressedblockspot.fabric;

import dev.compressedblockspot.CompressedPotsAddon;
import dev.compressedblockspot.SpotPotRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class CompressedPotsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(CompressedPotsAddon.POT_TYPE, SpotPotRenderer::new);
    }
}
