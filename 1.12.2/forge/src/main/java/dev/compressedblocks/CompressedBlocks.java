package dev.compressedblocks;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 压缩方块核心（1.12.2 Forge 移植）：纯数据驱动。
 * 方块/物品实例在此集中构建，注册动作由 forge 入口的 RegistryEvent.Register 循环完成。
 *
 * 与 1.16.5 版的差异：
 * - 材料集 36 种（再剔除玄武岩/黑石/蓝冰：1.16 内容 ×2 + 1.13 内容）；
 * - 方块属性走 1.12.2 构造器 setter 链（无 Block.Properties）；
 * - MapColor/SoundType/Material 均为 1.12.2 枚举/常量；
 * - 工具材质 1.12.2 无 Tier 接口，改用 EnumHelper.addToolMaterial 动态枚举（见入口）；
 * - 护甲材质同为 EnumHelper.addArmorMaterial（含盔甲层贴图路径）；
 * - 不可破坏 = 耐久 Integer.MAX_VALUE（1.12.2 无组件系统）；
 * - 树苗生长 = 代码 WorldGenerator（1.12.2 无 configured feature）。
 */
public final class CompressedBlocks {
    public static final String MOD_ID = "compressedblocks";
    public static final String VERSION = "0.1.0";

    public static final int LEVELS = 9;
    /** 六重及以上工具/护甲：不可破坏（耐久上限极大值）。 */
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

    /** 压缩爆炸抗性（1.12.2 setResistance 会 ×3，入口传 400 得 1200）。 */
    public static final float STORAGE_BLAST = 1200.0F;

    /** 压缩硬度：L1 = 原版该方块硬度，线性升到 L9 目标（黑曜石类 200、硬类 150、软类 100）。 */
    public static float hardnessFor(StorageMatView m, int level) {
        float vh = m.vh();
        float target = m.key().equals("obsidian") ? 200.0F : (vh >= 1.5F ? 150.0F : 100.0F);
        return vh + (target - vh) * (level - 1) / 8.0F;
    }

    public enum ToolKind { STONE, WOOD }

    /** 创造物品栏落位（1.12.2 仅三个页签：方块/工具/食物）。 */
    public enum Tab { BLOCKS, TOOLS, FOOD }

    /**
     * 储存方块材料视图（入口循环注册用；key/en/声音/颜色/材质/工具/挖掘档/发光/原版硬度）。
     * 36 材料：1.16.5 的 39 种剔除 玄武岩/黑石/蓝冰（1.12.2 无对应原版方块）。
     */
    public interface StorageMatView {
        String key();

        String enName();

        Material material();

        MapColor color();

        SoundType sound();

        boolean requiresTool();

        /** "pickaxe"/"shovel"/"axe"/"hoe"，null = 无工具要求。 */
        String tool();

        int baseHarvest();

        /** 发光等级 0-15。 */
        int light();

        float vh();
    }

    private static final class StorageMat implements StorageMatView {
        final String key;
        final String en;
        final SoundType sound;
        final MapColor color;
        final Material material;
        final boolean requiresTool;
        final String tool;
        final int baseHarvest;
        final int light;
        final float vh;

        StorageMat(String key, String en, SoundType sound, MapColor color, Material material,
                   boolean requiresTool, String tool, int baseHarvest, int light, float vh) {
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
        @Override public MapColor color() { return color; }
        @Override public SoundType sound() { return sound; }
        @Override public boolean requiresTool() { return requiresTool; }
        @Override public String tool() { return tool; }
        @Override public int baseHarvest() { return baseHarvest; }
        @Override public int light() { return light; }
        @Override public float vh() { return vh; }
    }

    private static final List<StorageMat> STORAGE = new ArrayList<>();

    private static StorageMat mat(String key, String en, SoundType sound, MapColor color,
                                  Material material, boolean requiresTool, String tool,
                                  int baseHarvest, float vh) {
        return mat(key, en, sound, color, material, requiresTool, tool, baseHarvest, 0, vh);
    }

    private static StorageMat mat(String key, String en, SoundType sound, MapColor color,
                                  Material material, boolean requiresTool, String tool,
                                  int baseHarvest, int light, float vh) {
        StorageMat m = new StorageMat(key, en, sound, color, material, requiresTool, tool,
            baseHarvest, light, vh);
        STORAGE.add(m);
        return m;
    }

    // 石/矿石族：镐；1-2 重石镐（0）、3-4 重铁镐（1）、5+ 重钻镐（2），黑曜石 3（下界合金级）
    private static final String PICK = "pickaxe";
    private static final String SHOVEL = "shovel";
    private static final String AXE = "axe";
    private static final String HOE = "hoe";
    private static final String NONE = null;

    private static void initMaterials() {
        // 注意：1.12.2 MapColor/Material 常量名与 1.16 略有差异（ADOBE=陶瓦橙、NETHER=下界）
        mat("cobblestone", "Cobblestone", SoundType.STONE, MapColor.STONE, Material.ROCK, true, PICK, 0, 2.0F);
        mat("stone", "Stone", SoundType.STONE, MapColor.STONE, Material.ROCK, true, PICK, 0, 1.5F);
        mat("oak_log", "Oak Log", SoundType.WOOD, MapColor.WOOD, Material.WOOD, false, AXE, 0, 2.0F);
        mat("spruce_log", "Spruce Log", SoundType.WOOD, MapColor.WOOD, Material.WOOD, false, AXE, 0, 2.0F);
        mat("birch_log", "Birch Log", SoundType.WOOD, MapColor.SAND, Material.WOOD, false, AXE, 0, 2.0F);
        mat("jungle_log", "Jungle Log", SoundType.WOOD, MapColor.WOOD, Material.WOOD, false, AXE, 0, 2.0F);
        mat("acacia_log", "Acacia Log", SoundType.WOOD, MapColor.ADOBE, Material.WOOD, false, AXE, 0, 2.0F);
        mat("dark_oak_log", "Dark Oak Log", SoundType.WOOD, MapColor.BROWN, Material.WOOD, false, AXE, 0, 2.0F);
        mat("dirt", "Dirt", SoundType.GROUND, MapColor.DIRT, Material.GROUND, false, SHOVEL, 0, 0.5F);
        mat("sand", "Sand", SoundType.SAND, MapColor.SAND, Material.SAND, false, SHOVEL, 0, 0.5F);
        mat("gravel", "Gravel", SoundType.GROUND, MapColor.STONE, Material.GROUND, false, SHOVEL, 0, 0.6F);
        mat("netherrack", "Netherrack", SoundType.STONE, MapColor.NETHERRACK, Material.ROCK, true, PICK, 0, 0.4F);
        mat("end_stone", "End Stone", SoundType.STONE, MapColor.SAND, Material.ROCK, true, PICK, 0, 3.0F);
        mat("obsidian", "Obsidian", SoundType.STONE, MapColor.OBSIDIAN, Material.ROCK, true, PICK, 3, 50.0F);
        mat("coal_block", "Block of Coal", SoundType.STONE, MapColor.BLACK, Material.ROCK, true, PICK, 0, 5.0F);
        mat("iron_block", "Block of Iron", SoundType.METAL, MapColor.IRON, Material.IRON, true, PICK, 1, 5.0F);
        mat("lapis_block", "Lapis Lazuli Block", SoundType.STONE, MapColor.LAPIS, Material.ROCK, true, PICK, 1, 3.0F);
        mat("gold_block", "Block of Gold", SoundType.METAL, MapColor.GOLD, Material.IRON, true, PICK, 2, 3.0F);
        mat("redstone_block", "Block of Redstone", SoundType.STONE, MapColor.RED, Material.ROCK, true, PICK, 1, 5.0F);
        mat("emerald_block", "Block of Emerald", SoundType.METAL, MapColor.EMERALD, Material.IRON, true, PICK, 2, 5.0F);
        mat("diamond_block", "Block of Diamond", SoundType.METAL, MapColor.DIAMOND, Material.IRON, true, PICK, 2, 5.0F);
        mat("granite", "Granite", SoundType.STONE, MapColor.ADOBE, Material.ROCK, true, PICK, 0, 1.5F);
        mat("diorite", "Diorite", SoundType.STONE, MapColor.QUARTZ, Material.ROCK, true, PICK, 0, 1.5F);
        mat("andesite", "Andesite", SoundType.STONE, MapColor.STONE, Material.ROCK, true, PICK, 0, 1.5F);
        mat("sandstone", "Sandstone", SoundType.STONE, MapColor.SAND, Material.ROCK, true, PICK, 0, 0.8F);
        mat("red_sandstone", "Red Sandstone", SoundType.STONE, MapColor.ADOBE, Material.ROCK, true, PICK, 0, 0.8F);
        mat("terracotta", "Terracotta", SoundType.STONE, MapColor.ADOBE, Material.ROCK, true, PICK, 0, 1.25F);
        mat("quartz_block", "Quartz Block", SoundType.STONE, MapColor.QUARTZ, Material.ROCK, true, PICK, 0, 0.8F);
        mat("purpur_block", "Purpur Block", SoundType.STONE, MapColor.MAGENTA, Material.ROCK, true, PICK, 0, 1.5F);
        mat("prismarine", "Prismarine", SoundType.STONE, MapColor.CYAN, Material.ROCK, true, PICK, 0, 1.5F);
        mat("glowstone", "Glowstone", SoundType.GLASS, MapColor.SAND, Material.GLASS, false, NONE, 15, 0.3F);
        mat("clay", "Clay", SoundType.GROUND, MapColor.CLAY, Material.CLAY, false, SHOVEL, 0, 0.6F);
        mat("hay_block", "Hay Bale", SoundType.PLANT, MapColor.YELLOW, Material.PLANTS, false, HOE, 0, 0.5F);
        mat("bone_block", "Bone Block", SoundType.STONE, MapColor.SAND, Material.ROCK, true, PICK, 0, 2.0F);
        mat("snow", "Snow Block", SoundType.SNOW, MapColor.SNOW, Material.SNOW, false, SHOVEL, 0, 0.2F);
        // 原版无方块形态：火药块（自定义方块）
        mat("gunpowder", "Block of Gunpowder", SoundType.SAND, MapColor.GRAY, Material.SAND, false, SHOVEL, 0, 0.5F);
    }

    /** 甘蔗 id 的材料段：去掉 "_block" 后缀（diamond_block → diamond），与生成器 cane_key 一致。 */
    public static String caneKey(String matKey) {
        return matKey.endsWith("_block") ? matKey.substring(0, matKey.length() - 6) : matKey;
    }

    /** 甘蔗英文显示名：材料名去掉 "Block of " 前缀 / " Block" / " Bale" 后缀。 */
    public static String caneEnName(String matEn) {
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

    // ------------------------------------------------------------------ 树 / 作物 / 食物 数据视图

    /** 树定义视图（入口数据表传入）。 */
    public interface WoodDefView {
        String key();

        String en();

        String zh();
    }

    /** 作物定义视图。 */
    public interface CropDefView {
        String key();

        String en();

        String zh();

        boolean hasSeeds();
    }

    /** 食物定义视图（原版数值；N 级压缩 = ×9^N）。 */
    public interface FoodDefView {
        String key();

        String en();

        String zh();

        float nutrition();

        float saturation();
    }

    private static final List<WoodDefView> WOODS = new ArrayList<>();
    private static final List<CropDefView> CROPS = new ArrayList<>();
    private static final List<FoodDefView> FOODS = new ArrayList<>();

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

    /** 耐久乘数 = 9^重；六重起极大值（不可破坏）。 */
    public static long armorDurabilityMult(int level) {
        return level >= UNBREAKABLE_FROM ? Integer.MAX_VALUE / 16L : pow9(level);
    }

    public static long pow9(int level) {
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

    /** @param itemName null = 无物品（压缩耕地）。 */
    public static final class BlockReg {
        public final String name;
        public final net.minecraft.block.Block block;
        public final String itemName;

        public BlockReg(String name, net.minecraft.block.Block block, String itemName) {
            this.name = name;
            this.block = block;
            this.itemName = itemName;
        }
    }

    public static final class ItemReg {
        public final String name;
        public final Item item;
        public final Tab tab;

        public ItemReg(String name, Item item, Tab tab) {
            this.name = name;
            this.item = item;
            this.tab = tab;
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
        public final String name;
        public final Item item;
        public final int piece;
        public final int level;

        public ArmorReg(String name, Item item, int piece, int level) {
            this.name = name;
            this.item = item;
            this.piece = piece;
            this.level = level;
        }
    }

    private static final List<BlockReg> BLOCKS = new ArrayList<>();
    private static final List<ItemReg> ITEMS = new ArrayList<>();
    private static final List<ToolReg> TOOL_REGS = new ArrayList<>();
    private static final List<ArmorReg> ARMOR_REGS = new ArrayList<>();
    private static final Map<Item, int[]> ARMOR_INDEX = new IdentityHashMap<>();
    private static final Map<net.minecraft.block.Block, Integer> DIRT_LEVELS = new IdentityHashMap<>();
    private static final Map<net.minecraft.block.Block, Integer> SAND_LEVELS = new IdentityHashMap<>();
    private static final Map<net.minecraft.block.Block, Integer> NETHER_SOIL_LEVELS = new IdentityHashMap<>();
    private static final Map<Integer, net.minecraft.block.Block> FARMLAND = new HashMap<>();

    /** 压缩箱子/压缩潜影盒引用（入口赋值）。 */
    public static CompressedChestBlock COMPRESSED_CHEST;
    public static CompressedShulkerBlock COMPRESSED_SHULKER;

    /** 压缩金属包：22 材质 × 9 级（与资源生成器 COMPAT_METALS 一致）。 */
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
    public static Integer dirtLevel(net.minecraft.block.state.IBlockState state) {
        return DIRT_LEVELS.get(state.getBlock());
    }

    /** 沙子等级查询。 */
    public static Integer sandLevel(net.minecraft.block.state.IBlockState state) {
        return SAND_LEVELS.get(state.getBlock());
    }

    /** 下界土壤等级查询：压缩下界岩 → 1-9。 */
    public static Integer netherSoilLevel(net.minecraft.block.state.IBlockState state) {
        return NETHER_SOIL_LEVELS.get(state.getBlock());
    }

    /** 耕地等级查询：压缩耕地 → 1-9，其他 null。 */
    public static Integer farmlandLevel(net.minecraft.block.state.IBlockState state) {
        if (state.getBlock() instanceof CompressedFarmBlock) {
            return ((CompressedFarmBlock) state.getBlock()).level();
        }
        return null;
    }

    public static net.minecraft.block.Block farmland(int level) {
        return FARMLAND.get(level);
    }

    /** 按注册名查物品；无则 null。 */
    public static Item itemByName(String name) {
        for (ItemReg e : ITEMS) {
            if (e.name.equals(name)) {
                return e.item;
            }
        }
        return null;
    }

    /** 按注册名查方块；无则 null。 */
    public static net.minecraft.block.Block blockByName(String name) {
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

    // ------------------------------------------------------------------ 工具

    /** 工具只有两条线：圆石线、木线（9 原木混用）。顺序 pickaxe/axe/shovel/hoe/sword。 */
    public static final String[] TOOL_TYPES = {"pickaxe", "axe", "shovel", "hoe", "sword"};

    /** 原版石级工具基础伤害 {pickaxe, axe, shovel, hoe, sword}。 */
    private static final float[] STONE_BASE_DAMAGE = {1.0F, 7.0F, 1.5F, -1.0F, 3.0F};

    /** 原版木级工具基础伤害。 */
    private static final float[] WOOD_BASE_DAMAGE = {1.0F, 6.0F, 1.5F, 0.0F, 3.0F};

    /**
     * 第 n 重工具统一伤害基数 d：总伤害公式（1.16.5 同源）
     * 石线 pick = 3×1.6^n − 1、木线 pick = 2×1.6^n − 1；
     * 1.12.2 ItemPickaxe/ItemSword/ItemSpade = base + material.damage，因此
     * damage = pick 总伤 − 1 即可让剑/锹自动落位（差值恰为原版类常数差）；
     * 斧头用 ItemAxe(material, damage, speed) 构造器直接传总伤。
     */
    public static float toolMaterialDamage(ToolKind kind, int level) {
        float mult = (float) Math.pow(1.6, level);
        float pickTotal = (kind == ToolKind.STONE ? 3.0F : 2.0F) * mult - 1.0F;
        return pickTotal - 1.0F;
    }

    /** 斧头总伤害（1.16.5 同源）：石 9×1.6^n−1、木 7×1.6^n−1。 */
    public static float toolAxeDamage(ToolKind kind, int level) {
        float mult = (float) Math.pow(1.6, level);
        return (kind == ToolKind.STONE ? 9.0F : 7.0F) * mult - 1.0F;
    }

    /** 工具基础伤害（名称/显示与 ToolReg.damage 用）。 */
    public static float toolBaseDamage(ToolKind kind, int toolIndex) {
        return kind == ToolKind.STONE ? STONE_BASE_DAMAGE[toolIndex] : WOOD_BASE_DAMAGE[toolIndex];
    }

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

    public static float[] speedLadder() {
        return SPEED;
    }

    public static String[] levelPrefix() {
        return LEVEL_PREFIX;
    }

    public static String[] levelEnPrefix() {
        return LEVEL_EN_PREFIX;
    }

    public static List<StorageMatView> storage() {
        return new ArrayList<StorageMatView>(STORAGE);
    }

    public static List<WoodDefView> woods() {
        return WOODS;
    }

    public static List<CropDefView> crops() {
        return CROPS;
    }

    public static List<FoodDefView> foods() {
        return FOODS;
    }

    // ------------------------------------------------------------------ 穿戴查询

    /** 当前穿戴件的最高重数（无穿戴 = 0）。 */
    public static int wornMaxLevel(net.minecraft.entity.player.EntityPlayer player) {
        int max = 0;
        for (net.minecraft.inventory.EntityEquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = ARMOR_INDEX.get(player.getItemStackFromSlot(slot).getItem());
            if (info != null) {
                max = Math.max(max, info[1]);
            }
        }
        return max;
    }

    /** 四件全穿且每件重数 ≥ minLevel。 */
    public static boolean fullSetAtLeast(net.minecraft.entity.player.EntityPlayer player, int minLevel) {
        for (net.minecraft.inventory.EntityEquipmentSlot slot : ARMOR_SLOTS) {
            int[] info = ARMOR_INDEX.get(player.getItemStackFromSlot(slot).getItem());
            if (info == null || info[1] < minLevel) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasFullLevel9Armor(net.minecraft.entity.player.EntityPlayer player) {
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
        return stack.isEmpty() ? null : ARMOR_INDEX.get(stack.getItem());
    }

    private static final net.minecraft.inventory.EntityEquipmentSlot[] ARMOR_SLOTS = {
        net.minecraft.inventory.EntityEquipmentSlot.HEAD, net.minecraft.inventory.EntityEquipmentSlot.CHEST,
        net.minecraft.inventory.EntityEquipmentSlot.LEGS, net.minecraft.inventory.EntityEquipmentSlot.FEET
    };

    // ------------------------------------------------------------------ 创造栏

    /** 图标物品查询（在物品注册后由入口回填）。 */
    private static final Map<String, Item> ICONS = new HashMap<>();

    public static void putIcon(String name, Item item) {
        ICONS.put(name, item);
    }

    private static Item icon(String name) {
        Item item = ICONS.get(name);
        return item != null ? item : net.minecraft.init.Items.STICK;
    }

    /** 压缩方块创造栏。 */
    public static final CreativeTabs TAB_BLOCKS = new CreativeTabs(MOD_ID + ".blocks") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(icon("1x_cobblestone"));
        }
    };

    /** 压缩工具创造栏（工具+材料+盆栽）。 */
    public static final CreativeTabs TAB_TOOLS = new CreativeTabs(MOD_ID + ".tools") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(icon("9x_wood_pickaxe"));
        }
    };

    /** 压缩食物创造栏。 */
    public static final CreativeTabs TAB_FOOD = new CreativeTabs(MOD_ID + ".food") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(icon("3x_beef"));
        }
    };

    // ------------------------------------------------------------------ 注册登记

    public static void addBlock(BlockReg reg) {
        BLOCKS.add(reg);
    }

    public static void addItem(ItemReg reg) {
        ITEMS.add(reg);
    }

    public static void addToolReg(ToolReg reg) {
        TOOL_REGS.add(reg);
    }

    public static void addArmorReg(ArmorReg reg) {
        ARMOR_REGS.add(reg);
    }

    public static void putArmorIndex(Item item, int piece, int level) {
        ARMOR_INDEX.put(item, new int[]{piece, level});
    }

    public static void putDirtLevel(net.minecraft.block.Block block, int level) {
        DIRT_LEVELS.put(block, level);
    }

    public static void putSandLevel(net.minecraft.block.Block block, int level) {
        SAND_LEVELS.put(block, level);
    }

    public static void putNetherSoilLevel(net.minecraft.block.Block block, int level) {
        NETHER_SOIL_LEVELS.put(block, level);
    }

    public static void putFarmland(int level, net.minecraft.block.Block block) {
        FARMLAND.put(level, block);
    }

    // ------------------------------------------------------------------ 构建

    private static boolean built;

    /**
     * 数据填充入口（由入口类在 @Mod 构造器调用——1.12.2 的 RegistryEvent.Register 在
     * preInit 之前发射，所有实例必须在注册事件前构建完毕）。
     */
    public static void build(WoodDefView[] woods, CropDefView[] crops, FoodDefView[] foods,
                             String[] compatKeys) {
        if (built) {
            return;
        }
        built = true;
        initMaterials();
        for (WoodDefView w : woods) {
            WOODS.add(w);
        }
        for (CropDefView c : crops) {
            CROPS.add(c);
        }
        for (FoodDefView f : foods) {
            FOODS.add(f);
        }
        for (String k : compatKeys) {
            COMPAT_METAL_KEYS.add(k);
        }
    }

    /** 四舍五入到 0.1 显示精度（自检/日志用）。 */
    public static float round1(float v) {
        return Math.round(v * 10.0F) / 10.0F;
    }

    private CompressedBlocks() {
    }
}
