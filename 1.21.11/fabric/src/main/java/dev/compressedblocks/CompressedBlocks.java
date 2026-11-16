package dev.compressedblocks;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;

/**
 * 压缩方块核心：纯数据驱动。方块/物品实例在此集中构建，注册动作由各加载器入口完成；
 * 本类不 import 任何加载器类（组织约定：core 每版本双加载器逐字节一致）。
 *
 * 0.3.0：储存方块统一硬度 50×1.2^(重-1)、全部 1200 爆炸抗性；新增压缩护甲（九重满套免疫一切伤害
 * 且饱食度常满，溢出防御转抗性提升）、24 种建材、耕地/作物/树苗/树叶农业系统、压缩食物。
 */
public final class CompressedBlocks {
    public static final String MOD_ID = "compressedblocks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final int LEVELS = 9;
    /** 六重及以上工具/护甲：不可破坏（原版 unbreakable 组件）。 */
    public static final int UNBREAKABLE_FROM = 6;
    /** 作物/食物封顶 3 级。 */
    public static final int CROP_MAX_LEVEL = 3;
    public static final int FOOD_MAX_LEVEL = 3;

    public static final String[] LEVEL_PREFIX = {
        "1x", "2x", "3x", "4x", "5x", "6x", "7x", "8x", "9x"
    };
    static final String[] LEVEL_EN_PREFIX = {
        "Compressed", "Double Compressed", "Triple Compressed", "Quadruple Compressed", "Quintuple Compressed",
        "Sextuple Compressed", "Septuple Compressed", "Octuple Compressed", "Nonuple Compressed"
    };

    /** 石线挖掘速度阶梯：一重=铁 6.0、二重=钻 8.0、三重=金 12.0，四重起 ×1.5 逐级连乘。 */
    static final float[] STONE_SPEED = {6.0F, 8.0F, 12.0F, 18.0F, 27.0F, 40.5F, 60.75F, 91.125F, 136.6875F};

    /** 木线挖掘速度阶梯 = 石线 × 0.95（木线整体定位于石线之下，但仍远快于原版木工具）。 */
    static final float[] WOOD_SPEED = {5.7F, 7.6F, 11.4F, 17.1F, 25.65F, 38.475F, 57.7125F, 86.56875F, 129.853125F};

    // ------------------------------------------------------------------ 储存方块

    /** 压缩方块硬度：黑曜石级 50 起步，每重 ×1.2（所需挖掘速度逐层上浮）。 */
    public static final float STORAGE_HARDNESS_BASE = 50.0F;
    public static final double STORAGE_HARDNESS_STEP = 1.2;
    /** 全部压缩方块炸不毁（黑曜石级爆炸抗性）。 */
    public static final float STORAGE_BLAST = 1200.0F;

    /** 压缩硬度：L1 = 原版该方块硬度，线性升到 L9 目标（黑曜石类 200、硬类 150、软类 100）。 */
    public static float hardnessFor(StorageMat m, int level) {
        float vh = m.vh();
        float target = m.key().equals("obsidian") ? 200.0F : (vh >= 1.5F ? 150.0F : 100.0F);
        return vh + (target - vh) * (level - 1) / 8.0F;
    }

    public enum ToolKind { STONE, WOOD }

    /** 创造物品栏落位。 */
    public enum Tab { BLOCKS, TOOLS, INGREDIENTS, FOOD, POTS }

    /**
     * 储存方块材料（key/英文名/声音/地图色/是否需要正确工具/发光/原版硬度）。
     * 51 材料：19 旧材料 + 8 矿物块 + 24 建材（含火药块——原版无方块形态）。
     * 硬度与爆炸抗性统一走公式；英文名供压缩甘蔗提示拼接（lang 由生成器产出）。
     */
    record StorageMat(String key, String en, SoundType sound, MapColor color,
                              boolean requiresTool, int light, float vh) {
    }

    static final List<StorageMat> STORAGE = List.of(
        new StorageMat("cobblestone", "Cobblestone", SoundType.STONE, MapColor.STONE, true, 0, 2F),
        new StorageMat("stone", "Stone", SoundType.STONE, MapColor.STONE, true, 0, 1.5F),
        new StorageMat("cobbled_deepslate", "Cobbled Deepslate", SoundType.DEEPSLATE, MapColor.DEEPSLATE, true, 0, 3.5F),
        new StorageMat("deepslate", "Deepslate", SoundType.DEEPSLATE, MapColor.DEEPSLATE, true, 0, 3F),
        new StorageMat("oak_log", "Oak Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("spruce_log", "Spruce Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("birch_log", "Birch Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("jungle_log", "Jungle Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("acacia_log", "Acacia Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("dark_oak_log", "Dark Oak Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("mangrove_log", "Mangrove Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("cherry_log", "Cherry Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("pale_oak_log", "Pale Oak Log", SoundType.WOOD, MapColor.WOOD, false, 0, 2F),
        new StorageMat("dirt", "Dirt", SoundType.GRAVEL, MapColor.DIRT, false, 0, 0.5F),
        new StorageMat("sand", "Sand", SoundType.SAND, MapColor.SAND, false, 0, 0.5F),
        new StorageMat("gravel", "Gravel", SoundType.GRAVEL, MapColor.STONE, false, 0, 0.6F),
        new StorageMat("netherrack", "Netherrack", SoundType.NETHERRACK, MapColor.NETHER, true, 0, 0.4F),
        new StorageMat("end_stone", "End Stone", SoundType.STONE, MapColor.SAND, true, 0, 3F),
        new StorageMat("obsidian", "Obsidian", SoundType.STONE, MapColor.COLOR_BLACK, true, 0, 50F),
        new StorageMat("coal_block", "Block of Coal", SoundType.STONE, MapColor.COLOR_BLACK, true, 0, 5F),
        new StorageMat("copper_block", "Block of Copper", SoundType.COPPER, MapColor.COLOR_ORANGE, true, 0, 3F),
        new StorageMat("iron_block", "Block of Iron", SoundType.METAL, MapColor.METAL, true, 0, 5F),
        new StorageMat("lapis_block", "Lapis Lazuli Block", SoundType.STONE, MapColor.LAPIS, true, 0, 3F),
        new StorageMat("gold_block", "Block of Gold", SoundType.METAL, MapColor.GOLD, true, 0, 3F),
        new StorageMat("redstone_block", "Block of Redstone", SoundType.STONE, MapColor.COLOR_RED, true, 0, 5F),
        new StorageMat("emerald_block", "Block of Emerald", SoundType.METAL, MapColor.EMERALD, true, 0, 5F),
        new StorageMat("diamond_block", "Block of Diamond", SoundType.METAL, MapColor.DIAMOND, true, 0, 5F),
        new StorageMat("granite", "Granite", SoundType.STONE, MapColor.COLOR_ORANGE, true, 0, 1.5F),
        new StorageMat("diorite", "Diorite", SoundType.STONE, MapColor.QUARTZ, true, 0, 1.5F),
        new StorageMat("andesite", "Andesite", SoundType.STONE, MapColor.STONE, true, 0, 1.5F),
        new StorageMat("calcite", "Calcite", SoundType.CALCITE, MapColor.QUARTZ, true, 0, 0.75F),
        new StorageMat("tuff", "Tuff", SoundType.TUFF, MapColor.COLOR_GRAY, true, 0, 1.5F),
        new StorageMat("sandstone", "Sandstone", SoundType.STONE, MapColor.SAND, true, 0, 0.8F),
        new StorageMat("red_sandstone", "Red Sandstone", SoundType.STONE, MapColor.COLOR_ORANGE, true, 0, 0.8F),
        new StorageMat("basalt", "Basalt", SoundType.BASALT, MapColor.COLOR_GRAY, true, 0, 1.25F),
        new StorageMat("blackstone", "Blackstone", SoundType.STONE, MapColor.COLOR_BLACK, true, 0, 1.5F),
        new StorageMat("dripstone_block", "Dripstone Block", SoundType.DRIPSTONE_BLOCK, MapColor.COLOR_BROWN, true, 0, 1.5F),
        new StorageMat("terracotta", "Terracotta", SoundType.STONE, MapColor.COLOR_ORANGE, true, 0, 1.25F),
        new StorageMat("quartz_block", "Quartz Block", SoundType.STONE, MapColor.QUARTZ, true, 0, 0.8F),
        new StorageMat("purpur_block", "Purpur Block", SoundType.STONE, MapColor.COLOR_MAGENTA, true, 0, 1.5F),
        new StorageMat("prismarine", "Prismarine", SoundType.STONE, MapColor.COLOR_CYAN, true, 0, 1.5F),
        new StorageMat("amethyst_block", "Amethyst Block", SoundType.AMETHYST, MapColor.COLOR_PURPLE, true, 0, 1.5F),
        new StorageMat("glowstone", "Glowstone", SoundType.GLASS, MapColor.SAND, false, 15, 0.3F),
        new StorageMat("clay", "Clay", SoundType.GRAVEL, MapColor.COLOR_LIGHT_GRAY, false, 0, 0.6F),
        new StorageMat("hay_block", "Hay Bale", SoundType.GRASS, MapColor.COLOR_YELLOW, false, 0, 0.5F),
        new StorageMat("bone_block", "Bone Block", SoundType.STONE, MapColor.SAND, true, 0, 2F),
        new StorageMat("moss_block", "Moss Block", SoundType.MOSS, MapColor.COLOR_GREEN, false, 0, 0.1F),
        new StorageMat("snow", "Snow Block", SoundType.SNOW, MapColor.SNOW, false, 0, 0.2F),
        new StorageMat("blue_ice", "Blue Ice", SoundType.GLASS, MapColor.COLOR_LIGHT_BLUE, false, 0, 2.8F),
        new StorageMat("mud", "Mud", SoundType.MUD, MapColor.COLOR_GRAY, false, 0, 0.5F),
        new StorageMat("gunpowder", "Block of Gunpowder", SoundType.SAND, MapColor.COLOR_GRAY, false, 0, 0.5F)
    );

    /** 甘蔗 id 的材料段：去掉 "_block" 后缀（diamond_block → diamond），与生成器 cane_key 一致。 */
    static String caneKey(String matKey) {
        return matKey.endsWith("_block") ? matKey.substring(0, matKey.length() - 6) : matKey;
    }

    /** 甘蔗英文显示名：材料名去掉 "Block of " 前缀 / " Block" / " Bale" 后缀，与生成器 cane_en 一致。 */
    static String caneEnName(String matEn) {
        if (matEn.startsWith("Block of ")) {
            return matEn.substring("Block of ".length());
        }
        if (matEn.endsWith(" Block")) {
            return matEn.substring(0, matEn.length() - " Block".length());
        }
        if (matEn.endsWith(" Bale")) {
            return matEn.substring(0, matEn.length() - " Bale".length());
        }
        return matEn;
    }

    // ------------------------------------------------------------------ 树 / 作物 / 食物

    record WoodDef(String key, String en, String zh) {
    }

    static final List<WoodDef> WOODS = List.of(
        new WoodDef("oak", "Oak", "橡树"),
        new WoodDef("spruce", "Spruce", "云杉"),
        new WoodDef("birch", "Birch", "白桦"),
        new WoodDef("jungle", "Jungle", "丛林"),
        new WoodDef("acacia", "Acacia", "金合欢"),
        new WoodDef("dark_oak", "Dark Oak", "深色橡树"),
        new WoodDef("mangrove", "Mangrove", "红树"),
        new WoodDef("cherry", "Cherry", "樱花"),
        new WoodDef("pale_oak", "Pale Oak", "苍白橡树")
    );

    record CropDef(String key, String en, boolean hasSeeds) {
    }

    static final List<CropDef> CROPS = List.of(
        new CropDef("wheat", "Wheat", true),
        new CropDef("carrot", "Carrot", false),
        new CropDef("potato", "Potato", false),
        new CropDef("beetroot", "Beetroot", true)
    );

    record FoodDef(String key, String en, float nutrition, float saturation) {
    }

    /** 原版数值（名称与生/熟数值全部对齐原版）；N 级压缩 = ×9^N。 */
    static final List<FoodDef> FOODS = List.of(
        new FoodDef("bread", "Bread", 5.0F, 6.0F),
        new FoodDef("beef", "Raw Beef", 3.0F, 1.8F),
        new FoodDef("cooked_beef", "Steak", 8.0F, 12.8F),
        new FoodDef("porkchop", "Raw Porkchop", 3.0F, 1.8F),
        new FoodDef("cooked_porkchop", "Cooked Porkchop", 8.0F, 12.8F),
        new FoodDef("mutton", "Raw Mutton", 2.0F, 1.2F),
        new FoodDef("cooked_mutton", "Cooked Mutton", 6.0F, 9.6F),
        new FoodDef("chicken", "Raw Chicken", 2.0F, 1.2F),
        new FoodDef("cooked_chicken", "Cooked Chicken", 6.0F, 7.2F),
        new FoodDef("rabbit", "Raw Rabbit", 3.0F, 1.8F),
        new FoodDef("cooked_rabbit", "Cooked Rabbit", 5.0F, 6.0F),
        new FoodDef("cod", "Raw Cod", 2.0F, 0.4F),
        new FoodDef("cooked_cod", "Cooked Cod", 5.0F, 6.0F),
        new FoodDef("salmon", "Raw Salmon", 2.0F, 0.4F),
        new FoodDef("cooked_salmon", "Cooked Salmon", 6.0F, 9.6F),
        new FoodDef("melon", "Watermelon", 2.0F, 1.2F),
        new FoodDef("rotten_flesh", "Rotten Flesh", 4.0F, 0.8F),
        new FoodDef("baked_potato", "Baked Potato", 5.0F, 6.0F),
        new FoodDef("apple", "Apple", 4.0F, 2.4F)
    );

    /** 盆栽苹果掉落：1 重 15%，每层 +1%（9 重 23%）；时运沿用原版 table_bonus 的相对倍数。 */
    private static final double[] APPLE_FORTUNE_MULT = {1.0, 1.1111112, 1.25, 1.6666667};

    /** 盆栽种 level 重树苗时的苹果掉落概率（%）。 */
    public static int applePotPct(int level) {
        return 15 + (Math.max(level, 1) - 1);
    }

    /** 压缩苹果只做到 3 重：4 重及以上统一掉 3 重苹果。 */
    public static int appleTier(int level) {
        return Math.min(Math.max(level, 1), FOOD_MAX_LEVEL);
    }

    /** 按百分比掷苹果数量（受时运提升概率；当前上限 < 100%，返回 0 或 1）。 */
    public static int appleCount(int basePct, int fortune, RandomSource random) {
        int f = Math.min(Math.max(fortune, 0), APPLE_FORTUNE_MULT.length - 1);
        int pct = (int) Math.round(basePct * APPLE_FORTUNE_MULT[f]);
        return random.nextInt(100) < pct ? 1 : 0;
    }

    // ------------------------------------------------------------------ 护甲

    public static final ArmorType[] ARMOR_TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
    /** 石制护甲基准=铁级：头 2 / 胸 6 / 腿 5 / 靴 2。 */
    public static final int[] ARMOR_BASE_DEFENSE = {2, 6, 5, 2};
    private static final int[] ARMOR_DURABILITY_BASE = {11, 16, 15, 13};
    static final int ARMOR_ENCHANTABILITY = 9;

    /** 每重每件 +1，单件封顶 10。 */
    public static int armorDefense(int level, int piece) {
        return Math.min(ARMOR_BASE_DEFENSE[piece] + level - 1, 10);
    }

    /** 超过 10 的部分转为抗性提升（等级=穿戴件溢出总和）。 */
    public static int armorOverflow(int level, int piece) {
        return Math.max(0, ARMOR_BASE_DEFENSE[piece] + level - 1 - 10);
    }

    /** 耐久 = 9^重 × 铁甲基准；六重起不可破坏（给不溢出的最大乘数，配合 unbreakable 组件）。 */
    public static int armorDurability(int level, int piece) {
        long mult = level >= UNBREAKABLE_FROM ? Integer.MAX_VALUE / 16L : pow9(level);
        return (int) (ARMOR_DURABILITY_BASE[piece] * mult);
    }

    static long pow9(int level) {
        long value = 1;
        for (int i = 0; i < level; i++) {
            value *= 9;
        }
        return value;
    }

    // ------------------------------------------------------------------ 注册表

    /** @param itemName null = 无物品（压缩耕地）。 */
    public record BlockReg(String name, Block block, String itemName) {
    }

    /** @param blockKey true = 翻译键用 block. 前缀（方块物品同 id），false = item. 前缀。 */
    public record ItemReg(String name, Item item, Tab tab, boolean blockKey) {
    }

    record ToolReg(String name, Item item, int durability, float speed, boolean unbreakable, double damage) {
    }

    record ArmorReg(String name, Item item, int piece, int level) {
    }

    record FoodReg(String name, Item item, int nutrition, float saturation) {
    }

    static final List<BlockReg> BLOCKS = new ArrayList<>();
    static final List<ItemReg> ITEMS = new ArrayList<>();
    static final List<BlockReg> STORAGE_BLOCKS = new ArrayList<>();
    static final List<ToolReg> TOOL_REGS = new ArrayList<>();
    static final List<ArmorReg> ARMOR_REGS = new ArrayList<>();
    static final List<FoodReg> FOOD_REGS = new ArrayList<>();
    static final Map<Item, int[]> ARMOR_INDEX = new IdentityHashMap<>();
    static final Map<Block, Integer> DIRT_LEVELS = new IdentityHashMap<>();
    static final Map<Block, Integer> SAND_LEVELS = new IdentityHashMap<>();
    static final Map<Block, Integer> NETHER_SOIL_LEVELS = new IdentityHashMap<>();
    static final Map<Integer, Block> FARMLAND = new IdentityHashMap<>();

    /** 渲染层注册用分组：cross/crop 类植物 → CUTOUT，树叶 → CUTOUT（客户端入口注册）。 */
    public static final List<Block> SAPLING_BLOCKS = new ArrayList<>();
    public static final List<Block> CANE_BLOCKS = new ArrayList<>();
    public static final List<Block> CROP_BLOCKS = new ArrayList<>();
    public static final List<Block> LEAVES_BLOCKS = new ArrayList<>();
    public static final List<Block> GENERATOR_BLOCKS = new ArrayList<>();

    /** 刷石机（3 压缩等级）与共享方块实体类型（类型由加载器入口构建注入）。 */
    public static BlockEntityType<CobblestoneGeneratorBlockEntity> GENERATOR_TYPE;

    /** 压缩熔炉（4 压缩等级）与各等级方块实体类型（类型由加载器入口构建注入）。 */
    public static final int FURNACE_TIERS = 4;
    public static final List<Block> FURNACE_BLOCKS = new ArrayList<>();
    public static BlockEntityType<?>[] FURNACE_TYPE;

    /** 压缩漏斗（3 压缩等级）与各等级方块实体类型（类型由加载器入口构建注入）。 */
    public static final int HOPPER_TIERS = 3;
    public static final List<Block> HOPPER_BLOCKS = new ArrayList<>();
    public static BlockEntityType<?>[] HOPPER_TYPE;

    /** 木质漏斗（4 个等级：0=基础木漏斗，1~3=一至三重压缩木漏斗）与方块实体类型。 */
    public static final int WOODEN_HOPPER_TIERS = 4;
    public static final List<Block> WOODEN_HOPPER_BLOCKS = new ArrayList<>();
    public static BlockEntityType<?>[] WOODEN_HOPPER_TYPE;

    /** 大容量容器插入钩子（加载器入口注入：Fabric=Transfer API、NeoForge=IItemHandler），
     *  兼容储物抽屉等不按原版 Container 64 上限计算的 mod。 */
    public interface ItemSink {
        /** 目标位置从给定面是否可接收物品。 */
        boolean accepts(Level level, BlockPos pos, Direction side);

        /** 尝试插入，返回移动数量。 */
        long insert(Level level, BlockPos pos, Direction side, ItemStack stack);
    }

    public static ItemSink ITEM_SINK;

    // 压缩箱子/压缩潜影盒（单一等级，243 格滚动存储）
    public static CompressedChestBlock COMPRESSED_CHEST;
    public static CompressedShulkerBlock COMPRESSED_SHULKER;
    /** 方块实体类型由加载器入口构建注入。 */
    public static BlockEntityType<ScrollingContainerBlockEntity> CHEST_TYPE;
    public static BlockEntityType<ScrollingContainerBlockEntity> SHULKER_TYPE;
    /** 菜单类型（原版公开构造；客户端工厂用哑容器，槽位按索引同步内容）。 */
    public static final MenuType<ScrollingContainerMenu> CHEST_MENU_TYPE = new MenuType<>(
        (id, inv) -> new ScrollingContainerMenu(
            CompressedBlocks.CHEST_MENU_TYPE, id, inv, new SimpleContainer(ScrollingContainerMenu.SIZE)),
        FeatureFlags.VANILLA_SET);
    public static final MenuType<ScrollingContainerMenu> SHULKER_MENU_TYPE = new MenuType<>(
        (id, inv) -> new ScrollingContainerMenu(
            CompressedBlocks.SHULKER_MENU_TYPE, id, inv, new SimpleContainer(ScrollingContainerMenu.SIZE)),
        FeatureFlags.VANILLA_SET);
    /** 木质漏斗界面：容量随等级变化，1 格与 3 格各一个菜单类型（客户端工厂拿不到方块实体）。 */
    public static final MenuType<WoodenHopperMenu> WOODEN_HOPPER_MENU_TYPE = new MenuType<>(
        (id, inv) -> new WoodenHopperMenu(
            CompressedBlocks.WOODEN_HOPPER_MENU_TYPE, id, inv,
            new SimpleContainer(WoodenHopperMenu.BASE_SIZE), WoodenHopperMenu.BASE_SIZE),
        FeatureFlags.VANILLA_SET);
    public static final MenuType<WoodenHopperMenu> COMPRESSED_WOODEN_HOPPER_MENU_TYPE = new MenuType<>(
        (id, inv) -> new WoodenHopperMenu(
            CompressedBlocks.COMPRESSED_WOODEN_HOPPER_MENU_TYPE, id, inv,
            new SimpleContainer(WoodenHopperMenu.COMPRESSED_SIZE), WoodenHopperMenu.COMPRESSED_SIZE),
        FeatureFlags.VANILLA_SET);

    /** 压缩金属包（科技/魔法 mod 经典材质；合成走 #c:*_ingots 通用标签）。(key, 挖掘档 0-2, 发光) */
    record CompatMetal(String key, int tier, int light) {
    }

    static final List<CompatMetal> COMPAT_METALS = List.of(
        new CompatMetal("tin", 0, 0),
        new CompatMetal("lead", 0, 0),
        new CompatMetal("zinc", 0, 0),
        new CompatMetal("plastic", 0, 0),
        new CompatMetal("silver", 1, 0),
        new CompatMetal("nickel", 1, 0),
        new CompatMetal("bronze", 1, 0),
        new CompatMetal("brass", 1, 0),
        new CompatMetal("electrum", 1, 0),
        new CompatMetal("invar", 1, 0),
        new CompatMetal("constantan", 1, 0),
        new CompatMetal("steel", 1, 0),
        new CompatMetal("manasteel", 1, 0),
        new CompatMetal("uranium", 2, 0),
        new CompatMetal("osmium", 2, 0),
        new CompatMetal("signalum", 2, 0),
        new CompatMetal("enderium", 2, 0),
        new CompatMetal("refined_obsidian", 2, 0),
        new CompatMetal("refined_glowstone", 2, 15),
        new CompatMetal("lumium", 2, 12),
        new CompatMetal("terrasteel", 2, 0),
        new CompatMetal("elementium", 2, 0)
    );

    private static int compatCount() {
        return COMPAT_METALS.size() * LEVELS;
    }


    /** 压缩盆栽（普通/漏斗）与共享方块实体类型。 */
    public static CompressedPotBlock POT;
    public static CompressedPotBlock HOPPER_POT;
    public static BlockEntityType<CompressedPotBlockEntity> POT_TYPE;

    public static List<BlockReg> blocks() {
        return BLOCKS;
    }

    public static List<ItemReg> items() {
        return ITEMS;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 泥土等级查询：压缩泥土方块 → 1-9，其他 null。 */
    public static Integer dirtLevel(BlockState state) {
        return DIRT_LEVELS.get(state.getBlock());
    }

    /** 沙子等级查询：压缩沙子方块 → 1-9，其他 null。 */
    public static Integer sandLevel(BlockState state) {
        return SAND_LEVELS.get(state.getBlock());
    }

    /** 下界土壤等级查询：压缩下界岩 → 1-9，其他 null（原版下界岩/灵魂沙在 soilLevelOf 记 1）。 */
    public static Integer netherSoilLevel(BlockState state) {
        return NETHER_SOIL_LEVELS.get(state.getBlock());
    }

    /** 耕地等级查询：压缩耕地 → 1-9，其他 null。 */
    public static Integer farmlandLevel(BlockState state) {
        return state.getBlock() instanceof CompressedFarmBlock farm ? farm.level() : null;
    }

    public static Block farmland(int level) {
        return FARMLAND.get(level);
    }

    /** 按注册名查物品；无则 null（压缩耕地没有物品）。 */
    public static Item itemByName(String name) {
        for (ItemReg e : ITEMS) {
            if (e.name().equals(name)) {
                return e.item();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 工具

    static final String[] TOOLS = {"pickaxe", "axe", "shovel", "hoe", "sword"};

    /** 原版石级工具 {attackDamage, attackSpeed}。 */
    private static final float[][] STONE_TOOL_STATS = {
        {1.0F, -2.8F}, {7.0F, -3.2F}, {1.5F, -3.0F}, {-1.0F, -2.0F}, {3.0F, -2.4F}
    };

    /** 原版木级工具 {attackDamage, attackSpeed}。 */
    private static final float[][] WOOD_TOOL_STATS = {
        {1.0F, -2.8F}, {6.0F, -3.2F}, {1.5F, -3.0F}, {0.0F, -3.0F}, {3.0F, -2.4F}
    };

    /** 工具只有两条线：圆石线（圆石+深板岩圆石混用）、木线（9 原木混用）。 */
    record ToolLine(String key, ToolKind kind, float[][] stats, float damageBonus, int enchantability) {
    }

    static final List<ToolLine> TOOL_LINES = List.of(
        new ToolLine("cobblestone", ToolKind.STONE, STONE_TOOL_STATS, 1.0F, 5),
        new ToolLine("wood", ToolKind.WOOD, WOOD_TOOL_STATS, 0.0F, 15)
    );

    /** 第 n 重耐久：9ⁿ × 基础；六重起不可破坏。 */
    public static int durabilityFor(int level, ToolKind kind) {
        if (level >= UNBREAKABLE_FROM) {
            return Integer.MAX_VALUE;
        }
        long base = kind == ToolKind.STONE ? 131 : 59;
        return (int) (base * pow9(level));
    }

    /** 石线挖掘等级阶梯：1~6 重保持原版石级，7 重=铁、8 重=钻石、9 重=下界合金。 */
    static TagKey<Block> stoneIncorrectTag(int level) {
        if (level >= 9) {
            return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        }
        if (level == 8) {
            return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }
        if (level == 7) {
            return BlockTags.INCORRECT_FOR_IRON_TOOL;
        }
        return BlockTags.INCORRECT_FOR_STONE_TOOL;
    }

    /**
     * 木线挖掘等级阶梯：木线 = 原版木工具的压缩版，整体比石线低一级 ——
     * 1~6 重保持原版木级，7 重=石级、8 重=铁级、9 重=钻石级。
     */
    static TagKey<Block> woodIncorrectTag(int level) {
        if (level >= 9) {
            return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }
        if (level == 8) {
            return BlockTags.INCORRECT_FOR_IRON_TOOL;
        }
        if (level == 7) {
            return BlockTags.INCORRECT_FOR_STONE_TOOL;
        }
        return BlockTags.INCORRECT_FOR_WOODEN_TOOL;
    }

    // ------------------------------------------------------------------ 构建

    static {
        CompressedContent.init();
    }

    // ------------------------------------------------------------------ 穿戴查询

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** 当前穿戴件溢出防御总和（转抗性提升等级）。 */
    public static int wornOverflow(Player player) {
        int sum = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = ARMOR_INDEX.get(player.getItemBySlot(slot).getItem());
            if (info != null) {
                sum += armorOverflow(info[1], info[0]);
            }
        }
        return sum;
    }

    /** 四件全九重 = 免疫一切伤害（含 /kill）+ 饱食度常满。 */
    /** 当前穿戴件的最高重数（无穿戴 = 0）。 */
    public static int wornMaxLevel(Player player) {
        int max = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = ARMOR_INDEX.get(player.getItemBySlot(slot).getItem());
            if (info != null) {
                max = Math.max(max, info[1]);
            }
        }
        return max;
    }

    /** 四件全穿且每件重数 ≥ minLevel。 */
    public static boolean fullSetAtLeast(Player player, int minLevel) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = ARMOR_INDEX.get(player.getItemBySlot(slot).getItem());
            if (info == null || info[1] < minLevel) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasFullLevel9Armor(Player player) {
        return fullSetAtLeast(player, LEVELS);
    }

    /** 穿戴重数对应的抗性提升等级（0 基）：7 重=1、8 重=2、9 重=3；7 重以下 = -1（无效果）。 */
    public static int resistanceAmplifier(int level) {
        if (level >= LEVELS) {
            return 2;
        }
        return level >= 7 ? level - 7 : -1;
    }

    /** 穿戴件信息 {部位 0-3, 有效重数}；非本模组护甲 = null（木质盔甲记有效重数 = 重数-1）。 */
    public static int[] armorInfoOf(ItemStack stack) {
        return ARMOR_INDEX.get(stack.getItem());
    }

    // ------------------------------------------------------------------ 创造栏

    private static ItemStack icon(String name) {
        for (ItemReg e : ITEMS) {
            if (e.name().equals(name)) {
                return new ItemStack(e.item());
            }
        }
        return ItemStack.EMPTY;
    }

    private static void acceptItems(CreativeModeTab.Output output, Tab tab) {
        for (ItemReg e : ITEMS) {
            if (e.tab() == tab) {
                output.accept(e.item());
            }
        }
    }

    public static final CreativeModeTab TAB_BLOCKS = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
        .title(Component.translatable("itemGroup.compressedblocks.blocks"))
        .icon(() -> icon("1x_cobblestone"))
        .displayItems((parameters, output) -> acceptItems(output, Tab.BLOCKS))
        .build();

    public static final CreativeModeTab TAB_TOOLS = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1)
        .title(Component.translatable("itemGroup.compressedblocks.tools"))
        .icon(() -> icon("9x_wood_pickaxe"))
        .displayItems((parameters, output) -> {
            acceptItems(output, Tab.TOOLS);
            acceptItems(output, Tab.INGREDIENTS);
        })
        .build();

    public static final CreativeModeTab TAB_FOOD = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 2)
        .title(Component.translatable("itemGroup.compressedblocks.food"))
        .icon(() -> icon("3x_beef"))
        .displayItems((parameters, output) -> acceptItems(output, Tab.FOOD))
        .build();

    // "压缩盆栽"创造栏由附属 mod compressedblockspot 独有（含压缩甘蔗/树苗的扫描展示），
    // 主 mod 不再注册 TAB_POTS；Tab.POTS 项仅作归档，附属在场时由附属栏呈现。

    // ------------------------------------------------------------------ 自检

    /** 启动自检：实现见 {@link CompressedSelfTest}；loader 调用点保持原样。 */
    public static void selfTest(ServerLevel level) {
        CompressedSelfTest.selfTest(level);
    }

    private CompressedBlocks() {
    }
}
