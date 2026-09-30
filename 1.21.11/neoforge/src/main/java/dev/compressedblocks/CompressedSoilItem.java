package dev.compressedblocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 作物种子与树苗的放置物品：土壤等级不符时拒绝放置并给出彩色英文提示（左下角聊天栏）。 */
public class CompressedSoilItem extends BlockItem {
    private final int level;
    private final boolean farm;
    private final String enName;

    public CompressedSoilItem(Block block, Item.Properties props, int level, boolean farm, String enName) {
        super(block, props);
        this.level = level;
        this.farm = farm;
        this.enName = enName;
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
}
