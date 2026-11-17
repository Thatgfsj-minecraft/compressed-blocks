package dev.compressedblocks.neoforge;

import dev.compressedblocks.CompressedFuels;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * NeoForge 燃料数据图生成器：唯一数值来源是 {@link CompressedFuels}（与 Fabric 端同一份规则）。
 *
 * <p>物品集合直接来自已注册物品表（{@code CompressedFuels.forEach} 遍历 BuiltInRegistries.ITEM），
 * 所以不会出现「生成器漏掉新物品」或「写进不存在的 id」。条目数与规则常量对拍，漂移时 runData 直接失败。</p>
 */
public final class FuelDataMapProvider extends DataMapProvider {
    FuelDataMapProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        DataMapProvider.Builder<FurnaceFuel, net.minecraft.world.item.Item> fuels =
            builder(NeoForgeDataMaps.FURNACE_FUELS);
        int[] count = {0};
        CompressedFuels.forEach((item, ticks) -> {
            fuels.add(item.builtInRegistryHolder(), new FurnaceFuel(ticks), false);
            count[0]++;
        });
        if (count[0] != CompressedFuels.EXPECTED_ITEM_COUNT) {
            throw new IllegalStateException("fuel entries " + count[0]
                + " != " + CompressedFuels.EXPECTED_ITEM_COUNT);
        }
    }
}