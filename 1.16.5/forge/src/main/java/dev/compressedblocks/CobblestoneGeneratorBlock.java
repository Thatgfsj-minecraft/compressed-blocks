package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * 刷石机（3 个压缩等级）：每秒产出 1 个对应等级的原石/压缩原石，压入周围容器；
 * 无容器时暂存机器内（一组上限），满了就停滞。
 * 机器外形内缩 1px（下方容器露出可点）；右键直开下方容器。
 */
public class CobblestoneGeneratorBlock extends Block implements EntityBlock {
    /** 16³ 内缩 1px：不再把下方容器整个挡在点击盲区里。 */
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** 邻居方块更新：重扫输出方向（下/上/北/南/西/东优先级，与放置顺序无关）。 */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos fromPos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof CobblestoneGeneratorBlockEntity) {
            ((CobblestoneGeneratorBlockEntity) level.getBlockEntity(pos)).rescanOutput();
        }
    }

    /** 右键直接打开下方容器；潜行右键保留给放置类操作，无容器时放行原版行为。 */
    private InteractionResult openBelow(Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown() || !(level.getBlockEntity(pos.below()) instanceof net.minecraft.world.MenuProvider)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer) {
            net.minecraftforge.fml.network.NetworkHooks.openGui(
                (net.minecraft.server.level.ServerPlayer) player,
                (net.minecraft.world.MenuProvider) level.getBlockEntity(pos.below()), pos.below());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = openBelow(level, pos, player);
        return result != InteractionResult.PASS ? result
            : super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public BlockEntity newBlockEntity(net.minecraft.world.level.BlockGetter reader) {
        return new CobblestoneGeneratorBlockEntity();
    }
}
