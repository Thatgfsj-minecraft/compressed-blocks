package dev.compressedblocks.forge;

import dev.compressedblocks.CobblestoneGeneratorBlock;
import dev.compressedblocks.CompressedArmorMaterial;
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
import dev.compressedblocks.CompressedSaplingBlock;
import dev.compressedblocks.CompressedShulkerBlock;
import dev.compressedblocks.CompressedSoilItem;
import dev.compressedblocks.CompressedTier;
import dev.compressedblocks.CobblestoneGeneratorBlockEntity;
import dev.compressedblocks.ScrollingContainerBlockEntity;
import dev.compressedblocks.ScrollingContainerMenu;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * 压缩方块 1.16.5 Forge 入口。
 * 1.16.5 注册表在构造期开放：core 构建后用 ForgeRegistries 循环注册（1549→1271 项，样板最少）。
 * 树特征走 FMLCommonSetupEvent.enqueueWork（世界生成注册表须在 worker 线程外写）。
 */
@Mod(CompressedBlocks.MOD_ID)
public class CompressedBlocksForge {
    // 压缩箱子/压缩潜影盒：方块实体类型与菜单类型（core 构建期赋值，方块/BE 引用）
    public static BlockEntityType<ScrollingContainerBlockEntity> CHEST_TYPE;
    public static BlockEntityType<ScrollingContainerBlockEntity> SHULKER_TYPE;
    public static BlockEntityType<CobblestoneGeneratorBlockEntity> GENERATOR_TYPE;
    public static MenuType<ScrollingContainerMenu> CHEST_MENU_TYPE;
    public static MenuType<ScrollingContainerMenu> SHULKER_MENU_TYPE;

    /** 压缩树特征：key = <wood>_<level>（AbstractTreeGrower 在世界生成时按名取用）。 */
    public static final Map<String, net.minecraft.world.level.levelgen.feature.ConfiguredFeature<
        net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration, ?>> TREE_FEATURES =
        new HashMap<>();

    public CompressedBlocksForge() {
        // 1. core 数据构建（全部方块/物品实例）
        CompressedBlocks.build(WOODS, CROPS, FOODS, COMPAT_METALS);
        // 2. 注册（1.16.5 构造期注册表开放）
        for (CompressedBlocks.BlockReg e : CompressedBlocks.blocks()) {
            net.minecraft.core.Registry.register(net.minecraft.core.Registry.BLOCK, id(e.name), e.block);
        }
        java.util.Set<String> done = new java.util.HashSet<>();
        for (CompressedBlocks.BlockReg e : CompressedBlocks.blocks()) {
            if (e.itemName != null) {
                Item item = CompressedBlocks.itemByName(e.itemName);
                net.minecraft.core.Registry.register(net.minecraft.core.Registry.ITEM, id(e.itemName), item);
                done.add(e.itemName);
            }
        }
        for (CompressedBlocks.ItemReg e : CompressedBlocks.items()) {
            if (!done.contains(e.name)) {
                net.minecraft.core.Registry.register(net.minecraft.core.Registry.ITEM, id(e.name), e.item);
            }
        }
        // 3. 方块实体类型与菜单类型
        CHEST_TYPE = BlockEntityType.Builder.of(
            () -> new ScrollingContainerBlockEntity(CHEST_TYPE, false),
            CompressedBlocks.COMPRESSED_CHEST).build(null);
        SHULKER_TYPE = BlockEntityType.Builder.of(
            () -> new ScrollingContainerBlockEntity(SHULKER_TYPE, true),
            CompressedBlocks.COMPRESSED_SHULKER).build(null);
        GENERATOR_TYPE = BlockEntityType.Builder.of(
            CobblestoneGeneratorBlockEntity::new,
            CompressedBlocks.blockByName("cobblestone_generator"),
            CompressedBlocks.blockByName("2x_cobblestone_generator"),
            CompressedBlocks.blockByName("3x_cobblestone_generator")).build(null);
        net.minecraft.core.Registry.register(net.minecraft.core.Registry.BLOCK_ENTITY_TYPE, id("compressed_chest"), CHEST_TYPE);
        net.minecraft.core.Registry.register(net.minecraft.core.Registry.BLOCK_ENTITY_TYPE, id("compressed_shulker_box"), SHULKER_TYPE);
        net.minecraft.core.Registry.register(net.minecraft.core.Registry.BLOCK_ENTITY_TYPE, id("cobblestone_generator"), GENERATOR_TYPE);
        net.minecraftforge.fml.network.IContainerFactory<ScrollingContainerMenu> chestFactory = (windowId, inv, data) -> {
            net.minecraft.core.BlockPos pos = data.readBlockPos();
            net.minecraft.world.level.block.entity.BlockEntity be = inv.player.level.getBlockEntity(pos);
            net.minecraft.world.Container backing = be instanceof ScrollingContainerBlockEntity
                ? (ScrollingContainerBlockEntity) be : null;
            return new ScrollingContainerMenu(CHEST_MENU_TYPE, windowId, inv, backing);
        };
        CHEST_MENU_TYPE = new MenuType<>(chestFactory);
        net.minecraftforge.fml.network.IContainerFactory<ScrollingContainerMenu> shulkerFactory = (windowId, inv, data) -> {
            net.minecraft.core.BlockPos pos = data.readBlockPos();
            net.minecraft.world.level.block.entity.BlockEntity be = inv.player.level.getBlockEntity(pos);
            net.minecraft.world.Container backing = be instanceof ScrollingContainerBlockEntity
                ? (ScrollingContainerBlockEntity) be : null;
            return new ScrollingContainerMenu(SHULKER_MENU_TYPE, windowId, inv, backing);
        };
        SHULKER_MENU_TYPE = new MenuType<>(shulkerFactory);
        net.minecraft.core.Registry.register(net.minecraft.core.Registry.MENU, id("compressed_chest"), CHEST_MENU_TYPE);
        net.minecraft.core.Registry.register(net.minecraft.core.Registry.MENU, id("compressed_shulker_box"), SHULKER_MENU_TYPE);
        // 4. 世界生成与事件
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CompressedBlocksForge::registerTreeFeatures);
    }

    /** 树苗生长特征：6 树 × 9 级，原版 oak 同款树形 + 同重数压缩原木/树叶（叶子 distance=7 自动凋落）。 */
    private static void registerTreeFeatures() {
        for (WoodDef w : WOODS) {
            for (int level = 1; level <= CompressedBlocks.LEVELS; level++) {
                String logName = CompressedBlocks.LEVEL_PREFIX_NAME()[level - 1] + "_" + w.key + "_log";
                Block log = CompressedBlocks.blockByName(logName);
                Block leaves = CompressedBlocks.blockByName(
                    CompressedBlocks.LEVEL_PREFIX_NAME()[level - 1] + "_" + w.key + "_leaves");
                if (log == null || leaves == null) {
                    continue;
                }
                net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider logProvider =
                    new net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider(
                        log.defaultBlockState());
                net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider leavesProvider =
                    new net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider(
                        leaves.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 7)
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false));
                net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration cfg =
                    new net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration
                        .TreeConfigurationBuilder(
                            logProvider,
                            leavesProvider,
                            new net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer(
                                net.minecraft.util.UniformInt.of(2, 1),
                                net.minecraft.util.UniformInt.of(0, 0), 3),
                            new net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer(
                                w.trunkBase, w.trunkRandA, 0),
                            new net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize(1, 0, 1))
                        .build();
                net.minecraft.world.level.levelgen.feature.ConfiguredFeature<
                    net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration, ?> cf =
                    net.minecraft.world.level.levelgen.feature.Feature.TREE.configured(cfg);
                net.minecraft.data.BuiltinRegistries.register(
                    net.minecraft.data.BuiltinRegistries.CONFIGURED_FEATURE,
                    new ResourceLocation(CompressedBlocks.MOD_ID, w.key + "_" + level), cf);
                TREE_FEATURES.put(w.key + "_" + level, cf);
            }
        }
    }

    /** 树苗生长器：世界生成期按 key 取已注册的压缩树特征。 */
    public static net.minecraft.world.level.block.grower.AbstractTreeGrower grower(String wood, int level) {
        String key = wood + "_" + level;
        return new net.minecraft.world.level.block.grower.AbstractTreeGrower() {
            @Override
            protected net.minecraft.world.level.levelgen.feature.ConfiguredFeature<
                net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration, ?> getConfiguredFeature(
                java.util.Random random, boolean hasFlowers) {
                return TREE_FEATURES.get(key);
            }
        };
    }

    // ------------------------------------------------------------------ 事件桥

    /** 护甲结算：抗性提升 + 八重飞行 + 九重饱食度常满。 */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level.isClientSide
            && event.player instanceof net.minecraft.server.level.ServerPlayer) {
            CompressedHooks.armorTick((net.minecraft.server.level.ServerPlayer) event.player);
        }
    }

    /** 九重满套：取消一切伤害（含 /kill）。 */
    @SubscribeEvent
    public void onIncomingDamage(LivingAttackEvent event) {
        if (event.getEntityLiving() instanceof net.minecraft.world.entity.player.Player
            && CompressedHooks.rejectDamage((net.minecraft.world.entity.player.Player) event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ 数据表（与 gen_1165_resources.py 严格一致）

    private static final class WoodDef implements CompressedBlocks.WoodDefView {
        final String key;
        final String en;
        final String zh;
        final int trunkBase;
        final int trunkRandA;

        WoodDef(String key, String en, String zh, int trunkBase, int trunkRandA) {
            this.key = key;
            this.en = en;
            this.zh = zh;
            this.trunkBase = trunkBase;
            this.trunkRandA = trunkRandA;
        }

        @Override public String key() { return key; }
        @Override public String en() { return en; }
        @Override public String zh() { return zh; }
    }

    private static final WoodDef[] WOODS = {
        new WoodDef("oak", "Oak", "橡树", 4, 2),
        new WoodDef("spruce", "Spruce", "云杉", 6, 3),
        new WoodDef("birch", "Birch", "白桦", 5, 2),
        new WoodDef("jungle", "Jungle", "丛林", 7, 6),
        new WoodDef("acacia", "Acacia", "金合欢", 5, 2),
        new WoodDef("dark_oak", "Dark Oak", "深色橡树", 5, 8),
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

    /** 22 兼容金属（与 gen_1165_resources.COMPAT_METALS 一致；挖掘档/发光由资源侧配方标签表达）。 */
    private static final String[] COMPAT_METALS = {
        "tin", "lead", "zinc", "plastic", "silver", "nickel", "bronze", "brass", "electrum",
        "invar", "constantan", "steel", "manasteel", "uranium", "osmium", "signalum",
        "enderium", "refined_obsidian", "refined_glowstone", "lumium", "terrasteel", "elementium"
    };

    // ------------------------------------------------------------------ core 构建回调（由 CompressedBlocks.build 调用）

    public static void constructAll() {
        String[] P = CompressedBlocks.LEVEL_PREFIX_NAME();
        int LEVELS = CompressedBlocks.LEVELS;
        int CROP_MAX = CompressedBlocks.CROP_MAX_LEVEL;
        int FOOD_MAX = CompressedBlocks.FOOD_MAX_LEVEL;
        int UNBREAKABLE_FROM = CompressedBlocks.UNBREAKABLE_FROM;

        // 1. 储存方块（39 材料 × 9 重）
        for (CompressedBlocks.StorageMatView m : CompressedBlocks.STORAGE_VIEW()) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = P[level - 1] + "_" + m.key();
                // 挖掘档位阶梯：基础档 + 3/4 重+1、5 重+2（黑曜石恒 3）
                int harvest = m.baseHarvest() >= 3 ? 3
                    : Math.min(2, m.baseHarvest() + (level >= 5 ? 2 : level >= 3 ? 1 : 0));
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of(m.material(), m.color())
                    .strength(CompressedBlocks.hardnessFor(m, level), CompressedBlocks.STORAGE_BLAST)
                    .sound(m.sound());
                if (m.requiresTool()) {
                    props = props.requiresCorrectToolForDrops();
                    if (m.tool() != null) {
                        props = props.harvestTool(m.tool()).harvestLevel(harvest);
                    }
                }
                if (m.light() > 0) {
                    props = props.lightLevel(state -> m.light());
                }
                Block block = new Block(props);
                BlockItem item = new BlockItem(block, new Item.Properties().tab(CompressedBlocks.TAB_BLOCKS));
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.BLOCKS, true));
                CompressedBlocks.addStorageBlock(CompressedBlocks.blocks().get(CompressedBlocks.blocks().size() - 1));
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
        // 2. 树叶（6 木 × 9 重）：原版凋落算法
        for (WoodDef w : WOODS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = P[level - 1] + "_" + w.key + "_leaves";
                Block block = new CompressedLeavesBlock(BlockBehaviour.Properties
                    .of(Material.PLANT, MaterialColor.PLANT)
                    .strength(0.2F, CompressedBlocks.STORAGE_BLAST)
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .noOcclusion());
                CompressedBlocks.addLeavesBlock(block);
                BlockItem item = new BlockItem(block, new Item.Properties().tab(CompressedBlocks.TAB_BLOCKS));
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.BLOCKS, true));
            }
        }
        // 3. 树苗（6 木 × 9 重）：只能种在等级足够的压缩泥土上
        for (WoodDef w : WOODS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = P[level - 1] + "_" + w.key + "_sapling";
                Block block = new CompressedSaplingBlock(grower(w.key, level), level,
                    BlockBehaviour.Properties.of(Material.PLANT, MaterialColor.PLANT)
                        .strength(0.0F, CompressedBlocks.STORAGE_BLAST)
                        .noCollission()
                        .randomTicks()
                        .sound(SoundType.GRASS));
                String enName = LEVEL_EN[level - 1] + " Compressed " + w.en + " Sapling";
                Item item = new CompressedSoilItem(block,
                    new Item.Properties().tab(CompressedBlocks.TAB_TOOLS), level, false, enName);
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.POTS, true));
                CompressedBlocks.addSaplingBlock(block);
            }
        }
        // 4. 压缩耕地（9 级）：无物品；锄 N 级压缩泥土需 N 级+锄头
        for (int level = 1; level <= LEVELS; level++) {
            String name = P[level - 1] + "_farmland";
            Block block = new CompressedFarmBlock(level, BlockBehaviour.Properties
                .of(Material.DIRT, MaterialColor.DIRT)
                .strength(0.6F, CompressedBlocks.STORAGE_BLAST)
                .sound(SoundType.GRAVEL));
            CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, null));
            CompressedBlocks.putFarmland(level, block);
        }
        // 5. 作物（4 种 × 3 级）：方块先建（种子物品 1.16.5 必须引用方块，随后 5b 建物品回挂）
        for (CropDef c : CROPS) {
            for (int level = 1; level <= CROP_MAX; level++) {
                String p = P[level - 1];
                String blockName = p + "_" + c.key + "_plant";
                Block block = new CompressedCropBlock(level, BlockBehaviour.Properties
                    .of(Material.PLANT, MaterialColor.PLANT)
                    .strength(0.0F, CompressedBlocks.STORAGE_BLAST)
                    .noCollission()
                    .randomTicks()
                    .sound(SoundType.CROP));
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(blockName, block, null));
            }
        }
        // 5b. 作物种子物品（= 方块的放置物品，id 与种子名相同）：压缩胡萝卜/土豆同时是压缩食物
        for (CropDef c : CROPS) {
            for (int level = 1; level <= CROP_MAX; level++) {
                String p = P[level - 1];
                String seedItem = c.hasSeeds ? p + "_" + c.key + "_seeds" : p + "_" + c.key;
                Block block = CompressedBlocks.blockByName(p + "_" + c.key + "_plant");
                int foodN = c.hasSeeds ? 0
                    : (int) ((c.key.equals("carrot") ? 3 : 1) * pow9(level));
                float foodS = c.hasSeeds ? 0.0F
                    : (c.key.equals("carrot") ? 1.8F : 0.3F) * pow9(level);
                String enName = LEVEL_EN[level - 1] + " Compressed " + c.en
                    + (c.hasSeeds ? " Seeds" : "");
                Item.Properties itemProps = new Item.Properties().tab(CompressedBlocks.TAB_FOOD);
                if (foodN > 0) {
                    itemProps = itemProps.food(new FoodProperties.Builder()
                        .nutrition(foodN).saturationMod(Math.min(1.0F, foodS / (foodN * 2.0F)))
                        .alwaysEat().build());
                }
                Item item = new CompressedSoilItem(block, itemProps, level, true, enName, foodN);
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(seedItem, item, CompressedBlocks.Tab.FOOD, false));
                ((CompressedCropBlock) block).setSeedItem(item);
                // 回填方块登记的物品名（作物方块登记时 itemName=null，这里改写为种子 id）
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
                Item produceItem = new Item(new Item.Properties().tab(CompressedBlocks.TAB_FOOD));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(produce, produceItem, CompressedBlocks.Tab.FOOD, false));
            }
        }
        // 5d. 更多压缩种子（南瓜/西瓜 × 3 级）
        for (String seedId : new String[] {"pumpkin_seeds", "melon_seeds"}) {
            for (int level = 1; level <= CROP_MAX; level++) {
                String name = P[level - 1] + "_" + seedId;
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name,
                    new Item(new Item.Properties().tab(CompressedBlocks.TAB_FOOD)),
                    CompressedBlocks.Tab.FOOD, false));
            }
        }
        // 5e. 压缩甘蔗（纯甘蔗 + 39 材料各一条 × 9 重）
        for (int level = 1; level <= LEVELS; level++) {
            registerCane(P[level - 1] + "_cane", level, "Sugar Cane", null);
        }
        for (CompressedBlocks.StorageMatView m : CompressedBlocks.STORAGE_VIEW()) {
            String key = caneKey(m.key());
            for (int level = 1; level <= LEVELS; level++) {
                registerCane(P[level - 1] + "_" + key + "_cane", level,
                    caneEnName(m.enName()) + " Cane", m.key());
            }
        }
        // 6. 刷石机（3 压缩等级）
        String[] generatorTiers = {"cobblestone_generator", "2x_cobblestone_generator", "3x_cobblestone_generator"};
        for (int i = 0; i < generatorTiers.length; i++) {
            String name = generatorTiers[i];
            CobblestoneGeneratorBlock block = new CobblestoneGeneratorBlock(i + 1,
                BlockBehaviour.Properties.of(Material.STONE, MaterialColor.COLOR_GRAY)
                    .strength(3.5F, CompressedBlocks.STORAGE_BLAST)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .noOcclusion());
            BlockItem item = new BlockItem(block, new Item.Properties().tab(CompressedBlocks.TAB_TOOLS));
            CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
            CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.TOOLS, true));
        }
        // 7. 压缩箱子/压缩潜影盒（单一等级，243 格滚动存储）
        CompressedBlocks.COMPRESSED_CHEST = new CompressedChestBlock(BlockBehaviour.Properties
            .of(Material.WOOD, MaterialColor.WOOD)
            .strength(2.5F, CompressedBlocks.STORAGE_BLAST)
            .sound(SoundType.WOOD)
            .noOcclusion());
        BlockItem chestItem = new BlockItem(CompressedBlocks.COMPRESSED_CHEST,
            new Item.Properties().tab(CompressedBlocks.TAB_TOOLS));
        CompressedBlocks.addBlock(new CompressedBlocks.BlockReg("compressed_chest", CompressedBlocks.COMPRESSED_CHEST, "compressed_chest"));
        CompressedBlocks.addItem(new CompressedBlocks.ItemReg("compressed_chest", chestItem, CompressedBlocks.Tab.TOOLS, true));
        CompressedBlocks.COMPRESSED_SHULKER = new CompressedShulkerBlock(BlockBehaviour.Properties
            .of(Material.STONE, MaterialColor.COLOR_PURPLE)
            .strength(2.5F, CompressedBlocks.STORAGE_BLAST)
            .sound(SoundType.STONE)
            .noOcclusion());
        BlockItem shulkerItem = new BlockItem(CompressedBlocks.COMPRESSED_SHULKER,
            new Item.Properties().tab(CompressedBlocks.TAB_TOOLS));
        CompressedBlocks.addBlock(new CompressedBlocks.BlockReg("compressed_shulker_box", CompressedBlocks.COMPRESSED_SHULKER, "compressed_shulker_box"));
        CompressedBlocks.addItem(new CompressedBlocks.ItemReg("compressed_shulker_box", shulkerItem, CompressedBlocks.Tab.TOOLS, true));
        // 8. 压缩金属包（22 材质 × 9 级）
        for (String key : COMPAT_METALS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = P[level - 1] + "_" + key + "_block";
                BlockBehaviour.Properties props = BlockBehaviour.Properties
                    .of(Material.METAL, MaterialColor.METAL)
                    .strength(5.0F, CompressedBlocks.STORAGE_BLAST)
                    .requiresCorrectToolForDrops()
                    .harvestTool(ToolType.PICKAXE)
                    .harvestLevel(key.equals("tin") || key.equals("lead") || key.equals("zinc")
                        || key.equals("plastic") ? 0
                        : key.equals("silver") || key.equals("nickel") || key.equals("bronze")
                        || key.equals("brass") || key.equals("electrum") || key.equals("invar")
                        || key.equals("constantan") || key.equals("steel") || key.equals("manasteel") ? 1 : 2)
                    .sound(SoundType.METAL);
                if (key.equals("refined_glowstone")) {
                    int light = 15;
                    props = props.lightLevel(state -> light);
                } else if (key.equals("lumium")) {
                    int light = 12;
                    props = props.lightLevel(state -> light);
                }
                Block block = new Block(props);
                BlockItem item = new BlockItem(block, new Item.Properties().tab(CompressedBlocks.TAB_BLOCKS));
                CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.BLOCKS, true));
            }
        }
        // 9. 压缩护甲（石/木 各 4 件 × 9 级）
        for (int level = 1; level <= LEVELS; level++) {
            String p = P[level - 1];
            long mult = level >= UNBREAKABLE_FROM ? Integer.MAX_VALUE / 16L : pow9(level);
            CompressedArmorMaterial stoneMat = new CompressedArmorMaterial("stone_" + p, level, (int) mult,
                SoundEvents.ARMOR_EQUIP_IRON,
                () -> new ItemStack(CompressedBlocks.itemByName(p + "_stone")));
            CompressedArmorMaterial woodMat = new CompressedArmorMaterial("wood_" + p, level, (int) mult,
                SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> new ItemStack(CompressedBlocks.itemByName(p + "_oak_log")));
            for (int piece = 0; piece < 4; piece++) {
                EquipmentSlot slot = piece == 0 ? EquipmentSlot.HEAD
                    : piece == 1 ? EquipmentSlot.CHEST
                    : piece == 2 ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
                String pieceName = piece == 0 ? "helmet" : piece == 1 ? "chestplate"
                    : piece == 2 ? "leggings" : "boots";
                Item stoneArmor = new ArmorItem(stoneMat, slot,
                    new Item.Properties().tab(CompressedBlocks.TAB_TOOLS));
                Item woodArmor = new ArmorItem(woodMat, slot,
                    new Item.Properties().tab(CompressedBlocks.TAB_TOOLS));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(p + "_stone_" + pieceName, stoneArmor, CompressedBlocks.Tab.TOOLS, false));
                CompressedBlocks.addItem(new CompressedBlocks.ItemReg(p + "_wood_" + pieceName, woodArmor, CompressedBlocks.Tab.TOOLS, false));
                CompressedBlocks.putArmorIndex(stoneArmor, piece, level);
                CompressedBlocks.putArmorIndex(woodArmor, piece, Math.max(1, level - 1));
            }
        }
        // 10. 压缩工具（2 线 × 5 类 × 9 重）
        for (CompressedBlocks.ToolLineView line : CompressedBlocks.TOOL_LINES_VIEW()) {
            for (int t = 0; t < CompressedBlocks.TOOL_TYPES().size(); t++) {
                for (int level = 1; level <= LEVELS; level++) {
                    String name = P[level - 1] + "_" + line.key() + "_" + CompressedBlocks.TOOL_TYPES().get(t);
                    float base = CompressedBlocks.toolBaseDamage(line, t);
                    float speed = CompressedBlocks.toolSpeed(line, t);
                    float bonus = CompressedBlocks.toolBonus(line, level, t);
                    // 1.16.5 工具攻击参数是整数：小数差并入 tier bonus，保持总伤害与 1.21 一致
                    int baseInt = Math.round(base);
                    float bonusAdjusted = bonus + (base - baseInt);
                    String repairName = line.key().equals("wood") ? P[level - 1] + "_oak_log"
                        : P[level - 1] + "_" + line.key();
                    Tier tier = new CompressedTier(level, line.kind(),
                        CompressedBlocks.SPEED()[level - 1], bonusAdjusted, line.enchantability(),
                        () -> {
                            Item repairItem = CompressedBlocks.itemByName(repairName);
                            return repairItem == null ? ItemStack.EMPTY : new ItemStack(repairItem);
                        });
                    Item.Properties props = new Item.Properties().tab(CompressedBlocks.TAB_TOOLS);
                String toolType = CompressedBlocks.TOOL_TYPES().get(t);
                Item item;
                if ("pickaxe".equals(toolType)) {
                    item = new PickaxeItem(tier, baseInt, speed, props);
                } else if ("sword".equals(toolType)) {
                    item = new SwordItem(tier, baseInt, speed, props);
                } else if ("axe".equals(toolType)) {
                    item = new AxeItem(tier, base + bonusAdjusted, speed, props);
                } else if ("shovel".equals(toolType)) {
                    item = new ShovelItem(tier, base + bonusAdjusted, speed, props);
                } else {
                    item = new CompressedHoeItem(tier, baseInt, speed, level, props);
                }
                    if (level == LEVELS) {
                        CompressedHooks.registerLevel9Tool(item);
                    }
                    CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.TOOLS, false));
                    CompressedBlocks.addToolReg(new CompressedBlocks.ToolReg(name, item,
                        CompressedBlocks.durabilityFor(level, line.kind()),
                        CompressedBlocks.SPEED()[level - 1], level >= UNBREAKABLE_FROM,
                        1.0 + base + bonus));
                }
            }
        }
        // 11. 压缩木棍
        for (int level = 1; level <= LEVELS; level++) {
            String name = P[level - 1] + "_stick";
            CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name,
                new Item(new Item.Properties().tab(CompressedBlocks.TAB_TOOLS)),
                CompressedBlocks.Tab.INGREDIENTS, false));
        }
    }

    private static void registerCane(String name, int level, String enName, String matKey) {
        Block block = new CompressedCaneBlock(level, BlockBehaviour.Properties
            .of(Material.PLANT, MaterialColor.PLANT)
            .noCollission()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS));
        Item item = new CompressedCaneItem(block,
            new Item.Properties().tab(CompressedBlocks.TAB_TOOLS), level, enName);
        CompressedBlocks.addBlock(new CompressedBlocks.BlockReg(name, block, name));
        CompressedBlocks.addItem(new CompressedBlocks.ItemReg(name, item, CompressedBlocks.Tab.POTS, true));
    }

    private static long pow9(int level) {
        long value = 1;
        for (int i = 0; i < level; i++) {
            value *= 9;
        }
        return value;
    }

    private static final String[] LEVEL_EN = {
        "Compressed", "Double Compressed", "Triple Compressed", "Quadruple Compressed", "Quintuple Compressed",
        "Sextuple Compressed", "Septuple Compressed", "Octuple Compressed", "Nonuple Compressed"
    };

    /** 甘蔗 id 的材料段：去掉 "_block" 后缀，与生成器 cane_key 一致。 */
    private static String caneKey(String matKey) {
        return matKey.endsWith("_block") ? matKey.substring(0, matKey.length() - 6) : matKey;
    }

    /** 甘蔗英文显示名：材料名去掉 "Block of " 前缀 / " Block" / " Bale" 后缀。 */
    private static String caneEnName(String matEn) {
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

    public static ResourceLocation id(String path) {
        return new ResourceLocation(CompressedBlocks.MOD_ID, path);
    }
}
