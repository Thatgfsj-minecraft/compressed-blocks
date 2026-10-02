package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 压缩箱子（单一等级）：243 格滚动存储。不能拼成大箱子（无合并逻辑，右键开自己的菜单）；
 * 有朝向（贴脸放置，正面带锁扣）；破坏时内容洒出（BE preRemoveSideEffects）。
 */
public class CompressedChestBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public CompressedChestBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScrollingContainerBlockEntity(CompressedBlocks.CHEST_TYPE, pos, state, false);
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
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
                                          Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        // 潜行右键保留给贴着放方块；否则打开容器
        if (player.isShiftKeyDown()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return this.useWithoutItem(state, level, pos, player, hit);
    }
}
