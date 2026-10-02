package dev.compressedblocks.fabric;

import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedHooks;
import dev.compressedblocks.CobblestoneGeneratorBlockEntity;
import dev.compressedblocks.CompressedPotBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

public class CompressedBlocksFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        java.util.Set<String> registeredItems = new java.util.HashSet<>();
        for (CompressedBlocks.BlockReg e : CompressedBlocks.blocks()) {
            Registry.register(BuiltInRegistries.BLOCK, CompressedBlocks.id(e.name()), e.block());
            if (e.itemName() != null) {
                Item item = CompressedBlocks.itemByName(e.itemName());
                Registry.register(BuiltInRegistries.ITEM, CompressedBlocks.id(e.itemName()), item);
                registeredItems.add(e.itemName());
            }
        }
        for (CompressedBlocks.ItemReg e : CompressedBlocks.items()) {
            if (registeredItems.contains(e.name())) {
                continue; // 方块物品已在上面随方块注册
            }
            Registry.register(BuiltInRegistries.ITEM, CompressedBlocks.id(e.name()), e.item());
        }
        // 创造物品栏：方块/工具/食物/盆栽四个分类栏
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CompressedBlocks.id("blocks"), CompressedBlocks.TAB_BLOCKS);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CompressedBlocks.id("tools"), CompressedBlocks.TAB_TOOLS);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CompressedBlocks.id("food"), CompressedBlocks.TAB_FOOD);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CompressedBlocks.id("pots"), CompressedBlocks.TAB_POTS);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CompressedBlocks.id("pot"),
            CompressedBlocks.POT_TYPE = FabricBlockEntityTypeBuilder
                .create(CompressedPotBlockEntity::new, CompressedBlocks.POT)
                .build());
        // 刷石机方块实体类型（产物随压缩等级）
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CompressedBlocks.id("cobblestone_generator"),
            CompressedBlocks.GENERATOR_TYPE = FabricBlockEntityTypeBuilder
                .create(CobblestoneGeneratorBlockEntity::new,
                    CompressedBlocks.GENERATOR_BLOCKS.toArray(new Block[0]))
                .build());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CompressedBlocks.selfTest(server.overworld()));
        // 护甲结算：抗性提升 + 九重饱食度常满
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                CompressedHooks.armorTick(player);
            }
        });
        // 九重满套：取消一切伤害（含 /kill）
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
            !(entity instanceof ServerPlayer player && CompressedHooks.rejectDamage(player)));
    }
}
