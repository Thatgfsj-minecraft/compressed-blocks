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
 * 木质漏斗方块：
 * tier 0 为基础木质漏斗（速度为原版漏斗的一半），
 * tier 1~3 为一至三重压缩木质漏斗（速度分别为对应压缩铁漏斗的一半）。
 */
public class WoodenHopperBlock extends HopperBlock {
    private final int tier; // 0=base, 1=1x, 2=2x, 3=3x

    public WoodenHopperBlock(int tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return this.tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WoodenHopperBlockEntity(pos, state, this.tier);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || CompressedBlocks.WOODEN_HOPPER_TYPE == null) {
            return null;
        }
        BlockEntityType<?> expected = CompressedBlocks.WOODEN_HOPPER_TYPE[this.tier];
        if (type != expected) {
            return null;
        }
        return (lvl, p, st, be) -> WoodenHopperBlockEntity.pushItemsTick(lvl, p, st, (WoodenHopperBlockEntity) be);
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WoodenHopperBlockEntity hopper) {
            Containers.dropContents(level, pos, hopper);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof Level lvl && !lvl.isClientSide() && lvl.getBlockEntity(pos) instanceof WoodenHopperBlockEntity hopper) {
            Containers.dropContents(lvl, pos, hopper);
        }
        super.destroy(level, pos, state);
    }
}
