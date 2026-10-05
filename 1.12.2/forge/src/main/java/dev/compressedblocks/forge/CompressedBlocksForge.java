package dev.compressedblocks.forge;

import dev.compressedblocks.CobblestoneGeneratorBlock;
import dev.compressedblocks.CobblestoneGeneratorBlockEntity;
import dev.compressedblocks.CompressedBlocks;
import dev.compressedblocks.CompressedCaneBlock;
import dev.compressedblocks.CompressedCaneItem;
import dev.compressedblocks.CompressedChestBlock;
import dev.compressedblocks.CompressedCropBlock;
import dev.compressedblocks.CompressedFarmBlock;
import dev.compressedblocks.CompressedFoodItem;
import dev.compressedblocks.CompressedHoeItem;
import dev.compressedblocks.CompressedHooks;
import dev.compressedblocks.CompressedLeavesBlock;
import dev.compressedblocks.CompressedPotBlock;
import dev.compressedblocks.CompressedPotBlockEntity;
import dev.compressedblocks.CompressedSaplingBlock;
import dev.compressedblocks.CompressedShulkerBlock;
import dev.compressedblocks.CompressedSoilItem;
import dev.compressedblocks.ScrollingContainerBlockEntity;
import dev.compressedblocks.ScrollingContainerMenu;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import java.util.HashSet;
import java.util.Set;

/**
 * 压缩方块 1.12.2 Forge 入口。
 * 1.12.2 与 1.16.5 的关键差异：
 * - 注册走 RegistryEvent.Register&lt;Block&gt;/&lt;Item&gt;（构造期注册表不可用）；
 *   所有实例在 @Mod 构造器内构建完毕（Register 事件在 preInit 之前发射）；
 * - GUI 打开走 player.openGui（无 NetworkHooks/MenuType），IGuiHandler 按 int id 分发；
 * - 方块实体在 preInit 用 GameRegistry.registerTileEntity 注册；
 * - 事件桥直接挂在 MinecraftForge.EVENT_BUS（1.12.2 无 mod 总线拆分）；
 * - 物品模型必须在客户端 ModelRegistryEvent 手动逐个注册（1.13+ 才有 blockstates 自动物品模型）。
 */
@Mod(modid = CompressedBlocks.MOD_ID, name = "Compressed Blocks", version = CompressedBlocks.VERSION,
    acceptedMinecraftVersions = "[1.12.2]")
public final class CompressedBlocksForge {
    /** GUI id：0 = 压缩箱子、1 = 压缩潜影盒（IGuiHandler 用 int 分发）。 */
    public static final int GUI_CHEST = 0;
    public static final int GUI_SHULKER = 1;

    /** 22 兼容金属（与 gen_1122_resources.COMPAT_METALS 一致）。 */
    private static final String[] COMPAT_METALS = {
        "tin", "lead", "zinc", "plastic", "silver", "nickel", "bronze", "brass", "electrum",
        "invar", "constantan", "steel", "manasteel", "uranium", "osmium", "signalum",
        "enderium", "refined_obsidian", "refined_glowstone", "lumium", "terrasteel", "elementium"
    };

    /** 工具/盔甲实例的耐久等参数（自检/文档用）。 */
    private static final Set<Item> LEVEL9_TOOLS = new HashSet<>();

    public CompressedBlocksForge() {
        // @Mod 实例引用（压缩箱子/潜影盒/刷石机的 openGui 需要）
        ModInstanceHolder.INSTANCE = this;
        // 1. core 数据构建（全部方块/物品实例，必须在 Register 事件前）
        CompressedBlocks.build(WOODS, CROPS, FOODS, COMPAT_METALS);
        constructAll();
        // 2. 事件桥（1.12.2 单总线）
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(CompressedHooks.BRIDGE);
    }

    // ------------------------------------------------------------------ 注册事件

    /** 方块注册（Register&lt;Item&gt; 在其后发射，物品可安全引用方块）。 */
    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        for (CompressedBlocks.BlockReg e : CompressedBlocks.blocks()) {
            event.getRegistry().register(e.block);
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // 先回填创造栏图标
        for (CompressedBlocks.BlockReg e : CompressedBlocks.blocks()) {
            if (e.itemName != null) {
                Item item = CompressedBlocks.itemByName(e.itemName);
                if (item != null) {
                    CompressedBlocks.putIcon(e.name, item);
                }
            }
        }
        Set<String> done = new HashSet<>();
        for (CompressedBlocks.BlockReg e : CompressedBlocks.blocks()) {
            if (e.itemName != null) {
                Item item = CompressedBlocks.itemByName(e.itemName);
                if (item != null && done.add(e.itemName)) {
                    event.getRegistry().register(item);
                }
            }
        }
        for (CompressedBlocks.ItemReg e : CompressedBlocks.items()) {
            if (done.add(e.name)) {
                event.getRegistry().register(e.item);
            }
        }
        // OreDictionary 兼容金属块（1.12.2 惯例 block<Name>）：1x 各注册一条供其它 mod 交互
        for (String key : COMPAT_METALS) {
            Block block = CompressedBlocks.blockByName("1x_" + key + "_block");
            if (block != null) {
                OreDictionary.registerOre("block" + capital(key), new ItemStack(block));
            }
        }
    }

    private static String capital(String key) {
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.toString();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 方块实体注册（1.12.2 用 GameRegistry + ResourceLocation）
        GameRegistryHolder.register();
        // GUI 处理器（压缩箱子/潜影盒的 openGui 通道）
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // 1.12.2 无世界生成 JSON：压缩树苗走代码 WorldGenerator，无需注册
    }

    /** 静态持有 TE 注册（避免 preInit 静态上下文问题）。 */
    private static final class GameRegistryHolder {
        static void register() {
            net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                ScrollingContainerBlockEntity.class, new ResourceLocation(CompressedBlocks.MOD_ID, "scrolling_container"));
            net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                CobblestoneGeneratorBlockEntity.class, new ResourceLocation(CompressedBlocks.MOD_ID, "cobblestone_generator"));
            net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                CompressedPotBlockEntity.class, new ResourceLocation(CompressedBlocks.MOD_ID, "pot"));
        }
    }

    /** @Mod 实例中转（避免方块类依赖入口实例字段）。 */
    public static final class ModInstanceHolder {
        public static Object INSTANCE;
    }

    // ------------------------------------------------------------------ 实例构建（1.16.5 同构，1.12.2 API）

    private static final String[] LEVEL_EN = {
        "Compressed", "Double Compressed", "Triple Compressed", "Quadruple Compressed", "Quintuple Compressed",
        "Sextuple Compressed", "Septuple Compressed", "Octuple Compressed", "Nonuple Compressed"
    };

    private static final class WoodDef implements CompressedBlocks.WoodDefView {
        final String key;
        final String en;
        final String zh;

        WoodDef(String key, String en, String zh) {
            this.key = key;
            this.en = en;
            this.zh = zh;
        }

        @Override public String key() { return key; }
        @Override public String en() { return en; }
        @Override public String zh() { return zh; }
    }

    private static final WoodDef[] WOODS = {
        new WoodDef("oak", "Oak", "橡树"),
        new WoodDef("spruce", "Spruce", "云杉"),
        new WoodDef("birch", "Birch", "白桦"),
        new WoodDef("jungle", "Jungle", "丛林"),
        new WoodDef("acacia", "Acacia", "金合欢"),
        new WoodDef("dark_oak", "Dark Oak", "深色橡树"),
    };

    private static final class CropDef implements CompressedBlocks.CropDefView {
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

        @Override public String key() { return key; }
        @Override public String en() { return en; }
        @Override public String zh() { return zh; }
        @Override public boolean hasSeeds() { return hasSeeds; }
    }

    private static final CropDef[] CROPS = {
        new CropDef("wheat", "Wheat", "小麦", true),
        new CropDef("carrot", "Carrot", "胡萝卜", false),
        new CropDef("potato", "Potato", "马铃薯", false),
        new CropDef("beetroot", "Beetroot", "甜菜根", true),
    };

    private static final class FoodDef implements CompressedBlocks.FoodDefView {
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

        @Override public String key() { return key; }
        @Override public String en() { return en; }
        @Override public String zh() { return zh; }
        @Override public float nutrition() { return nutrition; }
        @Override public float saturation() { return saturation; }
    }

    /** 原版数值；N 级压缩 = ×9^N。 */
    private static final FoodDef[] FOODS = {
        new FoodDef("bread", "Bread", "面包", 5.0F, 6.0F),
        new FoodDef("beef", "Raw Beef", "生牛肉", 3.0F, 1.8F),
        new FoodDef("cooked_beef", "Steak", "牛排", 8.0F, 12.8F),
        new FoodDef("porkchop", "Raw Porkchop", "生猪排", 3.0F, 1.8F),
        new FoodDef("cooked_porkchop", "Cooked Porkchop", "熟猪排", 8.0F, 12.8F),
        new FoodDef("mutton", "Raw Mutton", "羊肉", 2.0F, 1.2F),
        new FoodDef("cooked_mutton", "Cooked Mutton", "熟羊肉", 6.0F, 9.6F),
        new FoodDef("chicken", "Raw Chicken", "生鸡肉", 2.0F, 1.2F),
        new FoodDef("cooked_chicken", "Cooked Chicken", "熟鸡肉", 6.0F, 7.2F),
        new FoodDef("rabbit", "Raw Rabbit", "生兔肉", 3.0F, 1.8F),
        new FoodDef("cooked_rabbit", "Cooked Rabbit", "熟兔肉", 5.0F, 6.0F),
        new FoodDef("cod", "Raw Cod", "生鳕鱼", 2.0F, 0.4F),
        new FoodDef("cooked_cod", "Cooked Cod", "熟鳕鱼", 5.0F, 6.0F),
        new FoodDef("salmon", "Raw Salmon", "生鲑鱼", 2.0F, 0.4F),
        new FoodDef("cooked_salmon", "Cooked Salmon", "熟鲑鱼", 6.0F, 9.6F),
        new FoodDef("melon", "Watermelon", "西瓜", 2.0F, 1.2F),
        new FoodDef("rotten_flesh", "Rotten Flesh", "腐肉", 4.0F, 0.8F),
        new FoodDef("baked_potato", "Baked Potato", "烤土豆", 5.0F, 6.0F),
    };

    // ---- 方块/物品属性捷径（1.12.2 构造器 setter 链）

    private static Block simpleBlock(String name, Material material, MapColor color, SoundType sound,
                                     float hardness, int light, String tool, int harvest, boolean requiresTool) {
        Block block = new Block(material, color);
        block.setHardness(hardness);
        // 1.12.2 setResistance 会把值 ×3 存为爆炸抗性：目标 1200 → 传 400
        block.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
        BlockAccessors.sound(block, sound);
        if (light > 0) {
            block.setLightLevel(light / 15.0F);
        }
        if (requiresTool && tool != null) {
            block.setHarvestLevel(tool, harvest);
        }
        block.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
        block.setRegistryName(CompressedBlocks.MOD_ID, name);
        return block;
    }

    /** 储存方块挖掘档位阶梯：基础档 + 3/4 重+1、5 重+2（黑曜石恒 3）。 */
    private static int storageHarvest(int baseHarvest, int level) {
        return baseHarvest >= 3 ? 3
            : Math.min(2, baseHarvest + (level >= 5 ? 2 : level >= 3 ? 1 : 0));
    }

    private static void constructAll() {
        String[] P = CompressedBlocks.levelPrefix();
        int LEVELS = CompressedBlocks.LEVELS;
        int CROP_MAX = CompressedBlocks.CROP_MAX_LEVEL;
        int FOOD_MAX = CompressedBlocks.FOOD_MAX_LEVEL;
        int UNBREAKABLE_FROM = CompressedBlocks.UNBREAKABLE_FROM;

        // 1. 储存方块（36 材料 × 9 重）
        for (CompressedBlocks.StorageMatView m : CompressedBlocks.storage()) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = P[level - 1] + "_" + m.key();
                Block block = simpleBlock(name, m.material(), m.color(), m.sound(),
                    CompressedBlocks.hardnessFor(m, level), m.light(), m.tool(),
                    storageHarvest(m.baseHarvest(), level), m.requiresTool());
                block.setCreativeTab(CompressedBlocks.TAB_BLOCKS);
                ItemBlock item = new ItemBlock(block);
                item.setRegistryName(block.getRegistryName());
                item.setTranslationKey(block.getTranslationKey());
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.BLOCKS));
                if (m.key().equals("dirt")) {
                    CompressedBlocks.putDirtLevel(block, level);
                }
                if (m.key().equals("sand")) {
                    CompressedBlocks.putSandLevel(block, level);
                }
                if (m.key().equals("netherrack")) {
                    CompressedBlocks.putNetherSoilLevel(block, level);
                }
            }
        }
        // 2. 树叶（6 木 × 9 重）：原版凋落算法 + 客户端透明层
        for (WoodDef w : WOODS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = P[level - 1] + "_" + w.key + "_leaves";
                Block block = new CompressedLeavesBlock();
                block.setHardness(0.2F);
                block.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
                BlockAccessors.sound(block, SoundType.PLANT);
                block.setLightOpacity(1);
                block.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
                block.setRegistryName(CompressedBlocks.MOD_ID, name);
                block.setCreativeTab(CompressedBlocks.TAB_BLOCKS);
                ItemBlock item = new ItemBlock(block);
                item.setRegistryName(block.getRegistryName());
                item.setTranslationKey(block.getTranslationKey());
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.BLOCKS));
            }
        }
        // 3. 树苗（6 木 × 9 重）：只能种在等级足够的压缩泥土上，随机刻长代码树
        for (WoodDef w : WOODS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = P[level - 1] + "_" + w.key + "_sapling";
                Block block = new CompressedSaplingBlock(level, w.key);
                block.setHardness(0.0F);
                block.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
                BlockAccessors.sound(block, SoundType.PLANT);
                block.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
                block.setRegistryName(CompressedBlocks.MOD_ID, name);
                String enName = LEVEL_EN[level - 1] + " Compressed " + w.en + " Sapling";
                Item item = new CompressedSoilItem(block, level, false, enName);
                item.setCreativeTab(CompressedBlocks.TAB_TOOLS);
                item.setRegistryName(block.getRegistryName());
                item.setTranslationKey(block.getTranslationKey());
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.TOOLS));
            }
        }
        // 4. 压缩耕地（9 级）：无物品；锄 N 级压缩泥土需 N 级+锄头
        for (int level = 1; level <= LEVELS; level++) {
            String name = P[level - 1] + "_farmland";
            Block block = new CompressedFarmBlock(level);
            block.setHardness(0.6F);
            block.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
            BlockAccessors.sound(block, SoundType.GROUND);
            block.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
            block.setRegistryName(CompressedBlocks.MOD_ID, name);
            CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, null));
            CompressedBlocks.putFarmland(level, block);
        }
        // 5. 作物（4 种 × 3 级）：方块先建（种子物品随后回挂）
        for (CropDef c : CROPS) {
            for (int level = 1; level <= CROP_MAX; level++) {
                String p = P[level - 1];
                String blockName = p + "_" + c.key + "_plant";
                Block block = new CompressedCropBlock(level);
                block.setHardness(0.0F);
                block.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
                BlockAccessors.sound(block, SoundType.PLANT);
                block.setTranslationKey(CompressedBlocks.MOD_ID + "." + blockName);
                block.setRegistryName(CompressedBlocks.MOD_ID, blockName);
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(blockName, block, null));
            }
        }
        // 5b. 作物种子物品（= 方块的放置物品）：压缩胡萝卜/土豆同时是压缩食物
        for (CropDef c : CROPS) {
            for (int level = 1; level <= CROP_MAX; level++) {
                String p = P[level - 1];
                String seedItem = c.hasSeeds ? p + "_" + c.key + "_seeds" : p + "_" + c.key;
                Block block = CompressedBlocks.blockByName(p + "_" + c.key + "_plant");
                int foodN = c.hasSeeds ? 0
                    : (int) ((c.key.equals("carrot") ? 3 : 1) * CompressedBlocks.pow9(level));
                float foodS = c.hasSeeds ? 0.0F
                    : (c.key.equals("carrot") ? 1.8F : 0.3F) * CompressedBlocks.pow9(level);
                String enName = LEVEL_EN[level - 1] + " Compressed " + c.en
                    + (c.hasSeeds ? " Seeds" : "");
                Item item = new CompressedSoilItem(block, level, true, enName, foodN, foodS);
                item.setCreativeTab(CompressedBlocks.TAB_FOOD);
                item.setRegistryName(CompressedBlocks.MOD_ID, seedItem);
                item.setTranslationKey(CompressedBlocks.MOD_ID + "." + seedItem);
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(seedItem, item, CompressedBlocks.Tab.FOOD));
                ((CompressedCropBlock) block).setSeedItem(item);
                CompressedBlocks.rewriteBlockItemName(p + "_" + c.key + "_plant", seedItem);
            }
        }
        // 5c. 压缩小麦/甜菜根（不可种植的纯物品）
        for (CropDef c : CROPS) {
            if (!c.hasSeeds) {
                continue;
            }
            for (int level = 1; level <= CROP_MAX; level++) {
                String produce = P[level - 1] + "_" + c.key;
                Item produceItem = new Item();
                produceItem.setCreativeTab(CompressedBlocks.TAB_FOOD);
                produceItem.setRegistryName(CompressedBlocks.MOD_ID, produce);
                produceItem.setTranslationKey(CompressedBlocks.MOD_ID + "." + produce);
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(produce, produceItem, CompressedBlocks.Tab.FOOD));
            }
        }
        // 5d. 更多压缩种子（南瓜/西瓜 × 3 级）
        for (String seedId : new String[] {"pumpkin_seeds", "melon_seeds"}) {
            for (int level = 1; level <= CROP_MAX; level++) {
                String name = P[level - 1] + "_" + seedId;
                Item item = new Item();
                item.setCreativeTab(CompressedBlocks.TAB_FOOD);
                item.setRegistryName(CompressedBlocks.MOD_ID, name);
                item.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.FOOD));
            }
        }
        // 5f2. 压缩食物（18 种 × 3 级）——1.16.5 轮漏注册（lang/配方/贴图有、物品无），
        // 本轮补上：营养 ×9^N 封顶 3 级，饱食满可吃，溢出转回升
        for (FoodDef f : FOODS) {
            for (int level = 1; level <= FOOD_MAX; level++) {
                String p = P[level - 1];
                String name = p + "_" + f.key;
                Item item = new CompressedFoodItem(
                    (int) Math.min((long) f.nutrition * CompressedBlocks.pow9(level), Integer.MAX_VALUE / 4),
                    Math.min(f.saturation * CompressedBlocks.pow9(level), 3.4E38F));
                item.setCreativeTab(CompressedBlocks.TAB_FOOD);
                item.setRegistryName(CompressedBlocks.MOD_ID, name);
                item.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.FOOD));
            }
        }
        // 5e. 压缩甘蔗（纯甘蔗 + 36 材料各一条 × 9 重）
        for (int level = 1; level <= LEVELS; level++) {
            registerCane(P[level - 1] + "_cane", level, "Sugar Cane", null);
        }
        for (CompressedBlocks.StorageMatView m : CompressedBlocks.storage()) {
            String key = CompressedBlocks.caneKey(m.key());
            for (int level = 1; level <= LEVELS; level++) {
                registerCane(P[level - 1] + "_" + key + "_cane", level,
                    CompressedBlocks.caneEnName(m.enName()) + " Cane", m.key());
            }
        }
        // 5f. 盆栽 + 漏斗盆栽（原版配色，主 mod 版）
        registerPot("pot", false);
        registerPot("hopper_pot", true);
        // 6. 刷石机（3 压缩等级）
        String[] generatorTiers = {"cobblestone_generator", "2x_cobblestone_generator", "3x_cobblestone_generator"};
        for (int i = 0; i < generatorTiers.length; i++) {
            String name = generatorTiers[i];
            Block block = new CobblestoneGeneratorBlock(i + 1);
            block.setHardness(3.5F);
            block.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
            BlockAccessors.sound(block, SoundType.STONE);
            block.setHarvestLevel("pickaxe", 0);
            block.setLightOpacity(0);
            block.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
            block.setRegistryName(CompressedBlocks.MOD_ID, name);
            block.setCreativeTab(CompressedBlocks.TAB_TOOLS);
            ItemBlock item = new ItemBlock(block);
            item.setRegistryName(block.getRegistryName());
            item.setTranslationKey(block.getTranslationKey());
            CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
            CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.TOOLS));
        }
        // 7. 压缩箱子/压缩潜影盒（单一等级，243 格滚动存储）
        CompressedBlocks.COMPRESSED_CHEST = new CompressedChestBlock(false);
        CompressedBlocks.COMPRESSED_CHEST.setHardness(2.5F);
        CompressedBlocks.COMPRESSED_CHEST.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
        BlockAccessors.sound(CompressedBlocks.COMPRESSED_CHEST, SoundType.WOOD);
        CompressedBlocks.COMPRESSED_CHEST.setTranslationKey(CompressedBlocks.MOD_ID + ".compressed_chest");
        CompressedBlocks.COMPRESSED_CHEST.setRegistryName(CompressedBlocks.MOD_ID, "compressed_chest");
        CompressedBlocks.COMPRESSED_CHEST.setCreativeTab(CompressedBlocks.TAB_TOOLS);
        ItemBlock chestItem = new ItemBlock(CompressedBlocks.COMPRESSED_CHEST);
        chestItem.setRegistryName(CompressedBlocks.COMPRESSED_CHEST.getRegistryName());
        chestItem.setTranslationKey(CompressedBlocks.COMPRESSED_CHEST.getTranslationKey());
        CompressedBlocks.addBlock(new CompressedBlocks.BlockReg("compressed_chest", CompressedBlocks.COMPRESSED_CHEST, "compressed_chest"));
        CompressedBlocks.addItem(new CompressedBlocks.ItemReg("compressed_chest", chestItem, CompressedBlocks.Tab.TOOLS));
        CompressedBlocks.COMPRESSED_SHULKER = new CompressedShulkerBlock();
        CompressedBlocks.COMPRESSED_SHULKER.setHardness(2.5F);
        CompressedBlocks.COMPRESSED_SHULKER.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
        BlockAccessors.sound(CompressedBlocks.COMPRESSED_SHULKER, SoundType.STONE);
        CompressedBlocks.COMPRESSED_SHULKER.setTranslationKey(CompressedBlocks.MOD_ID + ".compressed_shulker_box");
        CompressedBlocks.COMPRESSED_SHULKER.setRegistryName(CompressedBlocks.MOD_ID, "compressed_shulker_box");
        CompressedBlocks.COMPRESSED_SHULKER.setCreativeTab(CompressedBlocks.TAB_TOOLS);
        ItemBlock shulkerItem = new ItemBlock(CompressedBlocks.COMPRESSED_SHULKER);
        shulkerItem.setRegistryName(CompressedBlocks.COMPRESSED_SHULKER.getRegistryName());
        shulkerItem.setTranslationKey(CompressedBlocks.COMPRESSED_SHULKER.getTranslationKey());
        CompressedBlocks.addBlock(new CompressedBlocks.BlockReg("compressed_shulker_box", CompressedBlocks.COMPRESSED_SHULKER, "compressed_shulker_box"));
        CompressedBlocks.addItem(new CompressedBlocks.ItemReg("compressed_shulker_box", shulkerItem, CompressedBlocks.Tab.TOOLS));
        // 8. 压缩金属包（22 材质 × 9 级）
        for (String key : COMPAT_METALS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = P[level - 1] + "_" + key + "_block";
                int light = key.equals("refined_glowstone") ? 15 : key.equals("lumium") ? 12 : 0;
                int harvest = key.equals("tin") || key.equals("lead") || key.equals("zinc")
                    || key.equals("plastic") ? 0
                    : key.equals("silver") || key.equals("nickel") || key.equals("bronze")
                    || key.equals("brass") || key.equals("electrum") || key.equals("invar")
                    || key.equals("constantan") || key.equals("steel") || key.equals("manasteel") ? 1 : 2;
                Block block = simpleBlock(name, Material.IRON, MapColor.IRON, SoundType.METAL,
                    5.0F, light, "pickaxe", harvest, true);
                block.setCreativeTab(CompressedBlocks.TAB_BLOCKS);
                ItemBlock item = new ItemBlock(block);
                item.setRegistryName(block.getRegistryName());
                item.setTranslationKey(block.getTranslationKey());
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.BLOCKS));
            }
        }
        // 9. 压缩护甲（石/木 各 4 件 × 9 级）：1.12.2 ArmorMaterial 是枚举，用 EnumHelper 动态加
        for (int level = 1; level <= LEVELS; level++) {
            String p = P[level - 1];
            int durabilityBase = (int) CompressedBlocks.armorDurabilityMult(level);
            net.minecraft.item.ItemArmor.ArmorMaterial stoneMat = EnumHelper.addArmorMaterial(
                "cb_stone_" + p, CompressedBlocks.MOD_ID + ":stone_" + p, durabilityBase,
                armorReductions(level), CompressedBlocks.armorEnchantability(),
                equipSound("iron"), 0.0F);
            net.minecraft.item.ItemArmor.ArmorMaterial woodMat = EnumHelper.addArmorMaterial(
                "cb_wood_" + p, CompressedBlocks.MOD_ID + ":wood_" + p, durabilityBase,
                armorReductions(level), CompressedBlocks.armorEnchantability(),
                equipSound("leather"), 0.0F);
            for (int piece = 0; piece < 4; piece++) {
                net.minecraft.inventory.EntityEquipmentSlot slot =
                    piece == 0 ? net.minecraft.inventory.EntityEquipmentSlot.HEAD
                    : piece == 1 ? net.minecraft.inventory.EntityEquipmentSlot.CHEST
                    : piece == 2 ? net.minecraft.inventory.EntityEquipmentSlot.LEGS
                    : net.minecraft.inventory.EntityEquipmentSlot.FEET;
                String pieceName = piece == 0 ? "helmet" : piece == 1 ? "chestplate"
                    : piece == 2 ? "leggings" : "boots";
                ItemArmor stoneArmor = new ItemArmor(stoneMat, 1, slot);
                ItemArmor woodArmor = new ItemArmor(woodMat, 1, slot);
                stoneArmor.setCreativeTab(CompressedBlocks.TAB_TOOLS);
                stoneArmor.setRegistryName(CompressedBlocks.MOD_ID, p + "_stone_" + pieceName);
                stoneArmor.setTranslationKey(CompressedBlocks.MOD_ID + "." + p + "_stone_" + pieceName);
                woodArmor.setCreativeTab(CompressedBlocks.TAB_TOOLS);
                woodArmor.setRegistryName(CompressedBlocks.MOD_ID, p + "_wood_" + pieceName);
                woodArmor.setTranslationKey(CompressedBlocks.MOD_ID + "." + p + "_wood_" + pieceName);
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(p + "_stone_" + pieceName, stoneArmor, CompressedBlocks.Tab.TOOLS));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(p + "_wood_" + pieceName, woodArmor, CompressedBlocks.Tab.TOOLS));
                CompressedBlocks.putArmorIndex(stoneArmor, piece, level);
                CompressedBlocks.putArmorIndex(woodArmor, piece, Math.max(1, level - 1));
                CompressedBlocks.addArmorReg(new CompressedBlocks.ArmorReg(p + "_stone_" + pieceName, stoneArmor, piece, level));
                CompressedBlocks.addArmorReg(new CompressedBlocks.ArmorReg(p + "_wood_" + pieceName, woodArmor, piece, level));
            }
        }
        // 10. 压缩工具（2 线 × 5 类 × 9 重）：1.12.2 ToolMaterial 也是枚举，EnumHelper 每组合一个
        for (CompressedBlocks.ToolKind kind : new CompressedBlocks.ToolKind[] {
            CompressedBlocks.ToolKind.STONE, CompressedBlocks.ToolKind.WOOD}) {
            String lineKey = kind == CompressedBlocks.ToolKind.STONE ? "cobblestone" : "wood";
            for (int level = 1; level <= LEVELS; level++) {
                String p = P[level - 1];
                int maxUses = CompressedBlocks.durabilityFor(level, kind);
                net.minecraft.item.Item.ToolMaterial material = EnumHelper.addToolMaterial(
                    "cb_" + lineKey + "_" + p,
                    CompressedBlocks.tierLevelFor(level), maxUses,
                    CompressedBlocks.speedLadder()[level - 1],
                    CompressedBlocks.toolMaterialDamage(kind, level),
                    kind == CompressedBlocks.ToolKind.STONE ? 5 : 15);
                // 修复材料：该线该重的压缩方块（圆石线）/ 任意同重压缩原木（木线）
                String repairName = kind == CompressedBlocks.ToolKind.WOOD
                    ? p + "_oak_log" : p + "_" + lineKey;
                Item repairItem = CompressedBlocks.itemByName(repairName);
                for (int t = 0; t < CompressedBlocks.TOOL_TYPES.length; t++) {
                    String toolType = CompressedBlocks.TOOL_TYPES[t];
                    String name = p + "_" + lineKey + "_" + toolType;
                    Item item;
                    if ("pickaxe".equals(toolType)) {
                        item = new RepairablePickaxe(material, repairItem);
                    } else if ("axe".equals(toolType)) {
                        item = new RepairableAxe(material, CompressedBlocks.toolAxeDamage(kind, level),
                            repairItem);
                    } else if ("shovel".equals(toolType)) {
                        item = new RepairableSpade(material, repairItem);
                    } else if ("sword".equals(toolType)) {
                        item = new RepairableSword(material, repairItem);
                    } else {
                        item = new CompressedHoeItem(material, level, repairItem);
                    }
                    item.setCreativeTab(CompressedBlocks.TAB_TOOLS);
                    item.setRegistryName(CompressedBlocks.MOD_ID, name);
                    item.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
                    if (level == LEVELS) {
                        LEVEL9_TOOLS.add(item);
                    }
                    CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.TOOLS));
                    float totalDamage = CompressedBlocks.toolBaseDamage(kind, t)
                        + CompressedBlocks.toolMaterialDamage(kind, level)
                        + (kind == CompressedBlocks.ToolKind.STONE ? 1.0F : 0.0F);
                    CompressedBlocks.addToolReg(new CompressedBlocks.ToolReg(name, item,
                        CompressedBlocks.durabilityFor(level, kind),
                        CompressedBlocks.speedLadder()[level - 1], level >= UNBREAKABLE_FROM, totalDamage));
                }
            }
        }
        // 11. 压缩木棍
        for (int level = 1; level <= LEVELS; level++) {
            String name = P[level - 1] + "_stick";
            Item item = new Item();
            item.setCreativeTab(CompressedBlocks.TAB_TOOLS);
            item.setRegistryName(CompressedBlocks.MOD_ID, name);
            item.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
            CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.TOOLS));
        }
    }

    /** 护甲每件防御数组（addArmorMaterial 要 4 元素 reductions）。 */
    private static int[] armorReductions(int level) {
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = CompressedBlocks.armorDefense(level, i);
        }
        return out;
    }

    private static SoundEvent equipSound(String kind) {
        return "iron".equals(kind)
            ? net.minecraft.init.SoundEvents.ITEM_ARMOR_EQUIP_IRON
            : net.minecraft.init.SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    private static void registerCane(String name, int level, String enName, String matKey) {
        Block block = new CompressedCaneBlock(level);
        block.setHardness(0.0F);
        block.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
        BlockAccessors.sound(block, SoundType.PLANT);
        block.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
        block.setRegistryName(CompressedBlocks.MOD_ID, name);
        Item item = new CompressedCaneItem(block, level, enName);
        item.setCreativeTab(CompressedBlocks.TAB_TOOLS);
        item.setRegistryName(block.getRegistryName());
        item.setTranslationKey(block.getTranslationKey());
        CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
        CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.TOOLS));
    }

    private static void registerPot(String name, boolean hopper) {
        Block block = new CompressedPotBlock(hopper);
        block.setHardness(1.5F);
        block.setResistance(CompressedBlocks.STORAGE_BLAST / 3.0F);
        BlockAccessors.sound(block, SoundType.STONE);
        block.setTranslationKey(CompressedBlocks.MOD_ID + "." + name);
        block.setRegistryName(CompressedBlocks.MOD_ID, name);
        block.setCreativeTab(CompressedBlocks.TAB_TOOLS);
        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(block.getRegistryName());
        item.setTranslationKey(block.getTranslationKey());
        CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
        CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.TOOLS));
    }

    // ------------------------------------------------------------------ 工具子类（修复材料任意重压缩方块）

    /** 圆石线镐：修复材料 = 对应重压缩圆石。 */
    private static final class RepairablePickaxe extends ItemPickaxe {
        private final Item repair;

        RepairablePickaxe(Item.ToolMaterial material, Item repair) {
            super(material);
            this.repair = repair;
        }

        @Override
        public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
            return repair.getItem() == this.repair;
        }
    }

    /** 圆石线斧：直接传总伤害（1.12.2 ItemAxe 的枚举索引越界规避）。 */
    private static final class RepairableAxe extends ItemAxe {
        private final Item repair;

        RepairableAxe(Item.ToolMaterial material, float damage, Item repair) {
            super(material, damage, -3.2F);
            this.repair = repair;
        }

        @Override
        public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
            return repair.getItem() == this.repair;
        }
    }

    /** 圆石线锹。 */
    private static final class RepairableSpade extends ItemSpade {
        private final Item repair;

        RepairableSpade(Item.ToolMaterial material, Item repair) {
            super(material);
            this.repair = repair;
        }

        @Override
        public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
            return repair.getItem() == this.repair;
        }
    }

    /** 圆石线剑。 */
    private static final class RepairableSword extends ItemSword {
        private final Item repair;

        RepairableSword(Item.ToolMaterial material, Item repair) {
            super(material);
            this.repair = repair;
        }

        @Override
        public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
            return repair.getItem() == this.repair;
        }
    }

    // ------------------------------------------------------------------ 客户端物品模型注册

    /** 1.12.2 必须为每个物品注册 ModelResourceLocation（生成器已产出 models/item/*.json）。 */
    @Mod.EventBusSubscriber(modid = CompressedBlocks.MOD_ID, value = Side.CLIENT)
    private static final class ClientModels {
        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public static void onModelRegistry(ModelRegistryEvent event) {
            for (CompressedBlocks.ItemReg e : CompressedBlocks.items()) {
                ModelLoader.setCustomModelResourceLocation(e.item, 0,
                    new ModelResourceLocation(CompressedBlocks.MOD_ID + ":" + e.name, "inventory"));
            }
        }
    }
}
