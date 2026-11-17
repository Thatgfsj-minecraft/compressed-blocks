package dev.compressedblocks;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 压缩锹：保留原版锹的铲平/铺路行为，同时叠加跨类别速度下限
 * （任意方块至少按本档速度挖；掉落与等级仍由原版 {@code minecraft:tool} 规则判定）。
 */
public class CompressedShovelItem extends ShovelItem {
    private final float miningSpeed;

    public CompressedShovelItem(ToolMaterial material, float attackDamage, float attackSpeed,
                                float miningSpeed, Item.Properties properties) {
        super(material, attackDamage, attackSpeed, properties);
        this.miningSpeed = miningSpeed;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return Math.max(super.getDestroySpeed(stack, state), this.miningSpeed);
    }
}
