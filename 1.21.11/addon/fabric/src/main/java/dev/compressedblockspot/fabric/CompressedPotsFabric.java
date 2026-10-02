package dev.compressedblockspot.fabric;

import dev.compressedblockspot.CompressedPotsAddon;
import dev.compressedblockspot.SpotPotBlockEntity;
import dev.compressedblockspot.SpotPotRenderer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

public class CompressedPotsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CompressedPotsAddon.init();
        // 大容量容器插入钩子：Fabric Transfer API（储物抽屉等现代 mod 的标准接口）
        CompressedPotsAddon.ITEM_SINK = new CompressedPotsAddon.ItemSink() {
            @Override
            public boolean accepts(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                   net.minecraft.core.Direction side) {
                return net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, side) != null;
            }

            @Override
            public long insert(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                               net.minecraft.core.Direction side, net.minecraft.world.item.ItemStack stack) {
                net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
                    storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, side);
                if (storage == null) {
                    return 0;
                }
                try (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction tx =
                         net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    long moved = storage.insert(
                        net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(stack), stack.getCount(), tx);
                    if (moved > 0) {
                        tx.commit();
                        return moved;
                    }
                }
                return 0;
            }
        };
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CompressedPotsAddon.id("pot"),
            CompressedPotsAddon.POT_TYPE = FabricBlockEntityTypeBuilder
                .create(SpotPotBlockEntity::new,
                    CompressedPotsAddon.COMPRESSED_POT, CompressedPotsAddon.HOPPER_POT)
                .build());
        // 保持 GENERATOR-style 无关：确保方块集合非空
        if (CompressedPotsAddon.BLOCKS.isEmpty()) {
            throw new IllegalStateException("compressedblockspot: no blocks registered");
        }
        for (Block ignored : CompressedPotsAddon.BLOCKS) {
            // 已在 init 注册
        }
    }
}
