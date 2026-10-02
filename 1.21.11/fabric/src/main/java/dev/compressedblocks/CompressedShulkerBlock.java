package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 压缩潜影盒（单一等级）：243 格滚动存储，无朝向整体方块。
 * 破坏时内容随物品保留（copy_components 战利品表，与原版潜影盒同机制）。
 */
public class CompressedShulkerBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public CompressedShulkerBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScrollingContainerBlockEntity(CompressedBlocks.SHULKER_TYPE, pos, state, true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MenuProvider menu) {
            player.openMenu(menu);
            return InteractionResult.CONSUME;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return this.useWithoutItem(state, level, pos, player, hit);
    }
}
