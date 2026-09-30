package dev.compressedblocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 压缩甘蔗放置物品：下方需 N 重以上压缩泥土/沙子，否则拒绝并给出彩色英文提示。 */
public class CompressedCaneItem extends BlockItem {
    private final int level;
    private final String enName;

    public CompressedCaneItem(Block block, Item.Properties props, int level, String enName) {
        super(block, props);
        this.level = level;
        this.enName = enName;
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        BlockState soil = ctx.getLevel().getBlockState(ctx.getClickedPos().below());
        if (!(soil.getBlock() instanceof CompressedCaneBlock)) {
            Integer dirt = CompressedBlocks.dirtLevel(soil);
            Integer sand = CompressedBlocks.sandLevel(soil);
            int soilLevel = dirt != null ? dirt : (sand != null ? sand : -1);
            if (soilLevel < this.level) {
                if (!ctx.getLevel().isClientSide() && ctx.getPlayer() instanceof ServerPlayer player) {
                    CompressedHooks.sendCaneHint(player, this.enName, this.level, soilLevel);
                }
                return InteractionResult.FAIL;
            }
        }
        return super.place(ctx);
    }
}
