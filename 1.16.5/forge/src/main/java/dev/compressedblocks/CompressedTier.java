package dev.compressedblocks;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * 压缩工具 Tier（每 line×level×tool 一个实例：1.21 的 bonus 公式依赖各工具基础伤害）。
 * 挖掘档位阶梯：7 重=铁(1)、8 重=钻石(2)、9 重=下界合金(3)；六重起耐久 MAX_VALUE（不可破坏）。
 */
public final class CompressedTier implements net.minecraft.world.item.Tier {
    private final int level;
    private final CompressedBlocks.ToolKind kind;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantability;
    private final Supplier<ItemStack> repair;

    public CompressedTier(int level, CompressedBlocks.ToolKind kind, float speed, float attackDamageBonus,
                          int enchantability, Supplier<ItemStack> repair) {
        this.level = level;
        this.kind = kind;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantability = enchantability;
        this.repair = repair;
    }

    @Override
    public int getUses() {
        return CompressedBlocks.durabilityFor(this.level, this.kind);
    }

    @Override
    public float getSpeed() {
        return this.speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return this.attackDamageBonus;
    }

    @Override
    public int getLevel() {
        return CompressedBlocks.tierLevelFor(this.level);
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantability;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(this.repair.get());
    }
}
