package dev.compressedblocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 作物种子与树苗的放置物品：土壤等级不符时拒绝放置并给出彩色英文提示（左下角聊天栏）。 */
public class CompressedSoilItem extends BlockItem {
    private final int level;
    private final boolean farm;
    private final String enName;
    private final int foodNutrition;

    public CompressedSoilItem(Block block, Item.Properties props, int level, boolean farm, String enName) {
        this(block, props, level, farm, enName, 0);
    }

    /** foodNutrition &gt; 0 时该物品同时是压缩食物（压缩胡萝卜/土豆）：随时可吃 + 溢出转回升。 */
    public CompressedSoilItem(Block block, Item.Properties props, int level, boolean farm, String enName, int foodNutrition) {
        super(block, props);
        this.level = level;
        this.farm = farm;
        this.enName = enName;
        this.foodNutrition = foodNutrition;
    }

    /** 种植所需土壤等级（盆栽交互用）。 */
    public int level() {
        return this.level;
    }

    /** 英文名（盆栽等级不足提示用）。 */
    public String enName() {
        return this.enName;
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        BlockState soil = ctx.getLevel().getBlockState(ctx.getClickedPos().below());
        Integer soilLevel = this.farm ? CompressedBlocks.farmlandLevel(soil) : CompressedBlocks.dirtLevel(soil);
        if (soilLevel == null || soilLevel < this.level) {
            if (!ctx.getLevel().isClientSide() && ctx.getPlayer() instanceof ServerPlayer player) {
                CompressedHooks.sendSoilHint(player, this.enName, this.level, this.farm, soilLevel);
            }
            return InteractionResult.FAIL;
        }
        return super.place(ctx);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (this.foodNutrition <= 0) {
            return super.finishUsingItem(stack, level, entity);
        }
        int hungerBefore = entity instanceof Player player ? player.getFoodData().getFoodLevel() : 20;
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            int overflow = this.foodNutrition - (20 - hungerBefore);
            if (overflow > 0) {
                for (var effect : CompressedHooks.foodEffects(overflow)) {
                    player.addEffect(effect);
                }
            }
        }
        return result;
    }
}
