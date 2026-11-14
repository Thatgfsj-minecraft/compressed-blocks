package dev.compressedblocks.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 放宽 ServerboundContainerClickPacket 的 changedSlots 最大槽位变动网络包限制。
 * 原版为 128，在 243 格滚动大容器或大堆叠模组下一次点击变动超过 128 槽位时会导致
 * io.netty.handler.codec.EncoderException: X elements exceeded max size of: 128
 * 放宽到 65536，确保大容量容器交互绝对稳定不掉线。
 */
@Mixin(ServerboundContainerClickPacket.class)
public class ServerboundContainerClickPacketMixin {

    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 128))
    private static int compressedblocks$expandMaxSlotCount(int original) {
        return 65536;
    }
}
