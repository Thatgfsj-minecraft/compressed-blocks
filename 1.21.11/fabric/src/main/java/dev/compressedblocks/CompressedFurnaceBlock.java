package dev.compressedblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 四阶压缩熔炉方块：
 * 1重（1x）：3x 速度，权限原版 + 1~3 重；
 * 2重（2x）：9x 速度，权限原版 + 1~6 重；
 * 3重（3x）：27x 速度，权限原版 + 1~9 重；
 * 4重（4x）：81x 极致速度，全通烧炼。
 */
public class CompressedFurnaceBlock extends AbstractFurnaceBlock {
    public static final MapCodec<CompressedFurnaceBlock> CODEC = simpleCodec(props -> new CompressedFurnaceBlock(1, props));

    private final int tier;

    public CompressedFurnaceBlock(int tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return this.tier;
    }

    @Override
    protected MapCodec<? extends AbstractFurnaceBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompressedFurnaceBlockEntity(pos, state, this.tier);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || CompressedBlocks.FURNACE_TYPE == null) {
            return null;
        }
        BlockEntityType<?> expected = CompressedBlocks.FURNACE_TYPE[this.tier - 1];
        if (type != expected) {
            return null;
        }
        return (lvl, p, st, be) -> CompressedFurnaceBlockEntity.serverTick((ServerLevel) lvl, p, st, (CompressedFurnaceBlockEntity) be);
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MenuProvider menu) {
            player.openMenu(menu);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        Direction direction = state.getValue(FACING);
        Direction.Axis axis = direction.getAxis();
        double horizontalOffset = random.nextDouble() * 0.6 - 0.3;
        double dx = axis == Direction.Axis.X ? direction.getStepX() * 0.52 : horizontalOffset;
        double dy = random.nextDouble() * 6.0 / 16.0;
        double dz = axis == Direction.Axis.Z ? direction.getStepZ() * 0.52 : horizontalOffset;
        level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
        level.addParticle(ParticleTypes.FLAME, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
    }
}
