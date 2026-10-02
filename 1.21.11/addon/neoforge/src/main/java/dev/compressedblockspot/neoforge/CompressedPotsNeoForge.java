package dev.compressedblockspot.neoforge;

import dev.compressedblockspot.CompressedPotsAddon;
import dev.compressedblockspot.SpotPotBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(CompressedPotsAddon.MOD_ID)
public class CompressedPotsNeoForge {

    public CompressedPotsNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegister);
    }

    private void onRegister(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.BLOCK) {
            CompressedPotsAddon.construct();
            event.register(Registries.BLOCK, helper -> {
                helper.register(CompressedPotsAddon.id("compressed_pot"), CompressedPotsAddon.COMPRESSED_POT);
                helper.register(CompressedPotsAddon.id("compressed_hopper_pot"), CompressedPotsAddon.HOPPER_POT);
            });
        } else if (event.getRegistryKey() == Registries.ITEM) {
            event.register(Registries.ITEM, helper -> {
                helper.register(CompressedPotsAddon.id("compressed_pot"),
                    CompressedPotsAddon.itemFor(CompressedPotsAddon.COMPRESSED_POT, "compressed_pot"));
                helper.register(CompressedPotsAddon.id("compressed_hopper_pot"),
                    CompressedPotsAddon.itemFor(CompressedPotsAddon.HOPPER_POT, "compressed_hopper_pot"));
            });
        } else if (event.getRegistryKey() == Registries.BLOCK_ENTITY_TYPE) {
            CompressedPotsAddon.constructBlockEntityType();
            event.register(Registries.BLOCK_ENTITY_TYPE, helper ->
                helper.register(CompressedPotsAddon.id("pot"), CompressedPotsAddon.POT_TYPE));
        } else if (event.getRegistryKey() == Registries.CREATIVE_MODE_TAB) {
            event.register(Registries.CREATIVE_MODE_TAB, helper -> {
                CompressedPotsAddon.TAB = net.minecraft.world.item.CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.compressedblockspot.pots"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(CompressedPotsAddon.HOPPER_POT))
                    .displayItems((parameters, output) -> {
                        output.accept(CompressedPotsAddon.HOPPER_POT);
                        output.accept(CompressedPotsAddon.COMPRESSED_POT);
                    })
                    .build();
                helper.register(CompressedPotsAddon.id("pots"), CompressedPotsAddon.TAB);
            });
        }
    }
}
