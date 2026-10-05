package dev.compressedblocks;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 作物种子与树苗的放置物品：土壤等级不符时拒绝放置并给出彩色英文提示。
 * 1.12.2 拦截点 = onItemUse（放置目标 = 点击面偏移格，与 1.16 的 BlockPlaceContext 等价）。
 * foodNutrition > 0 时同时是压缩食物（压缩胡萝卜/土豆）：原版进食链 + 溢出转回升。
 */
public class CompressedSoilItem extends ItemBlock {
    private final int level;
    private final boolean farm;
    private final String enName;
    private final int foodNutrition;
    private final float foodSaturation;

    public CompressedSoilItem(net.minecraft.block.Block block, int level, boolean farm, String enName) {
        this(block, level, farm, enName, 0, 0.0F);
    }

    public CompressedSoilItem(net.minecraft.block.Block block, int level, boolean farm, String enName,
                              int foodNutrition, float foodSaturation) {
        super(block);
        this.level = level;
        this.farm = farm;
        this.enName = enName;
        this.foodNutrition = foodNutrition;
        this.foodSaturation = foodSaturation;
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
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        BlockPos target = pos.offset(facing);
        net.minecraft.block.state.IBlockState soil = world.getBlockState(target.down());
        Integer soilLevel = this.farm ? CompressedBlocks.farmlandLevel(soil)
            : CompressedBlocks.dirtLevel(soil);
        if (soilLevel == null || soilLevel < this.level) {
            if (!world.isRemote && !player.capabilities.isCreativeMode) {
                // 提示只对真实玩家且非创造撒放时发（创造省事）
                if (world.getMinecraftServer() != null) {
                    CompressedHooks.sendSoilHint(player, this.enName, this.level, this.farm, soilLevel);
                }
            } else if (!world.isRemote) {
                CompressedHooks.sendSoilHint(player, this.enName, this.level, this.farm, soilLevel);
            }
            return EnumActionResult.FAIL;
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    // ---- 压缩食物职能（胡萝卜/土豆）：照 ItemFood 的进食链复刻

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase entity) {
        if (this.foodNutrition <= 0) {
            return stack;
        }
        int hungerBefore = entity instanceof EntityPlayer
            ? ((EntityPlayer) entity).getFoodStats().getFoodLevel() : 20;
        stack.shrink(1);
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            player.getFoodStats().addStats(this.foodNutrition, this.foodSaturation);
            if (!world.isRemote) {
                int overflow = this.foodNutrition - (20 - hungerBefore);
                if (overflow > 0) {
                    for (PotionEffect effect : CompressedHooks.foodEffects(overflow)) {
                        player.addPotionEffect(effect);
                    }
                }
            }
        } else {
            // 非玩家生物不吃（原版 ItemFood 同款忽略）
            return stack;
        }
        world.playSound(null, entity.posX, entity.posY, entity.posZ,
            net.minecraft.init.SoundEvents.ENTITY_GENERIC_EAT,
            net.minecraft.util.SoundCategory.PLAYERS, 0.5F,
            world.rand.nextFloat() * 0.1F + 0.9F);
        return stack;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return this.foodNutrition > 0 ? 32 : 0;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return this.foodNutrition > 0 ? EnumAction.EAT : EnumAction.NONE;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (this.foodNutrition > 0 && player.canEat(false)) {
            player.setActiveHand(hand);
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
    }
}
