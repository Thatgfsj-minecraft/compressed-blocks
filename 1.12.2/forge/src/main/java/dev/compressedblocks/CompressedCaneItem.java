package dev.compressedblocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 压缩甘蔗放置物品：下方需 N 重以上的压缩泥土或压缩沙子，否则拒绝并给出彩色英文提示。
 * 1.12.2：甘蔗作物的存活判定在 CompressedCaneBlock.canBlockStay；物品层只做提前拦截提示。
 */
public class CompressedCaneItem extends ItemBlock {
    private final int level;
    private final String enName;

    public CompressedCaneItem(net.minecraft.block.Block block, int level, String enName) {
        super(block);
        this.level = level;
        this.enName = enName;
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
        if (!(soil.getBlock() instanceof CompressedCaneBlock)) {
            Integer dirt = CompressedBlocks.dirtLevel(soil);
            Integer sand = CompressedBlocks.sandLevel(soil);
            int soilLevel = dirt != null ? dirt : (sand != null ? sand : -1);
            if (soilLevel < this.level) {
                if (!world.isRemote) {
                    CompressedHooks.sendCaneHint(player, this.enName, this.level, soilLevel);
                }
                return EnumActionResult.FAIL;
            }
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }
}
