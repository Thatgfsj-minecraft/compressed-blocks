package dev.compressedblocks.neoforge;

import dev.compressedblocks.CompressedBlocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** 数据生成入口（{@code ./gradlew runData}）：目前只产出燃料数据图。 */
@EventBusSubscriber(modid = CompressedBlocks.MOD_ID)
public final class CompressedBlocksDataGen {
    private CompressedBlocksDataGen() {
    }

    @SubscribeEvent
    static void gather(GatherDataEvent.Server event) {
        var generator = event.getGenerator();
        generator.addProvider(true,
            new FuelDataMapProvider(generator.getPackOutput(), event.getLookupProvider()));
    }
}