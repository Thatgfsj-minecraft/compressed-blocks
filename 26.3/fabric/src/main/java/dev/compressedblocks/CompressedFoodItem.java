package dev.compressedblocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 压缩食物（3 级封顶）：canAlwaysEat，饱食满也能吃（金苹果式）。
 * 营养溢出部分转为主动回血：溢出≤200 → 回升1×溢出秒；200-1000 → 回升2×(溢出-200)秒；
 * >1000 → 生命恢复5+力量5，各上限 10 分钟。
 */
public class CompressedFoodItem extends Item {
    private final int tier;
    private final int nutrition;

    public CompressedFoodItem(Item.Properties props, int tier, int nutrition) {
        super(props);
        this.tier = tier;
        this.nutrition = nutrition;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        int hungerBefore = entity instanceof Player player ? player.getFoodData().getFoodLevel() : 20;
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            int overflow = this.nutrition - (20 - hungerBefore);
            if (overflow > 0) {
                for (var effect : CompressedHooks.foodEffects(overflow)) {
                    player.addEffect(effect);
                }
            }
        }
        return result;
    }
}
