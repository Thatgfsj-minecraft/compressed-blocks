package dev.compressedblocks;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * 压缩护甲材质（石制/木质各 9 级）：石制基准=铁级防御；六重起耐久 MAX（不可破坏）。
 * getName 返回 namespace:路径 形式，1.16.5 盔甲层贴图走 textures/models/armor/<name>_layer_N.png。
 */
public final class CompressedArmorMaterial implements ArmorMaterial {
    private static final int[] BASE_DURABILITY = {11, 16, 15, 13};
    private final String name;
    private final int level;
    private final int multiplier;
    private final SoundEvent equipSound;
    private final Supplier<ItemStack> repair;

    public CompressedArmorMaterial(String name, int level, int multiplier, SoundEvent equipSound,
                                   Supplier<ItemStack> repair) {
        this.name = name;
        this.level = level;
        this.multiplier = multiplier;
        this.equipSound = equipSound;
        this.repair = repair;
    }

    @Override
    public int getDurabilityForSlot(EquipmentSlot slot) {
        long mult = this.level >= CompressedBlocks.UNBREAKABLE_FROM
            ? Integer.MAX_VALUE / 16L
            : this.multiplier;
        return (int) (BASE_DURABILITY[slot.getIndex()] * mult);
    }

    @Override
    public int getDefenseForSlot(EquipmentSlot slot) {
        return CompressedBlocks.armorDefense(this.level, slot.getIndex());
    }

    @Override
    public int getEnchantmentValue() {
        return CompressedBlocks.armorEnchantability();
    }

    @Override
    public SoundEvent getEquipSound() {
        return this.equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(this.repair.get());
    }

    @Override
    public String getName() {
        return CompressedBlocks.MOD_ID + ":" + this.name;
    }

    @Override
    public float getToughness() {
        return 0.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
