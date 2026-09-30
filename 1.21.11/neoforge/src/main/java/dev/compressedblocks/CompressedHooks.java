package dev.compressedblocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 行为钩子（九重工具挖基岩、护甲抗性/免疫/饱食常满、食物溢出回血、英文彩色提示）。
 * 由 mixin 与各加载器桥调用，双加载器共用，不 import 任何加载器类。
 */
public final class CompressedHooks {
    private static final Set<Item> LEVEL9_TOOLS = new HashSet<>();
    /** 基岩挖掘进度：与原版钻石镐挖黑曜石一致（硬度 50、速度 8 → 约 9.4 秒）。 */
    private static final float BEDROCK_PROGRESS_PER_TICK = 1.0F / 187.5F;
    /** 英文提示防刷屏：每玩家 1 秒一条。 */
    private static final Map<UUID, Long> HINT_COOLDOWN = new HashMap<>();

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

    // ------------------------------------------------------------------ 护甲

    /**
     * 护甲每刻结算（服务端玩家 tick 调用）：
     * 溢出防御 → 抗性提升（等级=溢出总和）；四件全九重 → 饱食度与饱和度常满。
     */
    public static void armorTick(ServerPlayer player) {
        // 效果阶梯（任意穿戴件取最高重数；短时长刷新，摘下即过期，无粒子）：
        // 6 重=抗性1+夜视；7 重=抗性2+夜视；8 重=抗性3+夜视+水下呼吸+海底行者+飞行；
        // 9 重=抗性5+夜视+水下呼吸+海底行者+飞行+速度1+急迫2（不死=九重满套伤害事件，饱食常满）
        int max = CompressedBlocks.wornMaxLevel(player);
        if (max >= 6) {
            addEffect(player, MobEffects.RESISTANCE, CompressedBlocks.resistanceAmplifier(max));
            addEffect(player, MobEffects.NIGHT_VISION, 0);
        }
        if (max >= 8) {
            addEffect(player, MobEffects.WATER_BREATHING, 0);
            ensureDepthStrider(player);
        }
        if (max >= 9) {
            addEffect(player, MobEffects.SPEED, 0);
            addEffect(player, MobEffects.HASTE, 1);
        }
        // 飞行：满套（4 件）且每件 ≥8 重；疾跑时飞行速度提到鞘翅级
        boolean mayFly = CompressedBlocks.fullSetAtLeast(player, 8);
        var abilities = player.getAbilities();
        if (mayFly) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                player.onUpdateAbilities();
            }
            float target = player.isSprinting() ? 0.15F : 0.05F;
            if (Math.abs(abilities.getFlyingSpeed() - target) > 0.001F) {
                abilities.setFlyingSpeed(target);
                player.onUpdateAbilities();
            }
        } else if (abilities.mayfly && !abilities.instabuild) {
            // 脱下满套立刻收回飞行（含正在飞：强制落地）
            abilities.mayfly = false;
            abilities.flying = false;
            abilities.setFlyingSpeed(0.05F);
            player.onUpdateAbilities();
        }
        if (CompressedBlocks.hasFullLevel9Armor(player)) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
        }
    }

    private static void addEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, 100, amplifier, true, false));
    }

    /** 海底行者 3：注入到穿戴中的本模组靴子（不满 3 级才写）。 */
    private static void ensureDepthStrider(ServerPlayer player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty() || !CompressedBlocks.isCompressedBoots(boots)) {
            return;
        }
        ItemEnchantments cur = boots.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        Holder<Enchantment> depthStrider = player.level().registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.DEPTH_STRIDER);
        if (cur.getLevel(depthStrider) < 3) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(cur);
            mutable.set(depthStrider, 3);
            boots.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
    }

    /** 铁砧材料修理：本模组物品 1 个材料修 50%（原版 25%）。返回是否改写了结果。 */
    public static boolean halfMaterialRepair(ItemStack input, ItemStack material, ItemStack result) {
        if (input.isEmpty() || material.isEmpty() || result.isEmpty() || result.getItem() != input.getItem()) {
            return false;
        }
        var repairable = input.get(DataComponents.REPAIRABLE);
        if (repairable == null || !repairable.isValidRepairItem(material)) {
            return false;
        }
        int max = input.getMaxDamage();
        if (max <= 0 || input.getDamageValue() <= 0) {
            return false;
        }
        int count = Math.min(material.getCount(), 2);
        int newDamage = Math.max(0, input.getDamageValue() - max / 2 * count);
        if (result.getDamageValue() == newDamage) {
            return false;
        }
        result.setDamageValue(newDamage);
        return true;
    }

    /** 四件全九重：取消一切伤害（含 /kill、虚空）。 */
    public static boolean rejectDamage(Player player) {
        return CompressedBlocks.hasFullLevel9Armor(player);
    }

    // ------------------------------------------------------------------ 食物

    /** 食物营养溢出 → 回升（生命恢复）：≤200 回升1×溢出秒；>200 回升2×(溢出-200)秒；>1000 回升5×(溢出/2)秒。 */
    public static MobEffectInstance foodRegen(int overflow) {
        if (overflow > 1000) {
            return new MobEffectInstance(MobEffects.REGENERATION, (overflow / 2) * 20, 4);
        }
        if (overflow > 200) {
            return new MobEffectInstance(MobEffects.REGENERATION, (overflow - 200) * 20, 1);
        }
        return new MobEffectInstance(MobEffects.REGENERATION, overflow * 20, 0);
    }

    // ------------------------------------------------------------------ 英文彩色提示

    private static boolean hintReady(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = HINT_COOLDOWN.get(player.getUUID());
        if (last != null && now - last < 20) {
            return false;
        }
        HINT_COOLDOWN.put(player.getUUID(), now);
        return true;
    }

    /** 土壤等级不符（种植作物/树苗）。 */
    public static void sendSoilHint(ServerPlayer player, String enName, int level, boolean farm, Integer soilLevel) {
        if (!hintReady(player)) {
            return;
        }
        String soil = farm ? "Compressed Farmland" : "Compressed Dirt";
        String found = soilLevel == null ? "not compressed soil" : soilLevel + "x " + soil;
        player.displayClientMessage(Component.empty()
            .append(Component.literal("[Compressed Blocks] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(enName).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" needs ").withStyle(ChatFormatting.RED))
            .append(Component.literal(level + "x+ " + soil).withStyle(ChatFormatting.GOLD))
            .append(Component.literal("! Found: ").withStyle(ChatFormatting.RED))
            .append(Component.literal(found).withStyle(ChatFormatting.GRAY)), false);
    }

    /** 压缩甘蔗：下方需 N 重以上的压缩泥土或压缩沙子。 */
    public static void sendCaneHint(ServerPlayer player, String enName, int level, int soilLevel) {
        if (!hintReady(player)) {
            return;
        }
        String found = soilLevel < 0 ? "not compressed soil" : soilLevel + "x";
        player.displayClientMessage(Component.empty()
            .append(Component.literal("[Compressed Blocks] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(enName).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" needs ").withStyle(ChatFormatting.RED))
            .append(Component.literal(level + "x+ Compressed Dirt/Sand").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("! Found: ").withStyle(ChatFormatting.RED))
            .append(Component.literal(found).withStyle(ChatFormatting.GRAY)), false);
    }

    /** 锄头等级不足（锄压缩泥土）。 */
    public static void sendTillHint(ServerPlayer player, int hoeLevel, int dirtLevel) {
        if (!hintReady(player)) {
            return;
        }
        player.displayClientMessage(Component.empty()
            .append(Component.literal("[Compressed Blocks] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(dirtLevel + "x Compressed Dirt").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" needs a ").withStyle(ChatFormatting.RED))
            .append(Component.literal(dirtLevel + "x+ Compressed Hoe").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("! Your hoe: ").withStyle(ChatFormatting.RED))
            .append(Component.literal(hoeLevel + "x").withStyle(ChatFormatting.GRAY)), false);
    }

    private CompressedHooks() {
    }
}
