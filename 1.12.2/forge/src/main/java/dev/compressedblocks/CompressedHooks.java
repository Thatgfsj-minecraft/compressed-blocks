package dev.compressedblocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 行为钩子（护甲抗性/免疫/饱食常满、食物溢出回血、英文彩色提示）。
 * 由 Forge 事件桥调用。1.12.2 差异：
 * - 药水常量在 net.minecraft.init.MobEffects（RESISTANCE/HASTE/NIGHT_VISION/WATER_BREATHING）；
 * - 飞行走 capabilities（allowFlying/flySpeed）；
 * - 效果构造 PotionEffect(Potion, duration, amplifier, ambient, particles)。
 * 降级记录：九重工具挖基岩需要 mixin（破坏进度客户端插值），本轮不实现。
 */
public final class CompressedHooks {
    /** 常规效果时长：5 秒短刷新（摘下即过期，无粒子）。 */
    private static final int DURATION = 100;
    /** 夜视时长 20 秒：低于 10 秒原版会闪黑屏警告，必须给长。 */
    private static final int NIGHT_VISION_DURATION = 400;
    /** 英文提示防刷屏：每玩家 1 秒一条。 */
    private static final Map<UUID, Long> HINT_COOLDOWN = new HashMap<>();

    /** 事件桥（ Forge 总线订阅者；1.12.2 单总线，直接 @SubscribeEvent）。 */
    public static final Object BRIDGE = new Object() {
        /** 护甲结算：抗性提升 + 八重飞行 + 九重饱食度常满。 */
        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !event.player.world.isRemote
                && event.player instanceof EntityPlayerMP) {
                armorTick((EntityPlayerMP) event.player);
            }
        }

        /** 九重满套：取消一切伤害（含 /kill）。 */
        @SubscribeEvent
        public void onIncomingDamage(LivingAttackEvent event) {
            if (event.getEntityLiving() instanceof EntityPlayer
                && CompressedHooks.rejectDamage((EntityPlayer) event.getEntityLiving())) {
                event.setCanceled(true);
            }
        }
    };

    // ------------------------------------------------------------------ 护甲

    /**
     * 护甲每刻结算（服务端玩家 tick 调用）。分部位效果：
     * 头盔 7 重=夜视、8 重=+水下呼吸；护腿 7/8/9 重=抗性 1/2/3、9 重=+急迫2；
     * 胸甲 8 重=生存飞行（疾跑=鞘翅速度）；靴子：无。
     * 木质盔甲按有效重数（重数-1）自动降档。饱食度常满：仅九重石甲满套。
     */
    public static void armorTick(EntityPlayerMP player) {
        int[][] slots = {
            CompressedBlocks.armorInfoOf(player.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD)),
            CompressedBlocks.armorInfoOf(player.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST)),
            CompressedBlocks.armorInfoOf(player.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.LEGS)),
            CompressedBlocks.armorInfoOf(player.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.FEET))
        };
        // 头盔：7 重=夜视（20 秒防黑屏闪）、8 重=+水下呼吸
        int[] head = slots[0];
        if (head != null && head[0] == 0 && head[1] >= 7) {
            addEffect(player, MobEffectsRef.NIGHT_VISION, 0, NIGHT_VISION_DURATION);
            if (head[1] >= 8) {
                addEffect(player, MobEffectsRef.WATER_BREATHING, 0, DURATION);
            }
        }
        // 护腿：抗性阶梯 + 九重急迫
        int[] legs = slots[2];
        if (legs != null && legs[0] == 2) {
            int amp = CompressedBlocks.resistanceAmplifier(legs[1]);
            if (amp >= 0) {
                addEffect(player, MobEffectsRef.RESISTANCE, amp, DURATION);
            }
            if (legs[1] >= 9) {
                addEffect(player, MobEffectsRef.HASTE, 1, DURATION);
            }
        }
        // 飞行：穿戴 ≥8 重（有效重数）胸甲；疾跑时飞行速度提到鞘翅级
        int[] chest = slots[1];
        boolean mayFly = chest != null && chest[0] == 1 && chest[1] >= 8;
        net.minecraft.entity.player.PlayerCapabilities abilities = player.capabilities;
        if (mayFly) {
            if (!abilities.allowFlying) {
                abilities.allowFlying = true;
                player.sendPlayerAbilities();
            }
            float target = player.isSprinting() ? 0.15F : 0.05F;
            if (Math.abs(abilities.getFlySpeed() - target) > 0.001F) {
                abilities.setFlySpeed(target);
                player.sendPlayerAbilities();
            }
        } else if (abilities.allowFlying && !abilities.isCreativeMode) {
            abilities.allowFlying = false;
            abilities.isFlying = false;
            abilities.setFlySpeed(0.05F);
            player.sendPlayerAbilities();
        }
        if (CompressedBlocks.hasFullLevel9Armor(player)) {
            player.getFoodStats().setFoodLevel(20);
            player.getFoodStats().setFoodSaturationLevel(20.0F);
        }
    }

    /** 四件全九重：取消一切伤害（含 /kill、虚空）。 */
    public static boolean rejectDamage(EntityPlayer player) {
        return CompressedBlocks.hasFullLevel9Armor(player);
    }

    private static void addEffect(EntityPlayerMP player, Potion potion, int amplifier, int duration) {
        player.addPotionEffect(new PotionEffect(potion, duration, amplifier, true, false));
    }

    /** 1.12.2 药水常量隔离（MobEffects 常量名与 1.16 的 MobEffects 系列不同名）。 */
    private static final class MobEffectsRef {
        static final Potion NIGHT_VISION = net.minecraft.init.MobEffects.NIGHT_VISION;
        static final Potion WATER_BREATHING = net.minecraft.init.MobEffects.WATER_BREATHING;
        static final Potion RESISTANCE = net.minecraft.init.MobEffects.RESISTANCE;
        static final Potion HASTE = net.minecraft.init.MobEffects.HASTE;
        static final Potion REGENERATION = net.minecraft.init.MobEffects.REGENERATION;
        static final Potion STRENGTH = net.minecraft.init.MobEffects.STRENGTH;
    }

    // ------------------------------------------------------------------ 食物

    /**
     * 食物营养溢出 → 回升：
     * ≤200 回升1×溢出秒；200-1000 回升2×(溢出-200)秒；>1000 生命恢复5+力量5，各上限 10 分钟。
     */
    public static List<PotionEffect> foodEffects(int overflow) {
        List<PotionEffect> out = new ArrayList<>();
        if (overflow > 1000) {
            out.add(new PotionEffect(MobEffectsRef.REGENERATION, 600 * 20, 4));
            out.add(new PotionEffect(MobEffectsRef.STRENGTH, 600 * 20, 4));
            return out;
        }
        if (overflow > 200) {
            out.add(new PotionEffect(MobEffectsRef.REGENERATION, (overflow - 200) * 20, 1));
            return out;
        }
        out.add(new PotionEffect(MobEffectsRef.REGENERATION, overflow * 20, 0));
        return out;
    }

    // ------------------------------------------------------------------ 英文彩色提示

    private static boolean hintReady(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        long now = player.world.getTotalWorldTime();
        Long last = HINT_COOLDOWN.get(player.getUniqueID());
        if (last != null && now - last < 20) {
            return false;
        }
        HINT_COOLDOWN.put(player.getUniqueID(), now);
        return true;
    }

    private static void sendHint(EntityPlayer player, String namePart, String midPart, String foundPart) {
        if (!hintReady(player)) {
            return;
        }
        player.sendMessage(new TextComponentString("")
            .appendSibling(tagged("[压缩方块] ", TextFormatting.DARK_GRAY))
            .appendSibling(tagged(namePart, TextFormatting.GOLD))
            .appendSibling(tagged(midPart, TextFormatting.RED))
            .appendSibling(tagged(foundPart, TextFormatting.GRAY)));
    }

    private static TextComponentString tagged(String text, TextFormatting color) {
        TextComponentString out = new TextComponentString(text);
        out.getStyle().setColor(color);
        return out;
    }

    /** 土壤等级不符（种植作物/树苗）。 */
    public static void sendSoilHint(EntityPlayer player, String enName, int level, boolean farm,
                                    Integer soilLevel) {
        String soil = farm ? "压缩耕地" : "压缩泥土";
        String found = soilLevel == null ? "不是压缩土壤" : soilLevel + " 重 " + soil;
        sendHint(player, enName,
            " 需要 " + level + " 重及以上的" + soil + "! 当前：", found);
    }

    /** 压缩甘蔗：下方需 N 重以上的压缩泥土或压缩沙子。 */
    public static void sendCaneHint(EntityPlayer player, String enName, int level, int soilLevel) {
        String found = soilLevel < 0 ? "不是压缩土壤" : soilLevel + " 重";
        sendHint(player, enName,
            " 需要 " + level + " 重及以上的压缩泥土/沙子! 当前：", found);
    }

    /** 盆栽土壤等级不足。 */
    public static void sendPotHint(EntityPlayer player, int level, int soilLevel) {
        sendHint(player, "盆内需要 " + level + " 重及以上的压缩泥土/沙子",
            "! 盆内土壤：", soilLevel + " 重");
    }

    /** 锄头等级不足（锄压缩泥土）。 */
    public static void sendTillHint(EntityPlayer player, int hoeLevel, int dirtLevel) {
        sendHint(player, dirtLevel + " 重压缩泥土",
            " 需要 " + dirtLevel + " 重及以上的压缩锄头! 你的锄头：", hoeLevel + " 重");
    }

    private CompressedHooks() {
    }
}
