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
