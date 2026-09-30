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
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
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

    public static float hardnessFor(int level) {
        return (float) (STORAGE_HARDNESS_BASE * Math.pow(STORAGE_HARDNESS_STEP, level - 1));
    }

    public enum ToolKind { STONE, WOOD }

    /** 创造物品栏落位。 */
    public enum Tab { BLOCKS, TOOLS, INGREDIENTS, FOOD }

    /**
     * 储存方块材料（key/声音/地图色/是否需要正确工具/发光）。19 旧材料 + 24 新建材。
     * 硬度与爆炸抗性统一走公式，不再复制原版数值。
     */
    private record StorageMat(String key, SoundType sound, MapColor color, boolean requiresTool, int light) {
    }

    private static final List<StorageMat> STORAGE = List.of(
        new StorageMat("cobblestone", SoundType.STONE, MapColor.STONE, true, 0),
        new StorageMat("stone", SoundType.STONE, MapColor.STONE, true, 0),
        new StorageMat("cobbled_deepslate", SoundType.DEEPSLATE, MapColor.DEEPSLATE, true, 0),
        new StorageMat("deepslate", SoundType.DEEPSLATE, MapColor.DEEPSLATE, true, 0),
        new StorageMat("oak_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("spruce_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("birch_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("jungle_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("acacia_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("dark_oak_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("mangrove_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("cherry_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("pale_oak_log", SoundType.WOOD, MapColor.WOOD, false, 0),
        new StorageMat("dirt", SoundType.GRAVEL, MapColor.DIRT, false, 0),
        new StorageMat("sand", SoundType.SAND, MapColor.SAND, false, 0),
        new StorageMat("gravel", SoundType.GRAVEL, MapColor.STONE, false, 0),
        new StorageMat("netherrack", SoundType.NETHERRACK, MapColor.NETHER, true, 0),
        new StorageMat("end_stone", SoundType.STONE, MapColor.SAND, true, 0),
        new StorageMat("obsidian", SoundType.STONE, MapColor.COLOR_BLACK, true, 0),
        new StorageMat("granite", SoundType.STONE, MapColor.COLOR_ORANGE, true, 0),
        new StorageMat("diorite", SoundType.STONE, MapColor.QUARTZ, true, 0),
        new StorageMat("andesite", SoundType.STONE, MapColor.STONE, true, 0),
        new StorageMat("calcite", SoundType.CALCITE, MapColor.QUARTZ, true, 0),
        new StorageMat("tuff", SoundType.TUFF, MapColor.COLOR_GRAY, true, 0),
        new StorageMat("sandstone", SoundType.STONE, MapColor.SAND, true, 0),
        new StorageMat("red_sandstone", SoundType.STONE, MapColor.COLOR_ORANGE, true, 0),
        new StorageMat("basalt", SoundType.BASALT, MapColor.COLOR_GRAY, true, 0),
        new StorageMat("blackstone", SoundType.STONE, MapColor.COLOR_BLACK, true, 0),
        new StorageMat("dripstone_block", SoundType.DRIPSTONE_BLOCK, MapColor.COLOR_BROWN, true, 0),
        new StorageMat("terracotta", SoundType.STONE, MapColor.COLOR_ORANGE, true, 0),
        new StorageMat("quartz_block", SoundType.STONE, MapColor.QUARTZ, true, 0),
        new StorageMat("purpur_block", SoundType.STONE, MapColor.COLOR_MAGENTA, true, 0),
        new StorageMat("prismarine", SoundType.STONE, MapColor.COLOR_CYAN, true, 0),
        new StorageMat("amethyst_block", SoundType.AMETHYST, MapColor.COLOR_PURPLE, true, 0),
        new StorageMat("glowstone", SoundType.GLASS, MapColor.SAND, false, 15),
        new StorageMat("clay", SoundType.GRAVEL, MapColor.COLOR_LIGHT_GRAY, false, 0),
        new StorageMat("hay_block", SoundType.GRASS, MapColor.COLOR_YELLOW, false, 0),
        new StorageMat("bone_block", SoundType.STONE, MapColor.SAND, true, 0),
        new StorageMat("moss_block", SoundType.MOSS, MapColor.COLOR_GREEN, false, 0),
        new StorageMat("snow", SoundType.SNOW, MapColor.SNOW, false, 0),
        new StorageMat("ice", SoundType.GLASS, MapColor.COLOR_LIGHT_BLUE, false, 0),
        new StorageMat("packed_ice", SoundType.STONE, MapColor.COLOR_LIGHT_BLUE, false, 0),
        new StorageMat("mud", SoundType.MUD, MapColor.COLOR_GRAY, false, 0)
    );

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

    /** 原版数值：面包 5/6.0、牛肉 8/12.8、西瓜片 2/1.2；N 级压缩 = ×9^N。 */
    private static final List<FoodDef> FOODS = List.of(
        new FoodDef("bread", "Bread", 5.0F, 6.0F),
        new FoodDef("beef", "Beef", 8.0F, 12.8F),
        new FoodDef("melon", "Watermelon", 2.0F, 1.2F)
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
    private static final Map<Integer, Block> FARMLAND = new IdentityHashMap<>();

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
                    .strength(hardnessFor(level), STORAGE_BLAST);
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
                ITEMS.add(new ItemReg(name, item, Tab.FOOD, true));
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
                String enName = c.hasSeeds() ? enFood + " Seeds" : enFood;
                Item item = new CompressedSoilItem(block,
                    new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(seedItem))),
                    level, true, enName);
                BLOCKS.add(new BlockReg(blockName, block, seedItem));
                ITEMS.add(new ItemReg(seedItem, item, Tab.FOOD, false));
                if (c.hasSeeds()) {
                    // 产物：压缩小麦 / 压缩甜菜根（不可种植的纯物品）
                    String produce = p + "_" + c.key();
                    Item produceItem = new Item(new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, id(produce))));
                    ITEMS.add(new ItemReg(produce, produceItem, Tab.FOOD, false));
                }
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
    public static boolean hasFullLevel9Armor(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = ARMOR_INDEX.get(player.getItemBySlot(slot).getItem());
            if (info == null || info[1] < LEVELS) {
                return false;
            }
        }
        return true;
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

    /** 总栏"压缩"：方块 → 食物/农业 → 工具。 */
    public static void acceptMainTab(CreativeModeTab.Output output) {
        acceptItems(output, Tab.BLOCKS);
        acceptItems(output, Tab.FOOD);
        acceptItems(output, Tab.TOOLS);
        acceptItems(output, Tab.INGREDIENTS);
    }

    public static final CreativeModeTab TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
        .title(Component.translatable("itemGroup.compressedblocks"))
        .icon(() -> icon("9x_cobblestone"))
        .displayItems((parameters, output) -> acceptMainTab(output))
        .build();

    public static final CreativeModeTab TAB_BLOCKS = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1)
        .title(Component.translatable("itemGroup.compressedblocks.blocks"))
        .icon(() -> icon("1x_cobblestone"))
        .displayItems((parameters, output) -> acceptItems(output, Tab.BLOCKS))
        .build();

    public static final CreativeModeTab TAB_TOOLS = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 2)
        .title(Component.translatable("itemGroup.compressedblocks.tools"))
        .icon(() -> icon("9x_wood_pickaxe"))
        .displayItems((parameters, output) -> {
            acceptItems(output, Tab.TOOLS);
            acceptItems(output, Tab.INGREDIENTS);
        })
        .build();

    public static final CreativeModeTab TAB_FOOD = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 3)
        .title(Component.translatable("itemGroup.compressedblocks.food"))
        .icon(() -> icon("3x_beef"))
        .displayItems((parameters, output) -> acceptItems(output, Tab.FOOD))
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
        if (BLOCKS.size() != 570) {
            errors.add("block registry size " + BLOCKS.size() + " != 570");
        }
        if (expectItems != 711) {
            errors.add("item registry size " + expectItems + " != 711");
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
            float want = hardnessFor(levelOfName(b.name()));
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
        // 溢出表抽查（护甲防御封顶 10，超出转抗性）
        if (armorOverflow(9, 1) != 4 || armorOverflow(9, 2) != 3 || armorOverflow(6, 1) != 1 || armorOverflow(5, 1) != 0) {
            errors.add("armor overflow table wrong");
        }
        if (errors.isEmpty()) {
            LOGGER.info("SELF-TEST PASS: blocks={} items={} ({} tools, {} armor, {} food)",
                BLOCKS.size(), ITEMS.size(), TOOL_REGS.size(), ARMOR_REGS.size(), FOOD_REGS.size());
        } else {
            throw new IllegalStateException("SELF-TEST FAILED: " + String.join("; ", errors));
        }
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
