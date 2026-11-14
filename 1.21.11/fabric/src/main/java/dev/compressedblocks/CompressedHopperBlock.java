package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 压缩漏斗方块：继承原版 HopperBlock，具备 5 个朝向、红石锁定（ENABLED 属性）、比较器输出等完整物理与红石特性。
 * 1 重 / 2 重 / 3 重对应 16 / 64 / 576 物品吞吐量与 9 格槽位容量。
 */
public class CompressedHopperBlock extends HopperBlock {
    private final int tier;

    public CompressedHopperBlock(int tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return this.tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompressedHopperBlockEntity(pos, state, this.tier);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || CompressedBlocks.HOPPER_TYPE == null) {
            return null;
        }
        BlockEntityType<?> expected = CompressedBlocks.HOPPER_TYPE[this.tier - 1];
        if (type != expected) {
            return null;
        }
        return (lvl, p, st, be) -> CompressedHopperBlockEntity.pushItemsTick(lvl, p, st, (CompressedHopperBlockEntity) be);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MenuProvider menu ? menu : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider menu) {
            player.openMenu(menu);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CompressedHopperBlockEntity hopper) {
            Containers.dropContents(level, pos, hopper);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof Level lvl && !lvl.isClientSide() && lvl.getBlockEntity(pos) instanceof CompressedHopperBlockEntity hopper) {
            Containers.dropContents(lvl, pos, hopper);
        }
        super.destroy(level, pos, state);
    }
}
