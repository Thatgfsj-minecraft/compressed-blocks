package dev.compressedblockspot.neoforge;

import dev.compressedblockspot.CompressedPotsAddon;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(CompressedPotsAddon.MOD_ID)
public class CompressedPotsNeoForge {

    public CompressedPotsNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegister);
        // FTB Ultimine（连锁破坏）收获适配
        dev.compressedblockspot.FtbUltimineCompat.init();
    }

    private void onRegister(RegisterEvent event) {
        // 大容量容器插入钩子：NeoForge 21.11 Transfer API（储物抽屉等现代 mod 的标准接口）
        if (CompressedPotsAddon.ITEM_SINK == null) {
            CompressedPotsAddon.ITEM_SINK = new CompressedPotsAddon.ItemSink() {
                @Override
                public boolean accepts(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                       net.minecraft.core.Direction side) {
                    return level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, side) != null;
                }

                @Override
                public long insert(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                   net.minecraft.core.Direction side, net.minecraft.world.item.ItemStack stack) {
                    net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>
                        handler = level.getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, side);
                    if (handler == null) {
                        return 0;
                    }
                    try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                             net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                        int moved = 0;
                        for (int i = 0; i < handler.size() && moved < stack.getCount(); i++) {
                            moved += handler.insert(i, net.neoforged.neoforge.transfer.item.ItemResource.of(stack),
                                stack.getCount() - moved, tx);
                        }
                        if (moved > 0) {
                            tx.commit();
                        }
                        return moved;
                    }
                }
            };
        }
        if (event.getRegistryKey() == Registries.BLOCK) {
            CompressedPotsAddon.construct();
            event.register(Registries.BLOCK, helper -> {
                helper.register(CompressedPotsAddon.id("compressed_pot"), CompressedPotsAddon.COMPRESSED_POT);
                helper.register(CompressedPotsAddon.id("compressed_hopper_pot"), CompressedPotsAddon.HOPPER_POT);
                for (int tier = 1; tier <= CompressedPotsAddon.GENERATOR_BLOCKS.size(); tier++) {
                    helper.register(CompressedPotsAddon.id(CompressedPotsAddon.generatorName(tier)),
                        CompressedPotsAddon.GENERATOR_BLOCKS.get(tier - 1));
                }
            });
        } else if (event.getRegistryKey() == Registries.ITEM) {
            event.register(Registries.ITEM, helper -> {
                helper.register(CompressedPotsAddon.id("compressed_pot"),
                    CompressedPotsAddon.itemFor(CompressedPotsAddon.COMPRESSED_POT, "compressed_pot"));
                helper.register(CompressedPotsAddon.id("compressed_hopper_pot"),
                    CompressedPotsAddon.itemFor(CompressedPotsAddon.HOPPER_POT, "compressed_hopper_pot"));
                for (int tier = 1; tier <= CompressedPotsAddon.GENERATOR_BLOCKS.size(); tier++) {
                    String name = CompressedPotsAddon.generatorName(tier);
                    helper.register(CompressedPotsAddon.id(name),
                        CompressedPotsAddon.itemFor(CompressedPotsAddon.GENERATOR_BLOCKS.get(tier - 1), name));
                }
            });
        } else if (event.getRegistryKey() == Registries.BLOCK_ENTITY_TYPE) {
            CompressedPotsAddon.constructBlockEntityType();
            event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
                helper.register(CompressedPotsAddon.id("pot"), CompressedPotsAddon.POT_TYPE);
                // 历史上漏注册过这一行导致启动崩溃：附属刷石机 BE 类型必须注册
                helper.register(CompressedPotsAddon.id("cobblestone_generator"),
                    CompressedPotsAddon.GENERATOR_TYPE);
            });
        } else if (event.getRegistryKey() == Registries.CREATIVE_MODE_TAB) {
            CompressedPotsAddon.constructTabs();
            event.register(Registries.CREATIVE_MODE_TAB, helper -> {
                helper.register(CompressedPotsAddon.id("addon_blocks"), CompressedPotsAddon.TAB_ADDON_BLOCKS);
                helper.register(CompressedPotsAddon.id("addon_tools"), CompressedPotsAddon.TAB_ADDON_TOOLS);
            });
        }
    }
}
