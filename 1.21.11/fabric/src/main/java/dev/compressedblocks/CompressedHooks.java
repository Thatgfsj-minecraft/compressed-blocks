package dev.compressedblocks;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 原版行为钩子的最小实现（九重压缩工具：可挖基岩、挖掉会掉基岩）。由 mixin 调用，双加载器共用。 */
public final class CompressedHooks {
    private static final Set<Item> LEVEL9_TOOLS = new HashSet<>();
    /** 基岩挖掘进度：与原版钻石镐挖黑曜石一致（硬度 50、速度 8 → 约 9.4 秒）。 */
    private static final float BEDROCK_PROGRESS_PER_TICK = 1.0F / 187.5F;

    public static void registerLevel9Tool(Item item) {
        LEVEL9_TOOLS.add(item);
    }

    /** 返回 0 表示不接管原版逻辑；>0 为本次调用的破坏进度。 */
    public static float bedrockDigProgress(BlockState state, Player player) {
        if (!state.is(Blocks.BEDROCK) || player == null) {
            return 0.0F;
        }
        ItemStack held = player.getMainHandItem();
        return LEVEL9_TOOLS.contains(held.getItem()) ? BEDROCK_PROGRESS_PER_TICK : 0.0F;
    }

    /** 基岩无战利品表（noLootTable），破坏成功后在这里补掉落；仅生存模式。 */
    public static boolean bedrockDrop(BlockState state, ServerPlayer player) {
        return state.is(Blocks.BEDROCK)
            && !player.isCreative()
            && LEVEL9_TOOLS.contains(player.getMainHandItem().getItem());
    }

    public static void popBedrock(ServerLevel level, BlockPos pos) {
        Block.popResource(level, pos, new ItemStack(Items.BEDROCK));
    }

    private CompressedHooks() {
    }
}
