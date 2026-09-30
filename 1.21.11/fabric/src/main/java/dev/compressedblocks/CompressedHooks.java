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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
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
        if (CompressedBlocks.hasFullLevel9Armor(player)) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
        }
        int overflow = CompressedBlocks.wornOverflow(player);
        if (overflow > 0) {
            // 短时长持续刷新：摘下即自然过期；无粒子、环境生效
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, overflow - 1, true, false));
        }
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
