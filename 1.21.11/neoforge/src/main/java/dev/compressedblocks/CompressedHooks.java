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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    /** 常规效果时长：5 秒短刷新（摘下即过期，无粒子）。 */
    private static final int DURATION = 100;
    /** 夜视时长 20 秒：低于 10 秒原版会闪黑屏警告，必须给长。 */
    private static final int NIGHT_VISION_DURATION = 400;
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
     * 护甲每刻结算（服务端玩家 tick 调用）。0.3.1 分部位效果——不再向装备注入任何附魔，
     * 不干扰原版附魔（海底行者/速冻已删除）：
     * 头盔 ≥6 重：水下呼吸+夜视；护腿 ≥6 重：抗性 1/2/3/5（按重数）+ 9 重急迫2；
     * 胸甲 ≥8 重：生存飞行（疾跑=鞘翅速度）；靴子：无。
     * 木质盔甲按有效重数（重数-1）自动降档。不死+饱食度常满：仅九重石甲满套。
     */
    public static void armorTick(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = CompressedBlocks.armorInfoOf(player.getItemBySlot(slot));
            if (info == null) {
                continue;
            }
            int level = info[1];
            switch (info[0]) {
                case 0 -> {
                    if (level >= 6) {
                        addEffect(player, MobEffects.WATER_BREATHING, 0, DURATION);
                        // 夜视给 20 秒：原版剩余 <10 秒会闪黑屏，短时长刷新观感极差
                        addEffect(player, MobEffects.NIGHT_VISION, 0, NIGHT_VISION_DURATION);
                    }
                }
                case 2 -> {
                    int amp = CompressedBlocks.resistanceAmplifier(level);
                    if (amp >= 0) {
                        addEffect(player, MobEffects.RESISTANCE, amp, DURATION);
                    }
                    if (level >= 9) {
                        addEffect(player, MobEffects.HASTE, 1, DURATION);
                    }
                }
                default -> {
                }
            }
        }
        // 飞行：穿戴 ≥8 重（有效重数）胸甲；疾跑时飞行速度提到鞘翅级
        int[] chest = CompressedBlocks.armorInfoOf(player.getItemBySlot(EquipmentSlot.CHEST));
        boolean mayFly = chest != null && chest[0] == 1 && chest[1] >= 8;
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
            // 脱下胸甲立刻收回飞行（含正在飞：强制落地）
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

    private static void addEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int duration) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false));
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

    /** 盆栽土壤等级不足（作物/树苗/甘蔗重数 > 土壤重数）。 */
    public static void sendPotHint(ServerPlayer player, String enName, int level, int soilLevel) {
        if (!hintReady(player)) {
            return;
        }
        player.displayClientMessage(Component.empty()
            .append(Component.literal("[Compressed Blocks] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(enName).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" needs ").withStyle(ChatFormatting.RED))
            .append(Component.literal(level + "x+ Compressed Dirt/Sand in the pot").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("! Pot soil: ").withStyle(ChatFormatting.RED))
            .append(Component.literal(soilLevel + "x").withStyle(ChatFormatting.GRAY)), false);
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
