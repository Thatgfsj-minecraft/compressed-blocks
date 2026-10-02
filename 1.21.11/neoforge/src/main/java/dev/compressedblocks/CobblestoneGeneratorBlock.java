package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 刷石机（3 个压缩等级）：每秒产出 1 个对应等级的原石/压缩原石，压入下方容器；
 * 下方无容器时暂存在机器内（一组上限），满了就停滞。
 * 机器外形内缩 1px（非完整实心方块，下方容器露出可点）；右键直开下方容器。
 */
public class CobblestoneGeneratorBlock extends Block implements EntityBlock {
    /** 16³ 内缩 1px：不再把下方箱子整个挡在点击盲区里。 */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    private final int tier;

    public CobblestoneGeneratorBlock(int tier, Properties props) {
        super(props);
        this.tier = tier;
    }

    /** 压缩等级 1-3：产物分别为原石 / 一重压缩原石 / 二重压缩原石。 */
    public int tier() {
        return this.tier;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** 右键直接打开下方容器；潜行右键保留给放置/拆除类操作，无容器时放行原版行为。 */
    private InteractionResult openBelow(Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown() || !(level.getBlockEntity(pos.below()) instanceof MenuProvider menu)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(menu);
        return InteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = openBelow(level, pos, player);
        return result != InteractionResult.PASS ? result
            : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        InteractionResult result = openBelow(level, pos, player);
        return result != InteractionResult.PASS ? result
            : super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CobblestoneGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != CompressedBlocks.GENERATOR_TYPE) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<CobblestoneGeneratorBlockEntity>)
            CobblestoneGeneratorBlockEntity::serverTick;
    }
}
