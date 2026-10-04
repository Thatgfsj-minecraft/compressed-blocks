package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 压缩锄：锄 N 级压缩泥土需要 N 级或以上的压缩锄头；等级不足给出彩色英文提示。原版锄地不受影响。 */
public class CompressedHoeItem extends HoeItem {
    private final int level;

    public CompressedHoeItem(Tier tier, int attackDamage, float attackSpeed, int level,
                             Properties props) {
        super(tier, attackDamage, attackSpeed, props);
        this.level = level;
    }

    public int level() {
        return this.level;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Integer dirtLevel = CompressedBlocks.dirtLevel(state);
        if (dirtLevel != null) {
            Player player = ctx.getPlayer();
            if (this.level >= dirtLevel) {
                if (ctx.getClickedFace() != Direction.DOWN && level.getBlockState(pos.above()).isAir()) {
                    level.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    if (!level.isClientSide) {
                        level.setBlock(pos, CompressedBlocks.farmland(dirtLevel).defaultBlockState(), 11);
                        if (player != null) {
                            ctx.getItemInHand().hurtAndBreak(1, player,
                                p -> p.broadcastBreakEvent(ctx.getHand()));
                        }
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                return InteractionResult.PASS;
            }
            if (!level.isClientSide && player instanceof ServerPlayer) {
                CompressedHooks.sendTillHint((ServerPlayer) player, this.level, dirtLevel);
            }
            return InteractionResult.PASS;
        }
        return super.useOn(ctx);
    }
}
