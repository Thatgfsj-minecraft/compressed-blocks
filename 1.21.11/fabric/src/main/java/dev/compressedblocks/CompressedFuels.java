package dev.compressedblocks;

import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * 压缩方块可燃物表（两条线共用；Fabric 走 FuelRegistryEvents 注册，NeoForge 走
 * {@code neoforge/data_maps/item/furnace_fuels.json} 数据图，二者数值必须一致 —— SELF-TEST 会对拍）。
 *
 * <p>规则（用户 2026-11-17 定稿）：</p>
 * <ul>
 *   <li>压缩木质储存方块（压缩原木 1~9 重）：原版原木 300 tick，每重 ×5（最高 5⁹；当前只有 1~9 重物品，
 *   {@code Math.min(level, LEVELS)} 只是防御性封顶，不是可无限叠加的规则）</li>
 *   <li>木线工具 / 木制盔甲：原版木质工具 200 tick，每重 ×5</li>
 *   <li>压缩木棍：原版木棍 100 tick，每重 ×5</li>
 *   <li>压缩树苗：原版树苗 100 tick，每重 ×5（2026-11-18 用户确认加入）</li>
 *   <li>压缩树叶：按树苗同档 100 tick，每重 ×5（2026-11-18 用户确认加入；方块本体不可燃
 *   ——高密度不点燃、不传播火焰，仅作熔炉燃料，与原版树叶本就非燃料不同，属模组定位的增强）</li>
 *   <li>木质漏斗（基础 + 1~3 重压缩）：按箱子 300 tick 基准，每重 ×5</li>
 *   <li>压缩箱子：5 × 箱子 = 1500 tick</li>
 *   <li>盆栽（pot / hopper_pot）**不可燃**</li>
 * </ul>
 */
public final class CompressedFuels {
    /** 每重倍率。 */
    public static final int TIER_FACTOR = 5;
    /** 原版原木燃烧时间。 */
    public static final int LOG_BASE = 300;
    /** 原版木质工具燃烧时间。 */
    public static final int TOOL_BASE = 200;
    /** 原版木棍燃烧时间。 */
    public static final int STICK_BASE = 100;
    /** 原版树苗燃烧时间（与木棍同为 100 tick）。 */
    public static final int SAPLING_BASE = 100;
    /** 压缩树叶燃烧时间：按树苗同档 100 tick（原版树叶本就非燃料，此处为模组定位的增强）。 */
    public static final int LEAVES_BASE = 100;
    /** 木制盔甲按木质工具时间（与 TOOL_BASE 同值，单列以便日后单独调整）。 */
    public static final int ARMOR_BASE = 200;
    /** 木质漏斗按箱子时间。 */
    public static final int HOPPER_BASE = 300;
    /** 压缩箱子 = 5 × 箱子。 */
    public static final int COMPRESSED_CHEST = LOG_BASE * TIER_FACTOR;
    /**
     * 规则命中的物品总数（9 树种 × (9 原木 + 9 树苗 + 9 树叶) + 9 压缩木棍 + 45 木线工具
     * + 36 木甲 + 4 木漏斗 + 1 压缩箱 = 338）。
     * 改动燃料规则时必须同步；SELF-TEST 用它对拍「注册进 FuelValues 的条目数」，防止静默漏注册。
     */
    public static final int EXPECTED_ITEM_COUNT = 338;

    private static final int[] TIER_MULT = new int[CompressedBlocks.LEVELS + 1];

    static {
        TIER_MULT[0] = 1;
        for (int i = 1; i < TIER_MULT.length; i++) {
            TIER_MULT[i] = TIER_MULT[i - 1] * TIER_FACTOR;
        }
    }

    private CompressedFuels() {
    }

    /** 遍历本模组全部可燃物品；回调 (物品, 燃烧 tick)。 */
    public static void forEach(BiConsumer<Item, Integer> sink) {
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(CompressedBlocks.MOD_ID)) {
                continue;
            }
            int burn = burnTimeFor(id.getPath());
            if (burn > 0) {
                sink.accept(item, burn);
            }
        }
    }

    /** 按注册名（不含命名空间）计算燃烧时间；不可燃返回 -1。 */
    public static int burnTimeFor(String path) {
        if (path.equals("wooden_hopper")) {
            return HOPPER_BASE;
        }
        if (path.equals("compressed_chest")) {
            return COMPRESSED_CHEST;
        }
        int level = levelOf(path);
        if (level < 0) {
            return -1;
        }
        int mult = TIER_MULT[Math.min(level, CompressedBlocks.LEVELS)];
        if (path.endsWith("_compressed_wooden_hopper")) {
            return level >= 1 ? HOPPER_BASE * mult : -1;
        }
        if (path.endsWith("_log")) {
            return level >= 1 ? LOG_BASE * mult : -1;
        }
        if (path.endsWith("_leaves")) {
            return level >= 1 ? LEAVES_BASE * mult : -1;
        }
        if (path.endsWith("_stick")) {
            return level >= 1 ? STICK_BASE * mult : -1;
        }
        if (path.endsWith("_sapling")) {
            return level >= 1 ? SAPLING_BASE * mult : -1;
        }
        int wood = path.indexOf("_wood_");
        if (wood >= 0) {
            String kind = path.substring(wood + "_wood_".length());
            if (kind.equals("pickaxe") || kind.equals("axe") || kind.equals("shovel")
                || kind.equals("hoe") || kind.equals("sword")
                || kind.equals("helmet") || kind.equals("chestplate")
                || kind.equals("leggings") || kind.equals("boots")) {
                return TOOL_BASE * mult;
            }
        }
        return -1;
    }

    /** 解析 "Nx_" 前缀为重数；无该前缀返回 0（基础物品）。 */
    private static int levelOf(String path) {
        int x = path.indexOf('x');
        if (x <= 0 || x > 2 || path.length() <= x + 1 || path.charAt(x + 1) != '_') {
            return 0;
        }
        String digits = path.substring(0, x);
        if (!digits.chars().allMatch(Character::isDigit)) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
