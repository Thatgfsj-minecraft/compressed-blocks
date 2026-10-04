package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.ToolType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 压缩方块核心（1.16.5 Forge 移植）：纯数据驱动。
 * 方块/物品实例在此集中构建，注册动作由 forge 入口循环 Registry.register 完成。
 *
 * 与 1.21.11 的差异：
 * - 材料集 39 种（剔除 1.17+ 方块：深板岩系/铜块/红树樱花苍白原木/方解石/凝灰岩/钟乳石/紫水晶/苔藓/泥巴）；
 * - 树 6 种（原版 1.16.5 只有橡/云杉/白桦/丛林/金合欢/深色橡）；
 * - 更多压缩种子只有南瓜/西瓜（火把花/瓶子草是 1.20 内容）；
 * - 挖掘档位走 Forge 的 ToolType + harvestLevel（1.16.5 无 needs_* 判定链）；
 * - 不可破坏 = 耐久 Integer.MAX_VALUE（1.16.5 无组件系统）；
 * - 树苗生长 = 代码注册的 configured feature（1.16.5 世界生成 JSON 结构不同，见 gen_1165_resources.py 注释）。
 */
public final class CompressedBlocks {
    public static final String MOD_ID = "compressedblocks";

    public static final int LEVELS = 9;
    /** 六重及以上工具/护甲：不可破坏（耐久 MAX_VALUE）。 */
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

    /** 压缩方块硬度：50 起步每重 ×1.2 的挖掘速度阶梯；统一 1200 爆炸抗性。 */
    public static final float STORAGE_BLAST = 1200.0F;

    /** 压缩硬度：L1 = 原版该方块硬度，线性升到 L9 目标（黑曜石类 200、硬类 150、软类 100）。 */
    public static float hardnessFor(StorageMatView m, int level) {
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
     * 储存方块材料视图（入口循环注册用；key/en/声音/颜色/材质/工具/挖掘档/发光/原版硬度）。
     * 39 材料：1.21.11 的 51 种剔除 12 种 1.17+ 材料。
     */
    public interface StorageMatView {
        String key();

        String enName();

        Material material();

        MaterialColor color();

        SoundType sound();

        boolean requiresTool();

        ToolType tool();

        int baseHarvest();

        int light();

        float vh();
    }

    private static final class StorageMat implements StorageMatView {
        final String key;
        final String en;
        final SoundType sound;
        final MaterialColor color;
        final Material material;
        final boolean requiresTool;
        final ToolType tool;
        final int baseHarvest;
        final int light;
        final float vh;

        StorageMat(String key, String en, SoundType sound, MaterialColor color, Material material,
                   boolean requiresTool, ToolType tool, int baseHarvest, int light, float vh) {
            this.key = key;
            this.en = en;
            this.sound = sound;
            this.color = color;
            this.material = material;
            this.requiresTool = requiresTool;
            this.tool = tool;
            this.baseHarvest = baseHarvest;
            this.light = light;
            this.vh = vh;
        }

        @Override public String key() { return key; }
        @Override public String enName() { return en; }
        @Override public Material material() { return material; }
        @Override public MaterialColor color() { return color; }
        @Override public SoundType sound() { return sound; }
        @Override public boolean requiresTool() { return requiresTool; }
        @Override public ToolType tool() { return tool; }
        @Override public int baseHarvest() { return baseHarvest; }
        @Override public int light() { return light; }
        @Override public float vh() { return vh; }
    }

    private static final List<StorageMat> STORAGE = new ArrayList<>();

    private static StorageMat mat(String key, String en, SoundType sound, MaterialColor color,
                                  Material material, boolean requiresTool, ToolType tool,
                                  int baseHarvest, float vh) {
        return mat(key, en, sound, color, material, requiresTool, tool, baseHarvest, 0, vh);
    }

    private static StorageMat mat(String key, String en, SoundType sound, MaterialColor color,
                                  Material material, boolean requiresTool, ToolType tool,
                                  int baseHarvest, int light, float vh) {
        StorageMat m = new StorageMat(key, en, sound, color, material, requiresTool, tool,
            baseHarvest, light, vh);
        STORAGE.add(m);
        return m;
    }

    // 石/矿石族：镐；1-2 重石镐（0）、3-4 重铁镐（1）、5+ 重钻镐（2），黑曜石 3（下界合金级）
    private static final ToolType PICK = ToolType.PICKAXE;
    private static final ToolType SHOVEL = ToolType.SHOVEL;
    private static final ToolType AXE = ToolType.AXE;
    private static final ToolType HOE = ToolType.HOE;
    private static final ToolType NONE = null;

    private static void initMaterials() {
        mat("cobblestone", "Cobblestone", SoundType.STONE, MaterialColor.STONE, Material.STONE, true, PICK, 0, 2F);
        mat("stone", "Stone", SoundType.STONE, MaterialColor.STONE, Material.STONE, true, PICK, 0, 1.5F);
        mat("oak_log", "Oak Log", SoundType.WOOD, MaterialColor.WOOD, Material.WOOD, false, AXE, 0, 2F);
        mat("spruce_log", "Spruce Log", SoundType.WOOD, MaterialColor.WOOD, Material.WOOD, false, AXE, 0, 2F);
        mat("birch_log", "Birch Log", SoundType.WOOD, MaterialColor.WOOD, Material.WOOD, false, AXE, 0, 2F);
        mat("jungle_log", "Jungle Log", SoundType.WOOD, MaterialColor.WOOD, Material.WOOD, false, AXE, 0, 2F);
        mat("acacia_log", "Acacia Log", SoundType.WOOD, MaterialColor.WOOD, Material.WOOD, false, AXE, 0, 2F);
        mat("dark_oak_log", "Dark Oak Log", SoundType.WOOD, MaterialColor.WOOD, Material.WOOD, false, AXE, 0, 2F);
        mat("dirt", "Dirt", SoundType.GRAVEL, MaterialColor.DIRT, Material.DIRT, false, SHOVEL, 0, 0.5F);
        mat("sand", "Sand", SoundType.SAND, MaterialColor.SAND, Material.SAND, false, SHOVEL, 0, 0.5F);
        mat("gravel", "Gravel", SoundType.GRAVEL, MaterialColor.STONE, Material.DIRT, false, SHOVEL, 0, 0.6F);
        mat("netherrack", "Netherrack", SoundType.NETHERRACK, MaterialColor.NETHER, Material.STONE, true, PICK, 0, 0.4F);
        mat("end_stone", "End Stone", SoundType.STONE, MaterialColor.SAND, Material.STONE, true, PICK, 0, 3F);
        mat("obsidian", "Obsidian", SoundType.STONE, MaterialColor.COLOR_BLACK, Material.STONE, true, PICK, 3, 50F);
        mat("coal_block", "Block of Coal", SoundType.STONE, MaterialColor.COLOR_BLACK, Material.STONE, true, PICK, 0, 5F);
        mat("iron_block", "Block of Iron", SoundType.METAL, MaterialColor.METAL, Material.METAL, true, PICK, 1, 5F);
        mat("lapis_block", "Lapis Lazuli Block", SoundType.STONE, MaterialColor.LAPIS, Material.STONE, true, PICK, 1, 3F);
        mat("gold_block", "Block of Gold", SoundType.METAL, MaterialColor.GOLD, Material.METAL, true, PICK, 2, 3F);
        mat("redstone_block", "Block of Redstone", SoundType.STONE, MaterialColor.COLOR_RED, Material.STONE, true, PICK, 1, 5F);
        mat("emerald_block", "Block of Emerald", SoundType.METAL, MaterialColor.EMERALD, Material.METAL, true, PICK, 2, 5F);
        mat("diamond_block", "Block of Diamond", SoundType.METAL, MaterialColor.DIAMOND, Material.METAL, true, PICK, 2, 5F);
        mat("granite", "Granite", SoundType.STONE, MaterialColor.COLOR_ORANGE, Material.STONE, true, PICK, 0, 1.5F);
        mat("diorite", "Diorite", SoundType.STONE, MaterialColor.QUARTZ, Material.STONE, true, PICK, 0, 1.5F);
        mat("andesite", "Andesite", SoundType.STONE, MaterialColor.STONE, Material.STONE, true, PICK, 0, 1.5F);
        mat("sandstone", "Sandstone", SoundType.STONE, MaterialColor.SAND, Material.STONE, true, PICK, 0, 0.8F);
        mat("red_sandstone", "Red Sandstone", SoundType.STONE, MaterialColor.COLOR_ORANGE, Material.STONE, true, PICK, 0, 0.8F);
        mat("basalt", "Basalt", SoundType.STONE, MaterialColor.COLOR_GRAY, Material.STONE, true, PICK, 0, 1.25F);
        mat("blackstone", "Blackstone", SoundType.STONE, MaterialColor.COLOR_BLACK, Material.STONE, true, PICK, 0, 1.5F);
        mat("terracotta", "Terracotta", SoundType.STONE, MaterialColor.COLOR_ORANGE, Material.STONE, true, PICK, 0, 1.25F);
        mat("quartz_block", "Quartz Block", SoundType.STONE, MaterialColor.QUARTZ, Material.STONE, true, PICK, 0, 0.8F);
        mat("purpur_block", "Purpur Block", SoundType.STONE, MaterialColor.COLOR_MAGENTA, Material.STONE, true, PICK, 0, 1.5F);
        mat("prismarine", "Prismarine", SoundType.STONE, MaterialColor.COLOR_CYAN, Material.STONE, true, PICK, 0, 1.5F);
        mat("glowstone", "Glowstone", SoundType.GLASS, MaterialColor.SAND, Material.GLASS, false, NONE, 0, 15, 0.3F);
        mat("clay", "Clay", SoundType.GRAVEL, MaterialColor.COLOR_LIGHT_GRAY, Material.DIRT, false, SHOVEL, 0, 0.6F);
        mat("hay_block", "Hay Bale", SoundType.GRASS, MaterialColor.COLOR_YELLOW, Material.GRASS, false, HOE, 0, 0.5F);
        mat("bone_block", "Bone Block", SoundType.STONE, MaterialColor.SAND, Material.STONE, true, PICK, 0, 2F);
        mat("snow", "Snow Block", SoundType.SNOW, MaterialColor.SNOW, Material.SNOW, false, SHOVEL, 0, 0.2F);
        mat("blue_ice", "Blue Ice", SoundType.GLASS, MaterialColor.COLOR_LIGHT_BLUE, Material.ICE, false, NONE, 0, 2.8F);
        // 原版无方块形态：火药块（自定义方块）
        mat("gunpowder", "Block of Gunpowder", SoundType.SAND, MaterialColor.COLOR_GRAY, Material.SAND, false, SHOVEL, 0, 0.5F);
    }

    /** 甘蔗 id 的材料段：去掉 "_block" 后缀（diamond_block → diamond），与生成器 cane_key 一致。 */
    static String caneKey(String matKey) {
        return matKey.endsWith("_block") ? matKey.substring(0, matKey.length() - 6) : matKey;
    }

    /** 甘蔗英文显示名：材料名去掉 "Block of " 前缀 / " Block" / " Bale" 后缀。 */
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

    private static final class WoodDef {
        final String key;
        final String en;
        final String zh;

        WoodDef(String key, String en, String zh) {
            this.key = key;
            this.en = en;
            this.zh = zh;
        }
    }

    private static final List<WoodDef> WOODS = new ArrayList<>();

    private static final class CropDef {
        final String key;
        final String en;
        final String zh;
        final boolean hasSeeds;

        CropDef(String key, String en, String zh, boolean hasSeeds) {
            this.key = key;
            this.en = en;
            this.zh = zh;
            this.hasSeeds = hasSeeds;
        }
    }

    private static final List<CropDef> CROPS = new ArrayList<>();

    private static final class FoodDef {
        final String key;
        final String en;
        final String zh;
        final float nutrition;
        final float saturation;

        FoodDef(String key, String en, String zh, float nutrition, float saturation) {
            this.key = key;
            this.en = en;
            this.zh = zh;
            this.nutrition = nutrition;
            this.saturation = saturation;
        }
    }

    /** 原版数值；N 级压缩 = ×9^N。 */
    private static final List<FoodDef> FOODS = new ArrayList<>();

    // ------------------------------------------------------------------ 护甲

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

    /** 耐久 = 9^重 × 铁甲基准；六重起不可破坏。 */
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

    public static int armorBaseDurability(int piece) {
        return ARMOR_DURABILITY_BASE[piece];
    }

    public static int armorEnchantability() {
        return ARMOR_ENCHANTABILITY;
    }

    // ------------------------------------------------------------------ 注册表

    /** @param item null = 无物品（压缩耕地）。 */
    public static final class BlockReg {
        public final String name;
        public final Block block;
        public final String itemName;

        public BlockReg(String name, Block block, String itemName) {
            this.name = name;
            this.block = block;
            this.itemName = itemName;
        }
    }

    /** @param blockKey true = 翻译键用 block. 前缀（方块物品同 id），false = item. 前缀。 */
    public static final class ItemReg {
        public final String name;
        public final Item item;
        public final Tab tab;
        public final boolean blockKey;

        public ItemReg(String name, Item item, Tab tab, boolean blockKey) {
            this.name = name;
            this.item = item;
            this.tab = tab;
            this.blockKey = blockKey;
        }
    }

    public static final class ToolReg {
        public final String name;
        public final Item item;
        public final int durability;
        public final float speed;
        public final boolean unbreakable;
        public final double damage;

        public ToolReg(String name, Item item, int durability, float speed, boolean unbreakable, double damage) {
            this.name = name;
            this.item = item;
            this.durability = durability;
            this.speed = speed;
            this.unbreakable = unbreakable;
            this.damage = damage;
        }
    }

    public static final class ArmorReg {
        final String name;
        final Item item;
        final int piece;
        final int level;

        ArmorReg(String name, Item item, int piece, int level) {
            this.name = name;
            this.item = item;
            this.piece = piece;
            this.level = level;
        }
    }

    private static final List<BlockReg> BLOCKS = new ArrayList<>();
    private static final List<ItemReg> ITEMS = new ArrayList<>();
    private static final List<BlockReg> STORAGE_BLOCKS = new ArrayList<>();
    private static final List<ToolReg> TOOL_REGS = new ArrayList<>();
    private static final List<ArmorReg> ARMOR_REGS = new ArrayList<>();
    private static final Map<Item, int[]> ARMOR_INDEX = new IdentityHashMap<>();
    private static final Map<Block, Integer> DIRT_LEVELS = new IdentityHashMap<>();
    private static final Map<Block, Integer> SAND_LEVELS = new IdentityHashMap<>();
    private static final Map<Block, Integer> NETHER_SOIL_LEVELS = new IdentityHashMap<>();
    private static final Map<Integer, Block> FARMLAND = new HashMap<>();

    /** 树苗（客户端渲染层免注册：1.16.5 用模型 render_type 字段，这里仅为查询保留）。 */
    public static final List<Block> SAPLING_BLOCKS = new ArrayList<>();
    public static final List<Block> LEAVES_BLOCKS = new ArrayList<>();

    // 压缩箱子/压缩潜影盒（单一等级，243 格滚动存储）
    public static CompressedChestBlock COMPRESSED_CHEST;
    public static CompressedShulkerBlock COMPRESSED_SHULKER;

    /** 压缩金属包：22 材质 × 9 级。 */
    public static final List<String> COMPAT_METAL_KEYS = new ArrayList<>();

    public static List<BlockReg> blocks() {
        return BLOCKS;
    }

    public static List<ItemReg> items() {
        return ITEMS;
    }

    public static List<ToolReg> toolRegs() {
        return TOOL_REGS;
    }

    public static List<ArmorReg> armorRegs() {
        return ARMOR_REGS;
    }

    /** 泥土等级查询：压缩泥土方块 → 1-9，其他 null。 */
    public static Integer dirtLevel(net.minecraft.world.level.block.state.BlockState state) {
        return DIRT_LEVELS.get(state.getBlock());
    }

    /** 沙子等级查询。 */
    public static Integer sandLevel(net.minecraft.world.level.block.state.BlockState state) {
        return SAND_LEVELS.get(state.getBlock());
    }

    /** 下界土壤等级查询：压缩下界岩 → 1-9。 */
    public static Integer netherSoilLevel(net.minecraft.world.level.block.state.BlockState state) {
        return NETHER_SOIL_LEVELS.get(state.getBlock());
    }

    /** 耕地等级查询：压缩耕地 → 1-9，其他 null。 */
    public static Integer farmlandLevel(net.minecraft.world.level.block.state.BlockState state) {
        if (state.getBlock() instanceof CompressedFarmBlock) {
            return ((CompressedFarmBlock) state.getBlock()).level();
        }
        return null;
    }

    public static Block farmland(int level) {
        return FARMLAND.get(level);
    }

    /** 按注册名查物品；无则 null（压缩耕地没有物品）。 */
    public static Item itemByName(String name) {
        for (ItemReg e : ITEMS) {
            if (e.name.equals(name)) {
                return e.item;
            }
        }
        return null;
    }

    /** 按注册名查方块；无则 null。 */
    public static Block blockByName(String name) {
        for (BlockReg b : BLOCKS) {
            if (b.name.equals(name)) {
                return b.block;
            }
        }
        return null;
    }

    /** 回填方块登记的物品名（作物方块在物品构建前登记为 null）。 */
    public static void rewriteBlockItemName(String blockName, String itemName) {
        for (int i = 0; i < BLOCKS.size(); i++) {
            BlockReg b = BLOCKS.get(i);
            if (b.name.equals(blockName)) {
                BLOCKS.set(i, new BlockReg(b.name, b.block, itemName));
                return;
            }
        }
    }

    /** 物品翻译键（lang 生成与自检用）。 */
    public static String translationKeyOf(ItemReg e) {
        return (e.blockKey ? "block." : "item.") + MOD_ID + "." + e.name;
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

    /** 工具只有两条线：圆石线、木线（9 原木混用）。 */
    /** 工具线视图（入口循环注册用）。 */
    public interface ToolLineView {
        String key();

        ToolKind kind();

        int enchantability();

        float baseDamage(int toolIndex);

        float speed(int toolIndex);

        float damageBonus();
    }

    private static final class ToolLine implements ToolLineView {
        final String key;
        final ToolKind kind;
        final float[][] stats;
        final float damageBonus;
        final int enchantability;

        ToolLine(String key, ToolKind kind, float[][] stats, float damageBonus, int enchantability) {
            this.key = key;
            this.kind = kind;
            this.stats = stats;
            this.damageBonus = damageBonus;
            this.enchantability = enchantability;
        }

        @Override public String key() { return key; }
        @Override public ToolKind kind() { return kind; }
        @Override public int enchantability() { return enchantability; }
        @Override public float baseDamage(int toolIndex) { return stats[toolIndex][0]; }
        @Override public float speed(int toolIndex) { return stats[toolIndex][1]; }
        @Override public float damageBonus() { return damageBonus; }
    }

    private static final List<ToolLine> TOOL_LINES = new ArrayList<>();

    /** 第 n 重耐久：9ⁿ × 基础；六重起不可破坏。 */
    public static int durabilityFor(int level, ToolKind kind) {
        if (level >= UNBREAKABLE_FROM) {
            return Integer.MAX_VALUE;
        }
        long base = kind == ToolKind.STONE ? 131 : 59;
        return (int) (base * pow9(level));
    }

    /** 挖掘等级阶梯：7 重=铁(1)、8 重=钻石(2)、9 重=下界合金(3)。 */
    public static int tierLevelFor(int level) {
        if (level >= 9) {
            return 3;
        }
        if (level == 8) {
            return 2;
        }
        if (level == 7) {
            return 1;
        }
        return 0;
    }

    public static List<String> tools() {
        return java.util.Arrays.asList(TOOLS);
    }

    /** 工具类型列表（pickaxe/axe/shovel/hoe/sword），与资源生成器顺序一致。 */
    public static List<String> TOOL_TYPES() {
        return java.util.Arrays.asList(TOOLS);
    }

    /** 挖掘速度阶梯（1-9 重）。 */
    public static float[] SPEED() {
        return SPEED;
    }

    /** 等级 id 前缀（1x..9x）。 */
    public static String[] LEVEL_PREFIX_NAME() {
        return LEVEL_PREFIX;
    }

    public static List<StorageMatView> STORAGE_VIEW() {
        return new ArrayList<>(STORAGE);
    }

    public static List<ToolLineView> TOOL_LINES_VIEW() {
        return new ArrayList<>(TOOL_LINES);
    }

    static List<ToolLine> toolLines() {
        return TOOL_LINES;
    }

    /** 工具伤害加成（供入口构建 CompressedTier：总伤害 = 对应原版工具 × 1.6^重）。 */
    public static float toolBonus(ToolLineView line, int level, int toolIndex) {
        float base = line.baseDamage(toolIndex);
        return (1.0F + base + line.damageBonus()) * (float) Math.pow(1.6, level) - 1.0F - base;
    }

    public static float toolBaseDamage(ToolLineView line, int toolIndex) {
        return line.baseDamage(toolIndex);
    }

    public static float toolSpeed(ToolLineView line, int toolIndex) {
        return line.speed(toolIndex);
    }

    // ------------------------------------------------------------------ 创造栏

    private static ItemStack icon(String name) {
        for (ItemReg e : ITEMS) {
            if (e.name.equals(name)) {
                return new ItemStack(e.item);
            }
        }
        return ItemStack.EMPTY;
    }

    /** 压缩方块创造栏。 */
    public static final CreativeModeTab TAB_BLOCKS = new CreativeModeTab(MOD_ID + ".blocks") {
        @Override
        public ItemStack makeIcon() {
            return icon("1x_cobblestone");
        }
    };

    /** 压缩工具创造栏（工具+材料）。 */
    public static final CreativeModeTab TAB_TOOLS = new CreativeModeTab(MOD_ID + ".tools") {
        @Override
        public ItemStack makeIcon() {
            return icon("9x_wood_pickaxe");
        }
    };

    /** 压缩食物创造栏。 */
    public static final CreativeModeTab TAB_FOOD = new CreativeModeTab(MOD_ID + ".food") {
        @Override
        public ItemStack makeIcon() {
            return icon("3x_beef");
        }
    };

    // ------------------------------------------------------------------ 构建

    private static boolean built;

    /** 数据填充入口（由各数据表 init 方法在类加载时调用，见 CompressedBlocksForge）。 */
    public static void build(WoodDefView[] woods, CropDefView[] crops, FoodDefView[] foods,
                             String[] compatKeys) {
        if (built) {
            return;
        }
        built = true;
        initMaterials();
        for (WoodDefView w : woods) {
            WOODS.add(new WoodDef(w.key(), w.en(), w.zh()));
        }
        for (CropDefView c : crops) {
            CROPS.add(new CropDef(c.key(), c.en(), c.zh(), c.hasSeeds()));
        }
        for (FoodDefView f : foods) {
            FOODS.add(new FoodDef(f.key(), f.en(), f.zh(), f.nutrition(), f.saturation()));
        }
        COMPAT_METAL_KEYS.addAll(java.util.Arrays.asList(compatKeys));
        for (ToolLine line : new ToolLine[] {
            new ToolLine("cobblestone", ToolKind.STONE, STONE_TOOL_STATS, 1.0F, 5),
            new ToolLine("wood", ToolKind.WOOD, WOOD_TOOL_STATS, 0.0F, 15)}) {
            TOOL_LINES.add(line);
        }
        CompressedBlocksForge.constructAll();
    }

    /** 数据视图（避免 core 依赖具体数据源）。 */
    public interface WoodDefView {
        String key();

        String en();

        String zh();
    }

    public interface CropDefView {
        String key();

        String en();

        String zh();

        boolean hasSeeds();
    }

    public interface FoodDefView {
        String key();

        String en();

        String zh();

        float nutrition();

        float saturation();
    }

    // ------------------------------------------------------------------ 供入口调用的构建步骤

    static List<StorageMat> storage() {
        return STORAGE;
    }

    static List<WoodDef> woods() {
        return WOODS;
    }

    static List<CropDef> crops() {
        return CROPS;
    }

    static List<FoodDef> foods() {
        return FOODS;
    }

    static String[] levelPrefix() {
        return LEVEL_PREFIX;
    }

    static String[] levelEnPrefix() {
        return LEVEL_EN_PREFIX;
    }

    public static void addBlock(BlockReg reg) {
        BLOCKS.add(reg);
    }

    public static void addItem(ItemReg reg) {
        ITEMS.add(reg);
    }

    public static void addStorageBlock(BlockReg reg) {
        STORAGE_BLOCKS.add(reg);
    }

    public static void addToolReg(ToolReg reg) {
        TOOL_REGS.add(reg);
    }

    static void addArmorReg(ArmorReg reg) {
        ARMOR_REGS.add(reg);
    }

    public static void putArmorIndex(Item item, int piece, int level) {
        ARMOR_INDEX.put(item, new int[]{piece, level});
    }

    public static void putDirtLevel(Block block, int level) {
        DIRT_LEVELS.put(block, level);
    }

    public static void putSandLevel(Block block, int level) {
        SAND_LEVELS.put(block, level);
    }

    public static void putNetherSoilLevel(Block block, int level) {
        NETHER_SOIL_LEVELS.put(block, level);
    }

    public static void putFarmland(int level, Block block) {
        FARMLAND.put(level, block);
    }

    public static void addSaplingBlock(Block block) {
        SAPLING_BLOCKS.add(block);
    }

    public static void addLeavesBlock(Block block) {
        LEAVES_BLOCKS.add(block);
    }

    // ------------------------------------------------------------------ 穿戴查询

    /** 当前穿戴件的最高重数（无穿戴 = 0）。 */
    public static int wornMaxLevel(net.minecraft.world.entity.player.Player player) {
        int max = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = ARMOR_INDEX.get(player.getItemBySlot(slot).getItem());
            if (info != null) {
                max = Math.max(max, info[1]);
            }
        }
        return max;
    }

    /** 四件全穿且每件重数 ≥ minLevel。 */
    public static boolean fullSetAtLeast(net.minecraft.world.entity.player.Player player, int minLevel) {
        for (net.minecraft.world.entity.EquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = ARMOR_INDEX.get(player.getItemBySlot(slot).getItem());
            if (info == null || info[1] < minLevel) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasFullLevel9Armor(net.minecraft.world.entity.player.Player player) {
        return fullSetAtLeast(player, LEVELS);
    }

    /** 穿戴重数对应的抗性提升等级：7 重=0、8 重=1、9 重=2；7 重以下 = -1（无效果）。 */
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

    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
        net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
        net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET
    };

    private CompressedBlocks() {
    }
}
