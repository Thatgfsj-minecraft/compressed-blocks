package dev.compressedblocks.mixin;

import dev.compressedblocks.CompressedHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 基岩补掉落：基岩标了 noLootTable，正常破坏路径永远不出物品。
 * 在 ServerPlayerGameMode.destroyBlock 成功返回后，若挖的是基岩且手持九重压缩工具
 * （生存模式），手动弹出一个基岩物品。
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    private BlockState compressedblocks$capturedState;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void compressedblocks$captureState(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        this.compressedblocks$capturedState = this.level.getBlockState(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void compressedblocks$bedrockDrop(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
            && this.compressedblocks$capturedState != null
            && CompressedHooks.bedrockDrop(this.compressedblocks$capturedState, this.player)) {
            CompressedHooks.popBedrock(this.level, pos);
        }
    }
}
