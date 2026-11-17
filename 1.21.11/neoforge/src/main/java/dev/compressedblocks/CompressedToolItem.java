package dev.compressedblocks;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 压缩工具（镐）的基础实现。
 *
 * <p>原版 {@link net.minecraft.world.item.ToolMaterial#applyToolProperties} 只给工具挂两条
 * {@code minecraft:tool} 规则：{@code deniesDrops(incorrectBlocksForDrops)} 与
 * {@code minesAndDrops(本工具类别的 #mineable/<type>, tier speed)}。{@code Tool#getMiningSpeed}
 * 只认「本类别标签命中」的速度规则，其余方块一律回落到 {@code defaultMiningSpeed = 1.0}，
 * 于是压缩镐挖木质半砖/楼梯/木板/原木时只有空手速度（与原版同名工具完全一样）。
 *
 * <p>这里把挖掘速度下限抬到工具自身速度：**任何方块**至少按该档速度挖。
 * {@code minecraft:tool} 组件规则一个字都不改，所以掉落与挖掘等级仍是原版语义
 * （只有正确工具 + 足够等级才掉落）；对「非正确工具」的方块，
 * {@code BlockBehaviour#getDestroyProgress} 仍会用 100 而非 30 作除数（慢 3.33 倍），不会失衡。
 */
public class CompressedToolItem extends Item {
    private final float miningSpeed;

    public CompressedToolItem(Item.Properties properties, float miningSpeed) {
        super(properties);
        this.miningSpeed = miningSpeed;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return Math.max(super.getDestroySpeed(stack, state), this.miningSpeed);
    }
}
