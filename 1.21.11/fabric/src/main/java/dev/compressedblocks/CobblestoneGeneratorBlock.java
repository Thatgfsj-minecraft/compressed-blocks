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
import net.minecraft.world.level.redstone.Orientation;

/**
 * 刷石机：每秒产出 1 个对应压缩等级的产物，压入下方容器；
 * 下方无容器时暂存在机器内（一组上限），满了就停滞。
 * 机器外形内缩 1px（非完整实心方块，下方容器露出可点）；右键直开下方容器。
 *
 * <p>tier = 产物压缩等级：0 = 原石，1..9 = 1x..9x 压缩圆石（主 mod 三档为 0/1/2）。
 * 本类可由附属 mod 继承：子类覆盖 {@link #newBlockEntity} 与 {@link #getTicker}
 * 接入自己的方块实体类型，并复用 {@link #generatorTicker(BlockEntityType, Level)}。
 */
public class CobblestoneGeneratorBlock extends Block implements EntityBlock {
    /** 16³ 内缩 1px：不再把下方箱子整个挡在点击盲区里。 */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    private final int tier;

    public CobblestoneGeneratorBlock(int tier, Properties props) {
        super(props);
        this.tier = tier;
    }

    /** 产物压缩等级：0 = 原石，1..9 = 1x..9x 压缩圆石。 */
    public int tier() {
        return this.tier;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** 邻居方块更新：重扫输出方向（下/上/北/南/西/东优先级，与放置顺序无关）。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   Orientation orientation, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof CobblestoneGeneratorBlockEntity be) {
            be.rescanOutput();
        }
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

    /**
     * public 且可覆盖：附属 mod 子类返回自己的方块实体实例
     * （用 {@code CobblestoneGeneratorBlockEntity(BlockEntityType, BlockPos, BlockState)} 构造）。
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CobblestoneGeneratorBlockEntity(pos, state);
    }

    /**
     * 刷石机 ticker 工厂：客户端或类型尚未就绪（附属 mod 注册早期）返回 null，
     * 否则返回驱动 {@link CobblestoneGeneratorBlockEntity#serverTick} 的通用 ticker。
     * 附属 mod 子类可复用本方法接入自己的方块实体类型。
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityTicker<T> generatorTicker(BlockEntityType<T> expected,
                                                                               Level level) {
        if (level.isClientSide() || expected == null) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<CobblestoneGeneratorBlockEntity>)
            CobblestoneGeneratorBlockEntity::serverTick;
    }

    /**
     * 主 mod 的 BE 类型返回 ticker，其余类型返回 null；
     * 附属 mod 子类覆盖本方法，用自己的类型调用 {@link #generatorTicker(BlockEntityType, Level)}。
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != CompressedBlocks.GENERATOR_TYPE) {
            return null;
        }
        return generatorTicker(type, level);
    }
}
