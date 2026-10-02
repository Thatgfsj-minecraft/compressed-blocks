package dev.compressedblocks.neoforge;

import dev.compressedblocks.CobblestoneGeneratorBlockEntity;
import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedHooks;
import dev.compressedblocks.CompressedPotBlockEntity;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(CompressedBlocks.MOD_ID)
public class CompressedBlocksNeoForge {

    public CompressedBlocksNeoForge(IEventBus modEventBus) {
        // 注意：不能在构造期触达 CompressedBlocks——new Block() 会写注册表的侵入式 holder，
        // 而 21.11 构造期注册表已冻结；核心类首次加载必须发生在 RegisterEvent（注册期）内。
        modEventBus.addListener(this::onRegister);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onIncomingDamage);
    }

    private void onRegister(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.BLOCK) {
            event.register(Registries.BLOCK, helper -> {
                for (CompressedBlocks.BlockReg e : CompressedBlocks.blocks()) {
                    helper.register(CompressedBlocks.id(e.name()), e.block());
                }
            });
        } else if (event.getRegistryKey() == Registries.ITEM) {
            event.register(Registries.ITEM, helper -> {
                Set<String> done = new HashSet<>();
                for (CompressedBlocks.BlockReg e : CompressedBlocks.blocks()) {
                    if (e.itemName() != null) {
                        Item item = CompressedBlocks.itemByName(e.itemName());
                        helper.register(CompressedBlocks.id(e.itemName()), item);
                        done.add(e.itemName());
                    }
                }
                for (CompressedBlocks.ItemReg e : CompressedBlocks.items()) {
                    if (!done.contains(e.name())) {
                        helper.register(CompressedBlocks.id(e.name()), e.item());
                    }
                }
            });
        } else if (event.getRegistryKey() == Registries.BLOCK_ENTITY_TYPE) {
            event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
                // 1.21.11 原版构造器私有，NeoForge 提供公开便捷构造
                CompressedBlocks.POT_TYPE = new BlockEntityType<>(CompressedPotBlockEntity::new,
                    CompressedBlocks.POT);
                CompressedBlocks.GENERATOR_TYPE = new BlockEntityType<>(CobblestoneGeneratorBlockEntity::new,
                    CompressedBlocks.GENERATOR_BLOCKS.toArray(new Block[0]));
                helper.register(CompressedBlocks.id("pot"), CompressedBlocks.POT_TYPE);
            });
        } else if (event.getRegistryKey() == Registries.CREATIVE_MODE_TAB) {
            // "压缩盆栽"栏由附属 mod 独有
            event.register(Registries.CREATIVE_MODE_TAB, helper -> {
                helper.register(CompressedBlocks.id("blocks"), CompressedBlocks.TAB_BLOCKS);
                helper.register(CompressedBlocks.id("tools"), CompressedBlocks.TAB_TOOLS);
                helper.register(CompressedBlocks.id("food"), CompressedBlocks.TAB_FOOD);
            });
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        CompressedBlocks.selfTest(event.getServer().overworld());
    }

    /** 护甲结算：抗性提升 + 九重饱食度常满。 */
    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            CompressedHooks.armorTick(player);
        }
    }

    /** 九重满套：取消一切伤害（含 /kill）。 */
    private void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && CompressedHooks.rejectDamage(player)) {
            event.setCanceled(true);
        }
    }
}
