package dev.compressedblocks.forge;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;

import java.lang.reflect.Method;

/**
 * 方块构造期反射访问器（1.12.2 专用）。
 * 原因：1.12.2 的 Block.setSoundType 是 protected，且原生 Block 无法子类化逐个暴露；
 * 也不引入 access transformer（RFG 旧版管线的 AT 文件名/时机不确定性高）。
 * 仅初始化期调用，无热路径性能影响。
 */
public final class BlockAccessors {
    private static Method setSoundTypeMethod;

    static {
        try {
            setSoundTypeMethod = Block.class.getDeclaredMethod("setSoundType", SoundType.class);
            setSoundTypeMethod.setAccessible(true);
        } catch (Throwable t) {
            throw new IllegalStateException("1.12.2 Block.setSoundType 反射绑定失败", t);
        }
    }

    /** 外部设置方块音效（等价 protected setSoundType）。 */
    public static Block sound(Block block, SoundType sound) {
        try {
            setSoundTypeMethod.invoke(block, sound);
        } catch (Throwable t) {
            throw new IllegalStateException("setSoundType 调用失败: " + block.getRegistryName(), t);
        }
        return block;
    }

    private BlockAccessors() {
    }
}
