package dev.compressedblocks.mixin;

import dev.compressedblocks.CompressedHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 基岩挖掘门：原版 BlockBehaviour.getDestroyProgress 对硬度 -1（基岩）无条件返回 0，
 * 发生在一切玩家速度计算之前，事件无法触及，只能注入。
 * 持有九重压缩工具时改写为固定进度（≈ 钻石镐挖黑曜石的手感）。
 */
@Mixin(BlockBehaviour.class)
public class BlockBehaviourMixin {

    @Inject(
        method = "getDestroyProgress(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
        at = @At("HEAD"),
        cancellable = true
    )
    private void compressedblocks$bedrockDigProgress(BlockState state, Player player, BlockGetter level,
                                                     BlockPos pos, CallbackInfoReturnable<Float> cir) {
        float f = CompressedHooks.bedrockDigProgress(state, player);
        if (f > 0.0F) {
            cir.setReturnValue(f);
        }
    }
}
