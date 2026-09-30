package dev.compressedblocks.mixin;

import dev.compressedblocks.CompressedHooks;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁砧材料修理改半：原版每个材料只修最大耐久的 25%，本模组工具/盔甲改为 1 个材料修 50%。
 * 在 createResult 末尾按 50%/材料 重算结果耐久（仅当两格为本模组物品+合法修理材料时生效）。
 */
@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {

    @Shadow private int repairItemCountCost;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void compressedblocks$halfMaterialRepair(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack input = menu.slots.get(0).getItem();
        ItemStack material = menu.slots.get(1).getItem();
        ItemStack result = menu.slots.get(2).getItem();
        if (CompressedHooks.halfMaterialRepair(input, material, result)) {
            this.repairItemCountCost = Math.max(1, material.getCount());
        }
    }
}
