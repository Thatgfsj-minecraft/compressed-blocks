package dev.compressedblocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 压缩锄：锄 N 级压缩泥土需要 N 级或以上的压缩锄头；等级不足给出彩色英文提示。
 * 原版锄地不受影响。1.12.2 钩子 = onItemUse（1.16 是 useOn）。
 */
public class CompressedHoeItem extends ItemHoe {
    private final int level;
    private final Item repair;

    public CompressedHoeItem(net.minecraft.item.Item.ToolMaterial material, int level, Item repair) {
        super(material);
        this.level = level;
        this.repair = repair;
    }

    public int level() {
        return this.level;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == this.repair;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        Integer dirtLevel = CompressedBlocks.dirtLevel(state);
        if (dirtLevel != null) {
            if (this.level >= dirtLevel) {
                BlockPos above = pos.up();
                net.minecraft.block.state.IBlockState aboveState = world.getBlockState(above);
                if (facing != EnumFacing.DOWN && aboveState.getMaterial() == net.minecraft.block.material.Material.AIR) {
                    world.playSound(player, pos, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    if (!world.isRemote) {
                        world.setBlockState(pos, CompressedBlocks.farmland(dirtLevel).getDefaultState(), 11);
                        stack.damageItem(1, player);
                    }
                    return EnumActionResult.SUCCESS;
                }
                return EnumActionResult.PASS;
            }
            if (!world.isRemote) {
                CompressedHooks.sendTillHint(player, this.level, dirtLevel);
            }
            return EnumActionResult.PASS;
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }
}
