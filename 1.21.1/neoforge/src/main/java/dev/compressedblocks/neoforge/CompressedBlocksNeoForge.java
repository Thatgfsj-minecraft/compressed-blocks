package dev.compressedblocks.neoforge;

import dev.compressedblocks.CompressedBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CompressedBlocks.MOD_ID)
public class CompressedBlocksNeoForge {

    public CompressedBlocksNeoForge(IEventBus modEventBus) {
        DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(CompressedBlocks.MOD_ID);
        DeferredRegister.Items items = DeferredRegister.createItems(CompressedBlocks.MOD_ID);
        DeferredRegister<CreativeModeTab> tabs = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CompressedBlocks.MOD_ID);
        for (CompressedBlocks.BlockEntry e : CompressedBlocks.blocks()) {
            blocks.register(e.name(), () -> e.block());
            items.register(e.name(), () -> e.item());
        }
        for (CompressedBlocks.ItemEntry e : CompressedBlocks.items()) {
            items.register(e.name(), () -> e.item());
        }
        // 独立创造物品栏"压缩"
        tabs.register("main", () -> CompressedBlocks.TAB);
        blocks.register(modEventBus);
        items.register(modEventBus);
        tabs.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        CompressedBlocks.selfTest();
    }
}
