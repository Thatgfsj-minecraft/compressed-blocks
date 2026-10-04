package dev.compressedblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 行为钩子（护甲抗性/免疫/饱食常满、食物溢出回血、英文彩色提示）。
 * 由 Forge 事件桥调用。1.16.5 降级说明：九重工具挖基岩需要 mixin（破坏进度客户端插值），
 * 本次移植不实现（见 backlog）。
 */
public final class CompressedHooks {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    /** 常规效果时长：5 秒短刷新（摘下即过期，无粒子）。 */
    private static final int DURATION = 100;
    /** 夜视时长 20 秒：低于 10 秒原版会闪黑屏警告，必须给长。 */
    private static final int NIGHT_VISION_DURATION = 400;
    /** 英文提示防刷屏：每玩家 1 秒一条。 */
    private static final Map<UUID, Long> HINT_COOLDOWN = new HashMap<>();

    /** 注册九重工具（1.16.5 无基岩挖掘钩子，保留注册位以对齐 1.21 结构）。 */
    public static void registerLevel9Tool(Item item) {
    }

    // ------------------------------------------------------------------ 护甲

    /**
     * 护甲每刻结算（服务端玩家 tick 调用）。分部位效果：
     * 头盔 7 重=夜视、8 重=+水下呼吸；护腿 7/8/9 重=抗性 1/2/3、9 重=+急迫2；
     * 胸甲 8 重=生存飞行（疾跑=鞘翅速度）；靴子：无。
     * 木质盔甲按有效重数（重数-1）自动降档。饱食度常满：仅九重石甲满套。
     */
    public static void armorTick(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = CompressedBlocks.armorInfoOf(player.getItemBySlot(slot));
            if (info == null) {
                continue;
            }
            int level = info[1];
            if (info[0] == 0) {
                // 头盔：7 重=夜视（20 秒防黑屏闪）、8 重=+水下呼吸
                if (level >= 7) {
                    addEffect(player, MobEffects.NIGHT_VISION, 0, NIGHT_VISION_DURATION);
                    if (level >= 8) {
                        addEffect(player, MobEffects.WATER_BREATHING, 0, DURATION);
                    }
                }
            } else if (info[0] == 2) {
                int amp = CompressedBlocks.resistanceAmplifier(level);
                if (amp >= 0) {
                    addEffect(player, MobEffects.DAMAGE_RESISTANCE, amp, DURATION);
                }
                if (level >= 9) {
                    addEffect(player, MobEffects.DIG_SPEED, 1, DURATION);
                }
            }
        }
        // 飞行：穿戴 ≥8 重（有效重数）胸甲；疾跑时飞行速度提到鞘翅级
        int[] chest = CompressedBlocks.armorInfoOf(player.getItemBySlot(EquipmentSlot.CHEST));
        boolean mayFly = chest != null && chest[0] == 1 && chest[1] >= 8;
        Abilities abilities = player.abilities;
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

    /** 四件全九重：取消一切伤害（含 /kill、虚空）。 */
    public static boolean rejectDamage(Player player) {
        return CompressedBlocks.hasFullLevel9Armor(player);
    }

    private static void addEffect(ServerPlayer player, MobEffect effect, int amplifier, int duration) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false));
    }

    // ------------------------------------------------------------------ 食物

    /**
     * 食物营养溢出 → 回升：
     * ≤200 回升1×溢出秒；200-1000 回升2×(溢出-200)秒；>1000 生命恢复5+力量5，各上限 10 分钟。
     */
    public static List<MobEffectInstance> foodEffects(int overflow) {
        List<MobEffectInstance> out = new ArrayList<>();
        if (overflow > 1000) {
            out.add(new MobEffectInstance(MobEffects.REGENERATION, 600 * 20, 4));
            out.add(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600 * 20, 4));
            return out;
        }
        if (overflow > 200) {
            out.add(new MobEffectInstance(MobEffects.REGENERATION, (overflow - 200) * 20, 1));
            return out;
        }
        out.add(new MobEffectInstance(MobEffects.REGENERATION, overflow * 20, 0));
        return out;
    }

    // ------------------------------------------------------------------ 英文彩色提示

    private static boolean hintReady(ServerPlayer player) {
        long now = player.level.getGameTime();
        Long last = HINT_COOLDOWN.get(player.getUUID());
        if (last != null && now - last < 20) {
            return false;
        }
        HINT_COOLDOWN.put(player.getUUID(), now);
        return true;
    }

    /** 土壤等级不符（种植作物/树苗）。 */
    public static void sendSoilHint(ServerPlayer player, String enName, int level, boolean farm,
                                    Integer soilLevel) {
        if (!hintReady(player)) {
            return;
        }
        String soil = farm ? "压缩耕地" : "压缩泥土";
        String found = soilLevel == null ? "不是压缩土壤" : soilLevel + " 重 " + soil;
        player.displayClientMessage(new net.minecraft.network.chat.TextComponent("")
            .append(new net.minecraft.network.chat.TextComponent("[压缩方块] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(new net.minecraft.network.chat.TextComponent(enName).withStyle(ChatFormatting.GOLD))
            .append(new net.minecraft.network.chat.TextComponent(" 需要 ").withStyle(ChatFormatting.RED))
            .append(new net.minecraft.network.chat.TextComponent(level + " 重及以上的" + soil).withStyle(ChatFormatting.GOLD))
            .append(new net.minecraft.network.chat.TextComponent("! 当前：").withStyle(ChatFormatting.RED))
            .append(new net.minecraft.network.chat.TextComponent(found).withStyle(ChatFormatting.GRAY)), false);
    }

    /** 压缩甘蔗：下方需 N 重以上的压缩泥土或压缩沙子。 */
    public static void sendCaneHint(ServerPlayer player, String enName, int level, int soilLevel) {
        if (!hintReady(player)) {
            return;
        }
        String found = soilLevel < 0 ? "不是压缩土壤" : soilLevel + " 重";
        player.displayClientMessage(new net.minecraft.network.chat.TextComponent("")
            .append(new net.minecraft.network.chat.TextComponent("[压缩方块] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(new net.minecraft.network.chat.TextComponent(enName).withStyle(ChatFormatting.GOLD))
            .append(new net.minecraft.network.chat.TextComponent(" 需要 ").withStyle(ChatFormatting.RED))
            .append(new net.minecraft.network.chat.TextComponent(level + " 重及以上的压缩泥土/沙子").withStyle(ChatFormatting.GOLD))
            .append(new net.minecraft.network.chat.TextComponent("! 当前：").withStyle(ChatFormatting.RED))
            .append(new net.minecraft.network.chat.TextComponent(found).withStyle(ChatFormatting.GRAY)), false);
    }

    /** 锄头等级不足（锄压缩泥土）。 */
    public static void sendTillHint(ServerPlayer player, int hoeLevel, int dirtLevel) {
        if (!hintReady(player)) {
            return;
        }
        player.displayClientMessage(new net.minecraft.network.chat.TextComponent("")
            .append(new net.minecraft.network.chat.TextComponent("[压缩方块] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(new net.minecraft.network.chat.TextComponent(dirtLevel + " 重压缩泥土").withStyle(ChatFormatting.GOLD))
            .append(new net.minecraft.network.chat.TextComponent(" 需要 ").withStyle(ChatFormatting.RED))
            .append(new net.minecraft.network.chat.TextComponent(dirtLevel + " 重及以上的压缩锄头").withStyle(ChatFormatting.GOLD))
            .append(new net.minecraft.network.chat.TextComponent("! 你的锄头：").withStyle(ChatFormatting.RED))
            .append(new net.minecraft.network.chat.TextComponent(hoeLevel + " 重").withStyle(ChatFormatting.GRAY)), false);
    }

    private CompressedHooks() {
    }
}
