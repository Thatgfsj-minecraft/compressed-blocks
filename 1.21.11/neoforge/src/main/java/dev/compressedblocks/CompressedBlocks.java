package dev.compressedblocks;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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

    private static final String[] LEVEL_PREFIX = {
        "1x", "2x", "3x", "4x", "5x", "6x", "7x", "8x", "9x"
    };
    private static final String[] LEVEL_EN_PREFIX = {
        "Compressed", "Double Compressed", "Triple Compressed", "Quadruple Compressed", "Quintuple Compressed",
        "Sextuple Compressed", "Septuple Compressed", "Octuple Compressed", "Nonuple Compressed"
    };

    /** 挖掘速度阶梯：一重=铁 6.0、二重=钻 8.0、三重=金 12.0，四重起 ×1.5 逐级连乘。 */
    private static final float[] SPEED = {6.0F, 8.0F, 12.0F, 18.0F, 27.0F, 40.5F, 60.75F, 91.125F, 136.6875F};

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

    private static StorageMat storageMatOf(String blockName) {
        String mat = blockName.substring(blockName.indexOf('_') + 1);
        for (StorageMat m : STORAGE) {
            if (m.key().equals(mat)) {
                return m;
            }
        }
        throw new IllegalStateException("no StorageMat for " + blockName);
    }

    public enum ToolKind { STONE, WOOD }

    /** 创造物品栏落位。 */
    public enum Tab { BLOCKS, TOOLS, INGREDIENTS, FOOD, POTS }

    /**
     * 储存方块材料（key/英文名/声音/地图色/是否需要正确工具/发光/原版硬度）。
     * 51 材料：19 旧材料 + 8 矿物块 + 24 建材（含火药块——原版无方块形态）。
     * 硬度与爆炸抗性统一走公式；英文名供压缩甘蔗提示拼接（lang 由生成器产出）。
     */
    private record StorageMat(String key, String en, SoundType sound, MapColor color,
                              boolean requiresTool, int light, float vh) {
    }

    private static final List<StorageMat> STORAGE = List.of(
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

    private record WoodDef(String key, String en, String zh) {
    }

    private static final List<WoodDef> WOODS = List.of(
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

    private record CropDef(String key, String en, boolean hasSeeds) {
    }

    private static final List<CropDef> CROPS = List.of(
        new CropDef("wheat", "Wheat", true),
        new CropDef("carrot", "Carrot", false),
        new CropDef("potato", "Potato", false),
        new CropDef("beetroot", "Beetroot", true)
    );

    private record FoodDef(String key, String en, float nutrition, float saturation) {
    }

    /** 原版数值（名称与生/熟数值全部对齐原版）；N 级压缩 = ×9^N。 */
    private static final List<FoodDef> FOODS = List.of(
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
        new FoodDef("baked_potato", "Baked Potato", 5.0F, 6.0F)
    );

    // ------------------------------------------------------------------ 护甲

    public static final ArmorType[] ARMOR_TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
    /** 石制护甲基准=铁级：头 2 / 胸 6 / 腿 5 / 靴 2。 */
    public static final int[] ARMOR_BASE_DEFENSE = {2, 6, 5, 2};
    private static final int[] ARMOR_DURABILITY_BASE = {11, 16, 15, 13};
    private static final int ARMOR_ENCHANTABILITY = 9;

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

    private static long pow9(int level) {
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

    private static final List<BlockReg> BLOCKS = new ArrayList<>();
    private static final List<ItemReg> ITEMS = new ArrayList<>();
    private static final List<BlockReg> STORAGE_BLOCKS = new ArrayList<>();
    private static final List<ToolReg> TOOL_REGS = new ArrayList<>();
    private static final List<ArmorReg> ARMOR_REGS = new ArrayList<>();
    private static final List<FoodReg> FOOD_REGS = new ArrayList<>();
    private static final Map<Item, int[]> ARMOR_INDEX = new IdentityHashMap<>();
    private static final Map<Block, Integer> DIRT_LEVELS = new IdentityHashMap<>();
    private static final Map<Block, Integer> SAND_LEVELS = new IdentityHashMap<>();
    private static final Map<Integer, Block> FARMLAND = new IdentityHashMap<>();

    /** 渲染层注册用分组：cross/crop 类植物 → CUTOUT，树叶 → CUTOUT_MIPPED（客户端入口注册）。 */
    public static final List<Block> SAPLING_BLOCKS = new ArrayList<>();
    public static final List<Block> CANE_BLOCKS = new ArrayList<>();
    public static final List<Block> CROP_BLOCKS = new ArrayList<>();
    public static final List<Block> LEAVES_BLOCKS = new ArrayList<>();

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

    private static final String[] TOOLS = {"pickaxe", "axe", "shovel", "hoe", "sword"};

    /** 原版石级工具 {attackDamage, attackSpeed}。 */
    private static final float[][] STONE_TOOL_STATS = {
        {1.0F, -2.8F}, {7.0F, -3.2F}, {1.5F, -3.0F}, {-1.0F, -2.0F}, {3.0F, -2.4F}
    };

    /** 原版木级工具 {attackDamage, attackSpeed}。 */
    private static final float[][] WOOD_TOOL_STATS = {
        {1.0F, -2.8F}, {6.0F, -3.2F}, {1.5F, -3.0F}, {0.0F, -3.0F}, {3.0F, -2.4F}
    };

    /** 工具只有两条线：圆石线（圆石+深板岩圆石混用）、木线（9 原木混用）。 */
    private record ToolLine(String key, ToolKind kind, float[][] stats, float damageBonus, int enchantability) {
    }

    private static final List<ToolLine> TOOL_LINES = List.of(
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

    /** 挖掘等级阶梯：7 重=铁、8 重=钻石、9 重=下界合金；其余保持原版石级/木级。 */
    private static TagKey<Block> incorrectTagFor(int level, boolean stone) {
        if (level >= 9) {
            return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        }
        if (level == 8) {
            return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }
        if (level == 7) {
            return BlockTags.INCORRECT_FOR_IRON_TOOL;
        }
        return stone ? BlockTags.INCORRECT_FOR_STONE_TOOL : BlockTags.INCORRECT_FOR_WOODEN_TOOL;
    }

    private static Item buildTool(ToolLine line, int level, int toolIndex, String name) {
        boolean stone = line.kind() == ToolKind.STONE;
        float[] stats = line.stats()[toolIndex];
        ToolMaterial material = new ToolMaterial(
            incorrectTagFor(level, stone),
            durabilityFor(level, line.kind()),
            SPEED[level - 1],
            line.damageBonus() + level,
            line.enchantability(),
            TagKey.create(Registries.ITEM, id("repair_" + line.key() + "_tool_" + LEVEL_PREFIX[level - 1]))
        );
        Item.Properties props = new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, id(name)));
        if (level >= UNBREAKABLE_FROM) {
            props = props.component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }
        boolean unbreakable = level >= UNBREAKABLE_FROM;
        Item item = switch (TOOLS[toolIndex]) {
            case "pickaxe" -> new Item(props.pickaxe(material, stats[0], stats[1]));
            case "sword" -> new Item(props.sword(material, stats[0], stats[1]));
            case "axe" -> new AxeItem(material, stats[0], stats[1], props);
            case "shovel" -> new ShovelItem(material, stats[0], stats[1], props);
            case "hoe" -> new CompressedHoeItem(material, stats[0], stats[1], level, props);
            default -> throw new IllegalStateException("unknown tool: " + TOOLS[toolIndex]);
        };
        if (level == LEVELS) {
            CompressedHooks.registerLevel9Tool(item);
        }
        double damage = stats[0] + line.damageBonus() + level;
        TOOL_REGS.add(new ToolReg(name, item, durabilityFor(level, line.kind()), SPEED[level - 1], unbreakable, damage));
        return item;
    }

    // ------------------------------------------------------------------ 树苗

    private static ResourceKey<ConfiguredFeature<?, ?>> featureKey(String feature, String prefix) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, id(feature + "_" + prefix));
    }

    /** 树苗生长器：引用数据包里的压缩树特征（原版形状 + 同重数压缩原木/树叶）。 */
    private static TreeGrower grower(String wood, int level) {
        String name = MOD_ID + ":" + wood + "_" + level;
        String p = LEVEL_PREFIX[level - 1];
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> mega = Optional.empty();
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMega = Optional.empty();
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree = Optional.empty();
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree = Optional.empty();
        float secondaryChance = 0.0F;
        switch (wood) {
            case "oak" -> {
                secondaryChance = 0.1F;
                tree = Optional.of(featureKey("oak", p));
                secondaryTree = Optional.of(featureKey("fancy_oak", p));
            }
            case "spruce" -> {
                secondaryChance = 0.5F;
                mega = Optional.of(featureKey("mega_spruce", p));
                secondaryMega = Optional.of(featureKey("mega_pine", p));
                tree = Optional.of(featureKey("spruce", p));
            }
            case "jungle" -> {
                mega = Optional.of(featureKey("mega_jungle", p));
                tree = Optional.of(featureKey("jungle", p));
            }
            case "mangrove" -> {
                secondaryChance = 0.85F;
                tree = Optional.of(featureKey("mangrove", p));
                secondaryTree = Optional.of(featureKey("tall_mangrove", p));
            }
            case "dark_oak" -> mega = Optional.of(featureKey("dark_oak", p));
            case "pale_oak" -> mega = Optional.of(featureKey("pale_oak", p));
            default -> tree = Optional.of(featureKey(wood, p));
        }
        return new TreeGrower(name, secondaryChance, mega, secondaryMega, tree, secondaryTree,
            Optional.empty(), Optional.empty());
    }

    private static void registerCane(String name, int level, String enName) {
        Block block = new CompressedCaneBlock(level, BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id(name)))
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY));
        Item item = new CompressedCaneItem(block,
            new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name)))
                .useBlockDescriptionPrefix(),
            level, LEVEL_EN_PREFIX[level - 1] + " " + enName);
        BLOCKS.add(new BlockReg(name, block, name));
        ITEMS.add(new ItemReg(name, item, Tab.POTS, true));
        CANE_BLOCKS.add(block);
    }

    // ------------------------------------------------------------------ 构建

    static {
        // 1. 储存方块（43 材料 × 9 重）：统一硬度公式 + 1200 爆炸抗性
        for (StorageMat m : STORAGE) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = LEVEL_PREFIX[level - 1] + "_" + m.key();
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                    .mapColor(m.color())
                    .sound(m.sound())
                    .strength(hardnessFor(m, level), STORAGE_BLAST);
                if (m.requiresTool()) {
                    props = props.requiresCorrectToolForDrops();
                }
                if (m.light() > 0) {
                    props = props.lightLevel(state -> m.light());
                }
                Block block = new Block(props);
                BlockItem item = new BlockItem(block,
                    new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, id(name)))
                        .useBlockDescriptionPrefix());
                BLOCKS.add(new BlockReg(name, block, name));
                ITEMS.add(new ItemReg(name, item, Tab.BLOCKS, true));
                STORAGE_BLOCKS.add(BLOCKS.get(BLOCKS.size() - 1));
                if (m.key().equals("dirt")) {
                    DIRT_LEVELS.put(block, level);
                }
                if (m.key().equals("sand")) {
                    SAND_LEVELS.put(block, level);
                }
            }
        }
        // 2. 树叶（9 木 × 9 重）：原版凋落算法
        for (WoodDef w : WOODS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = LEVEL_PREFIX[level - 1] + "_" + w.key() + "_leaves";
                Block block = new CompressedLeavesBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                    .mapColor(MapColor.PLANT)
                    .strength(0.2F, STORAGE_BLAST)
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .noOcclusion());
                LEAVES_BLOCKS.add(block);
                BlockItem item = new BlockItem(block,
                    new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, id(name)))
                        .useBlockDescriptionPrefix());
                BLOCKS.add(new BlockReg(name, block, name));
                ITEMS.add(new ItemReg(name, item, Tab.BLOCKS, true));
            }
        }
        // 3. 树苗（9 木 × 9 重）：只能种在等级足够的压缩泥土上
        for (WoodDef w : WOODS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = LEVEL_PREFIX[level - 1] + "_" + w.key() + "_sapling";
                Block block = new CompressedSaplingBlock(grower(w.key(), level), level,
                    BlockBehaviour.Properties.of()
                        .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                        .mapColor(MapColor.PLANT)
                        .strength(0.0F, STORAGE_BLAST)
                        .noCollision()
                        .randomTicks()
                        .sound(SoundType.GRASS));
                String enName = LEVEL_EN_PREFIX[level - 1] + " Compressed " + w.en() + " Sapling";
                Item item = new CompressedSoilItem(block,
                    new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, id(name)))
                        .useBlockDescriptionPrefix(),
                    level, false, enName);
                BLOCKS.add(new BlockReg(name, block, name));
                ITEMS.add(new ItemReg(name, item, Tab.POTS, true));
                SAPLING_BLOCKS.add(block);
            }
        }
        // 4. 压缩耕地（9 级）：无物品；锄 N 级压缩泥土需 N 级+锄头
        for (int level = 1; level <= LEVELS; level++) {
            String name = LEVEL_PREFIX[level - 1] + "_farmland";
            Block block = new CompressedFarmBlock(level, BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                .mapColor(MapColor.DIRT)
                .strength(0.6F, STORAGE_BLAST)
                .sound(SoundType.GRAVEL));
            BLOCKS.add(new BlockReg(name, block, null));
            FARMLAND.put(level, block);
        }
        // 5. 作物（4 种 × 3 级）：只能种在等级足够的压缩耕地上
        for (CropDef c : CROPS) {
            for (int level = 1; level <= CROP_MAX_LEVEL; level++) {
                String p = LEVEL_PREFIX[level - 1];
                String blockName = p + "_" + c.key() + "_plant";
                String seedItem = c.hasSeeds() ? p + "_" + c.key() + "_seeds" : p + "_" + c.key();
                String enFood = LEVEL_EN_PREFIX[level - 1] + " Compressed " + c.en();
                Block block = new CompressedCropBlock(level, seedItem, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(blockName)))
                    .mapColor(MapColor.PLANT)
                    .strength(0.0F, STORAGE_BLAST)
                    .noCollision()
                    .randomTicks()
                    .sound(SoundType.CROP));
                CROP_BLOCKS.add(block);
                String enName = c.hasSeeds() ? enFood + " Seeds" : enFood;
                // 压缩胡萝卜/土豆同时是压缩食物：随时可吃 + 溢出转回升（数值 = 原版 × 9^重）
                int foodN = c.hasSeeds() ? 0
                    : (int) ((c.key().equals("carrot") ? 3 : 1) * pow9(level));
                float foodS = c.hasSeeds() ? 0.0F
                    : (c.key().equals("carrot") ? 1.8F : 0.3F) * pow9(level);
                Item.Properties props = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(seedItem)));
                if (foodN > 0) {
                    props = props.food(new FoodProperties(foodN, foodS, true));
                }
                Item item = new CompressedSoilItem(block, props, level, true, enName, foodN);
                BLOCKS.add(new BlockReg(blockName, block, seedItem));
                ITEMS.add(new ItemReg(seedItem, item, Tab.FOOD, false));
                if (foodN > 0) {
                    FOOD_REGS.add(new FoodReg(seedItem, item, foodN, foodS));
                }
                if (c.hasSeeds()) {
                    // 产物：压缩小麦 / 压缩甜菜根（不可种植的纯物品）
                    String produce = p + "_" + c.key();
                    Item produceItem = new Item(new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, id(produce))));
                    ITEMS.add(new ItemReg(produce, produceItem, Tab.FOOD, false));
                }
            }
        }
        // 5b. 压缩甘蔗（纯甘蔗 + 51 材料各一条 × 9 重）：只能种在 N 重以上压缩泥土/沙子，生长同原版
        for (int level = 1; level <= LEVELS; level++) {
            registerCane(LEVEL_PREFIX[level - 1] + "_cane", level, "Sugar Cane");
        }
        for (StorageMat m : STORAGE) {
            String key = caneKey(m.key());
            for (int level = 1; level <= LEVELS; level++) {
                registerCane(LEVEL_PREFIX[level - 1] + "_" + key + "_cane", level,
                    caneEnName(m.en()) + " Cane");
            }
        }
        // 5c. 压缩盆栽（普通/漏斗）：盆内存土壤与作物（Botany Pots 机制），漏斗盆栽成熟自动收割进下方容器
        POT = new CompressedPotBlock(false, BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id("pot")))
            .mapColor(MapColor.TERRACOTTA_ORANGE)
            .strength(1.5F, STORAGE_BLAST)
            .sound(SoundType.STONE)
            .noOcclusion());
        HOPPER_POT = new CompressedPotBlock(true, BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id("hopper_pot")))
            .mapColor(MapColor.METAL)
            .strength(1.5F, STORAGE_BLAST)
            .sound(SoundType.STONE)
            .noOcclusion());
        // POT_TYPE 由各加载器入口构建后注入（1.21.11 原版构造器私有，Fabric/NeoForge 各有公开构建路径）
        for (String name : new String[] {"pot", "hopper_pot"}) {
            Block block = name.equals("pot") ? POT : HOPPER_POT;
            BlockItem item = new BlockItem(block,
                new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))
                    .useBlockDescriptionPrefix());
            BLOCKS.add(new BlockReg(name, block, name));
            ITEMS.add(new ItemReg(name, item, Tab.POTS, true));
        }
        // 5d. 更多压缩种子（原版其余种子 × 3 级）：盆栽通用兼容可直接种植
        String[][] extraSeeds = {
            {"pumpkin_seeds", "Pumpkin Seeds"},
            {"melon_seeds", "Melon Seeds"},
            {"torchflower_seeds", "Torchflower Seeds"},
            {"pitcher_pod", "Pitcher Pod"}
        };
        for (String[] s : extraSeeds) {
            for (int level = 1; level <= CROP_MAX_LEVEL; level++) {
                String name = LEVEL_PREFIX[level - 1] + "_" + s[0];
                Item item = new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name))));
                ITEMS.add(new ItemReg(name, item, Tab.FOOD, false));
            }
        }
        // 6. 压缩食物（3 种 × 3 级）：canAlwaysEat，溢出转回升
        for (FoodDef f : FOODS) {
            for (int level = 1; level <= FOOD_MAX_LEVEL; level++) {
                String name = LEVEL_PREFIX[level - 1] + "_" + f.key();
                int nutrition = (int) (f.nutrition() * pow9(level));
                float saturation = f.saturation() * pow9(level);
                Item item = new CompressedFoodItem(
                    new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, id(name)))
                        .food(new FoodProperties(nutrition, saturation, true)),
                    level, nutrition);
                ITEMS.add(new ItemReg(name, item, Tab.FOOD, false));
                FOOD_REGS.add(new FoodReg(name, item, nutrition, saturation));
            }
        }
        // 7. 压缩护甲（4 件 × 9 级）：石制，铁级基准；九重满套免疫一切伤害
        for (int level = 1; level <= LEVELS; level++) {
            String p = LEVEL_PREFIX[level - 1];
            long mult = level >= UNBREAKABLE_FROM ? Integer.MAX_VALUE / 16L : pow9(level);
            Map<ArmorType, Integer> defense = Map.of(
                ArmorType.HELMET, armorDefense(level, 0),
                ArmorType.CHESTPLATE, armorDefense(level, 1),
                ArmorType.LEGGINGS, armorDefense(level, 2),
                ArmorType.BOOTS, armorDefense(level, 3));
            ArmorMaterial material = new ArmorMaterial(
                (int) mult,
                defense,
                ARMOR_ENCHANTABILITY,
                SoundEvents.ARMOR_EQUIP_IRON,
                0.0F,
                0.0F,
                TagKey.create(Registries.ITEM, id("repair_stone_armor_" + p)),
                ResourceKey.create(EquipmentAssets.ROOT_ID, id("stone_" + p))
            );
            for (int piece = 0; piece < 4; piece++) {
                String name = p + "_stone_" + switch (piece) {
                    case 0 -> "helmet";
                    case 1 -> "chestplate";
                    case 2 -> "leggings";
                    default -> "boots";
                };
                Item.Properties props = new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))
                    .humanoidArmor(material, ARMOR_TYPES[piece]);
                if (level >= UNBREAKABLE_FROM) {
                    props = props.component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                }
                Item item = new Item(props);
                ITEMS.add(new ItemReg(name, item, Tab.TOOLS, false));
                ArmorReg reg = new ArmorReg(name, item, piece, level);
                ARMOR_REGS.add(reg);
                ARMOR_INDEX.put(item, new int[]{piece, level});
            }
        }
        // 7b. 压缩木质盔甲（4 件 × 9 重）：防御/耐久与石甲相同；效果按有效重数（重数-1）结算；
        //     不毁仍从 6 重开始；9 重木甲有效 8 重（头盔水下呼吸+夜视、护腿抗性3、胸甲飞行），无 9 重石甲的不死/急迫档
        for (int level = 1; level <= LEVELS; level++) {
            String p = LEVEL_PREFIX[level - 1];
            long mult = level >= UNBREAKABLE_FROM ? Integer.MAX_VALUE / 16L : pow9(level);
            Map<ArmorType, Integer> defense = Map.of(
                ArmorType.HELMET, armorDefense(level, 0),
                ArmorType.CHESTPLATE, armorDefense(level, 1),
                ArmorType.LEGGINGS, armorDefense(level, 2),
                ArmorType.BOOTS, armorDefense(level, 3));
            ArmorMaterial woodMaterial = new ArmorMaterial(
                (int) mult,
                defense,
                ARMOR_ENCHANTABILITY,
                SoundEvents.ARMOR_EQUIP_LEATHER,
                0.0F,
                0.0F,
                TagKey.create(Registries.ITEM, id("repair_wood_armor_" + p)),
                ResourceKey.create(EquipmentAssets.ROOT_ID, id("wood_" + p))
            );
            for (int piece = 0; piece < 4; piece++) {
                String name = p + "_wood_" + switch (piece) {
                    case 0 -> "helmet";
                    case 1 -> "chestplate";
                    case 2 -> "leggings";
                    default -> "boots";
                };
                Item.Properties props = new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))
                    .humanoidArmor(woodMaterial, ARMOR_TYPES[piece]);
                if (level >= UNBREAKABLE_FROM) {
                    props = props.component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                }
                Item item = new Item(props);
                ITEMS.add(new ItemReg(name, item, Tab.TOOLS, false));
                ArmorReg reg = new ArmorReg(name, item, piece, level);
                ARMOR_REGS.add(reg);
                ARMOR_INDEX.put(item, new int[]{piece, Math.max(1, level - 1)});
            }
        }
        // 8. 工具（2 线 × 5 类 × 9 重）
        for (ToolLine line : TOOL_LINES) {
            for (int t = 0; t < TOOLS.length; t++) {
                for (int level = 1; level <= LEVELS; level++) {
                    String name = LEVEL_PREFIX[level - 1] + "_" + line.key() + "_" + TOOLS[t];
                    Item item = buildTool(line, level, t, name);
                    ITEMS.add(new ItemReg(name, item, Tab.TOOLS, false));
                }
            }
        }
        // 9. 压缩木棍
        for (int level = 1; level <= LEVELS; level++) {
            String name = LEVEL_PREFIX[level - 1] + "_stick";
            ITEMS.add(new ItemReg(name,
                new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))),
                Tab.INGREDIENTS, false));
        }
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

    public static final CreativeModeTab TAB_POTS = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 3)
        .title(Component.translatable("itemGroup.compressedblocks.pots"))
        .icon(() -> icon("pot"))
        .displayItems((parameters, output) -> acceptItems(output, Tab.POTS))
        .build();

    // ------------------------------------------------------------------ 自检

    private static final BlockPos CHECK_POS = new BlockPos(0, 100, 0);

    private static TagKey<Item> vanillaItemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(path));
    }

    private static void assertTier(List<String> errors, String name, Block state, boolean expectHarvest) {
        Item found = null;
        for (ItemReg e : ITEMS) {
            if (e.name().equals(name)) {
                found = e.item();
                break;
            }
        }
        if (found == null) {
            errors.add("missing item for tier check: " + name);
            return;
        }
        boolean got = new ItemStack(found).isCorrectToolForDrops(state.defaultBlockState());
        if (got != expectHarvest) {
            errors.add(name + " harvest " + state + " = " + got + ", want " + expectHarvest);
        }
    }

    /**
     * 启动自检：注册计数、翻译键、硬度/爆炸抗性公式、护甲防御/溢出/耐久/附魔、工具耐久/速度/伤害、
     * 食物数值、标签族（#logs/#leaves/enchantable）。任何版本升级/映射变化都会在这里第一时间爆掉。
     */
    public static void selfTest(ServerLevel level) {
        List<String> errors = new ArrayList<>();
        long blockCount = BuiltInRegistries.BLOCK.keySet().stream().filter(i -> i.getNamespace().equals(MOD_ID)).count();
        long itemCount = BuiltInRegistries.ITEM.keySet().stream().filter(i -> i.getNamespace().equals(MOD_ID)).count();
        int expectItems = ITEMS.size();
        if (blockCount != BLOCKS.size()) {
            errors.add("block count " + blockCount + " != " + BLOCKS.size());
        }
        if (itemCount != expectItems) {
            errors.add("item count " + itemCount + " != " + expectItems);
        }
        if (BLOCKS.size() != 1112) {
            errors.add("block registry size " + BLOCKS.size() + " != 1112");
        }
        if (expectItems != 1346) {
            errors.add("item registry size " + expectItems + " != 1346");
        }
        // 方块：注册、翻译键、物品映射
        for (BlockReg b : BLOCKS) {
            if (BuiltInRegistries.BLOCK.get(id(b.name())).map(h -> h.value() != b.block()).orElse(true)) {
                errors.add("block not registered: " + b.name());
            }
            if (b.itemName() == null) {
                if (BuiltInRegistries.ITEM.get(id(b.name())).isPresent()) {
                    errors.add("farmland should have no item: " + b.name());
                }
                continue;
            }
            boolean itemMismatch = BuiltInRegistries.ITEM.get(id(b.itemName()))
                .map(h -> !(h.value() instanceof BlockItem bi) || bi.getBlock() != b.block())
                .orElse(true);
            if (itemMismatch) {
                errors.add("block item mismatch: " + b.name());
            }
            // 作物等方块物品 id 与方块 id 不同，走 item. 前缀；其余方块物品随方块走 block. 前缀
            String wantKey = b.itemName().equals(b.name())
                ? "block." + MOD_ID + "." + b.name()
                : "item." + MOD_ID + "." + b.itemName();
            String key = BuiltInRegistries.ITEM.get(id(b.itemName())).map(h -> h.value().getDescriptionId()).orElse("");
            if (!key.equals(wantKey)) {
                errors.add("block item translation key mismatch: " + b.name() + " -> " + key);
            }
        }
        // 储存方块：硬度公式 + 爆炸抗性 + 荧石发光
        for (BlockReg b : STORAGE_BLOCKS) {
            float hardness = b.block().defaultBlockState().getDestroySpeed(level, CHECK_POS);
            float want = hardnessFor(storageMatOf(b.name()), levelOfName(b.name()));
            if (Math.abs(hardness - want) > 0.01F) {
                errors.add(b.name() + " hardness " + hardness + " != " + want);
            }
            if (Math.abs(b.block().getExplosionResistance() - STORAGE_BLAST) > 0.01F) {
                errors.add(b.name() + " blast " + b.block().getExplosionResistance() + " != " + STORAGE_BLAST);
            }
        }
        Block glowstone = blockByName("9x_glowstone");
        if (glowstone != null && glowstone.defaultBlockState().getLightEmission() != 15) {
            errors.add("glowstone light != 15");
        }
        // 标签族：树叶凋落/工具材料混用/附魔的前提
        Block oakLog = blockByName("1x_oak_log");
        Block oakLeaves = blockByName("1x_oak_leaves");
        if (oakLog == null || !oakLog.defaultBlockState().is(BlockTags.LOGS)) {
            errors.add("compressed log not in #minecraft:logs");
        }
        if (oakLeaves == null || !oakLeaves.defaultBlockState().is(BlockTags.LEAVES)) {
            errors.add("compressed leaves not in #minecraft:leaves");
        }
        // 物品：注册、翻译键、耐久/速度/伤害（工具）、护甲属性、食物数值、附魔标签
        TagKey<Item> enchantableArmor = vanillaItemTag("enchantable/armor");
        TagKey<Item> durability = vanillaItemTag("enchantable/durability");
        TagKey<Item> mining = vanillaItemTag("enchantable/mining");
        TagKey<Item> sharpWeapon = vanillaItemTag("enchantable/sharp_weapon");
        for (ItemReg e : ITEMS) {
            if (BuiltInRegistries.ITEM.get(id(e.name())).map(h -> h.value() != e.item()).orElse(true)) {
                errors.add("item not registered: " + e.name());
            }
            String wantKey = (e.blockKey() ? "block." : "item.") + MOD_ID + "." + e.name();
            if (!e.item().getDescriptionId().equals(wantKey)) {
                errors.add("item translation key mismatch: " + e.name() + " -> " + e.item().getDescriptionId());
            }
        }
        for (ToolReg t : TOOL_REGS) {
            ItemStack stack = new ItemStack(t.item());
            if (stack.getMaxDamage() != t.durability()) {
                errors.add(t.name() + " durability " + stack.getMaxDamage() + " != " + t.durability());
            }
            boolean unbreakable = stack.get(DataComponents.UNBREAKABLE) != null;
            if (unbreakable != t.unbreakable()) {
                errors.add(t.name() + " unbreakable " + unbreakable + " != " + t.unbreakable());
            }
            Tool tool = stack.get(DataComponents.TOOL);
            if (tool == null) {
                errors.add(t.name() + " missing TOOL component");
            } else {
                float speed = Float.NaN;
                for (Tool.Rule rule : tool.rules()) {
                    if (rule.speed().isPresent()) {
                        speed = rule.speed().get();
                    }
                }
                float wantSpeed = t.name().endsWith("_sword") ? 1.5F : t.speed();
                if (Math.abs(speed - wantSpeed) > 0.001F) {
                    errors.add(t.name() + " speed " + speed + " != " + wantSpeed);
                }
            }
            if (Math.abs(attackDamage(stack) - t.damage()) > 0.001) {
                errors.add(t.name() + " attack damage " + attackDamage(stack) + " != " + t.damage());
            }
            if (!stack.is(durability)) {
                errors.add(t.name() + " not in #enchantable/durability");
            }
            if (t.name().endsWith("_pickaxe") && !stack.is(mining)) {
                errors.add(t.name() + " not in #enchantable/mining");
            }
            if ((t.name().endsWith("_sword") || t.name().endsWith("_axe")) && !stack.is(sharpWeapon)) {
                errors.add(t.name() + " not in #enchantable/sharp_weapon");
            }
        }
        for (ArmorReg a : ARMOR_REGS) {
            ItemStack stack = new ItemStack(a.item());
            if (stack.getMaxDamage() != armorDurability(a.level(), a.piece())) {
                errors.add(a.name() + " durability " + stack.getMaxDamage() + " != " + armorDurability(a.level(), a.piece()));
            }
            float armor = Float.NaN;
            ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (mods != null) {
                for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                    if (entry.attribute().is(Attributes.ARMOR)) {
                        armor = (float) entry.modifier().amount();
                    }
                }
            }
            float want = armorDefense(a.level(), a.piece());
            if (Math.abs(armor - want) > 0.001F) {
                errors.add(a.name() + " defense " + armor + " != " + want);
            }
            Enchantable ench = stack.get(DataComponents.ENCHANTABLE);
            if (ench == null || ench.value() != ARMOR_ENCHANTABILITY) {
                errors.add(a.name() + " enchantability != " + ARMOR_ENCHANTABILITY);
            }
            if (!stack.is(enchantableArmor)) {
                errors.add(a.name() + " not in #enchantable/armor");
            }
            boolean unbreakable = stack.get(DataComponents.UNBREAKABLE) != null;
            if (unbreakable != (a.level() >= UNBREAKABLE_FROM)) {
                errors.add(a.name() + " unbreakable mismatch");
            }
        }
        for (FoodReg f : FOOD_REGS) {
            FoodProperties food = new ItemStack(f.item()).get(DataComponents.FOOD);
            if (food == null) {
                errors.add(f.name() + " missing FOOD component");
                continue;
            }
            if (food.nutrition() != f.nutrition() || !food.canAlwaysEat()) {
                errors.add(f.name() + " food " + food.nutrition() + "/" + food.saturation() + " != " + f.nutrition());
            }
            if (Math.abs(food.saturation() - f.saturation()) > 0.01F) {
                errors.add(f.name() + " saturation " + food.saturation() + " != " + f.saturation());
            }
        }
        // 挖掘等级阶梯：6x 仍为石级，7x=铁，8x/9x=钻石级及以上
        assertTier(errors, "1x_cobblestone_pickaxe", Blocks.DIAMOND_ORE, false);
        assertTier(errors, "6x_cobblestone_pickaxe", Blocks.DIAMOND_ORE, false);
        assertTier(errors, "7x_cobblestone_pickaxe", Blocks.DIAMOND_ORE, true);
        assertTier(errors, "7x_cobblestone_pickaxe", Blocks.OBSIDIAN, false);
        assertTier(errors, "8x_cobblestone_pickaxe", Blocks.OBSIDIAN, true);
        assertTier(errors, "9x_cobblestone_pickaxe", Blocks.OBSIDIAN, true);
        // 效果阶梯抽查：7/8/9 重 → 护腿抗性 1/2/3 级，6 重及以下无效果
        if (resistanceAmplifier(7) != 0 || resistanceAmplifier(8) != 1
            || resistanceAmplifier(9) != 2
            || resistanceAmplifier(6) != -1 || resistanceAmplifier(5) != -1) {
            errors.add("armor effect ladder wrong");
        }
        selfTestPotFlow(level, errors);
        if (errors.isEmpty()) {
            LOGGER.info("SELF-TEST PASS: blocks={} items={} ({} tools, {} armor, {} food)",
                BLOCKS.size(), ITEMS.size(), TOOL_REGS.size(), ARMOR_REGS.size(), FOOD_REGS.size());
        } else {
            throw new IllegalStateException("SELF-TEST FAILED: " + String.join("; ", errors));
        }
    }

    /**
     * 盆栽闭环：空服无玩家时区块不计时（边界加载），RCON tick sprint 驱不动方块实体，
     * 故在自检里直接驱动 serverTick 走完一茬：漏斗盆栽应自动收割（甘蔗 3-5×入箱、小麦走原版
     * 战利品表入箱）并自动补种（进度清零、作物保留）。
     */
    private static void selfTestPotFlow(ServerLevel level, List<String> errors) {
        Block hopperPot = blockByName("hopper_pot");
        if (hopperPot == null) {
            errors.add("hopper_pot missing");
            return;
        }
        harvestFlow(level, errors, "1x_cane", "1x_cane", "1x_cane", 2);
        harvestFlow(level, errors, "1x_wheat_plant", "1x_wheat_seeds", "1x_wheat", 1);
    }

    private static void harvestFlow(ServerLevel level, List<String> errors, String plantName,
                                    String seedName, String expectItem, int minCount) {
        Block potBlock = blockByName("hopper_pot");
        BlockPos potPos = new BlockPos(4 + expectItem.length(), 90, 4);
        level.setBlock(potPos.below(), Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(potPos, potBlock.defaultBlockState(), 3);
        CompressedPotBlockEntity pot = (CompressedPotBlockEntity) ((EntityBlock) potBlock).newBlockEntity(
            potPos, potBlock.defaultBlockState());
        pot.setLevel(level);
        pot.setSoil(blockByName("1x_dirt").defaultBlockState());
        pot.setPlant(blockByName(plantName).defaultBlockState(), MOD_ID + ":" + seedName);
        for (int i = 0; i < pot.requiredGrowth(); i++) {
            CompressedPotBlockEntity.serverTick(level, potPos, potBlock.defaultBlockState(), pot);
        }
        int got = 0;
        if (level.getBlockEntity(potPos.below()) instanceof net.minecraft.world.Container chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack slot = chest.getItem(i);
                if (slot.is(itemByName(expectItem))) {
                    got += slot.getCount();
                }
            }
        }
        if (got < minCount) {
            errors.add("pot auto-harvest " + plantName + " -> " + expectItem + " x" + got + " (want >= " + minCount + ")");
        }
        if (pot.plant() == null || pot.growthFraction() != 0.0F) {
            errors.add("pot did not replant after harvest: " + plantName);
        }
        level.setBlock(potPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(potPos.below(), Blocks.AIR.defaultBlockState(), 3);
    }

    private static double attackDamage(ItemStack stack) {
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                    return entry.modifier().amount();
                }
            }
        }
        return Double.NaN;
    }

    private static int levelOfName(String name) {
        return switch (name.charAt(1)) {
            case 'x' -> Integer.parseInt(name.substring(0, 1));
            default -> Integer.parseInt(name.substring(0, 2));
        };
    }

    private static Block blockByName(String name) {
        for (BlockReg b : BLOCKS) {
            if (b.name().equals(name)) {
                return b.block();
            }
        }
        return null;
    }

    private CompressedBlocks() {
    }
}
