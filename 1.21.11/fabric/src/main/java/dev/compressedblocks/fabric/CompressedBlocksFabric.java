package dev.compressedblocks.fabric;

import dev.compressedblocks.CompressedBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class CompressedBlocksFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        for (CompressedBlocks.BlockEntry e : CompressedBlocks.blocks()) {
            Registry.register(BuiltInRegistries.BLOCK, CompressedBlocks.id(e.name()), e.block());
            Registry.register(BuiltInRegistries.ITEM, CompressedBlocks.id(e.name()), e.item());
        }
        for (CompressedBlocks.ItemEntry e : CompressedBlocks.items()) {
            Registry.register(BuiltInRegistries.ITEM, CompressedBlocks.id(e.name()), e.item());
        }
        // 独立创造物品栏"压缩"
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CompressedBlocks.id("main"), CompressedBlocks.TAB);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CompressedBlocks.selfTest());
    }
}
