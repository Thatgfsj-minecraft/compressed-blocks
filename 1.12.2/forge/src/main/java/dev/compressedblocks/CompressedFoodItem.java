package dev.compressedblocks;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

/**
 * 压缩食物（18 种 × 3 级封顶）：canAlwaysEat，饱食满也能吃（金苹果式）。
 * 营养溢出部分转为主动回血：≤200 → 回升 1×溢出秒；200-1000 → 回升 2×(溢出-200)秒；
 * >1000 → 生命恢复5 + 力量5，各上限 10 分钟。
 * 1.12.2 进食完成钩子 = onItemUseFinish/onFoodEaten（无 LivingEntity.useFinish）。
 */
public class CompressedFoodItem extends ItemFood {
    private final int nutrition;

    public CompressedFoodItem(int nutrition, float saturation) {
        // 1.12.2 构造（amount, saturation, isWolfFood）；alwaysEdible = 饱食满也能吃
        super(clamp(nutrition), saturation, false);
        this.setAlwaysEdible();
        this.nutrition = nutrition;
    }

    private static int clamp(int nutrition) {
        return Math.max(1, Math.min(nutrition, Integer.MAX_VALUE / 4));
    }

    @Override
    protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            int overflow = this.nutrition - (20 - player.getFoodStats().getFoodLevel());
            if (overflow > 0) {
                for (PotionEffect effect : CompressedHooks.foodEffects(overflow)) {
                    player.addPotionEffect(effect);
                }
            }
        }
    }
}
