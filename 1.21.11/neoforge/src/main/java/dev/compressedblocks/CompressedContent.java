package dev.compressedblocks;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import static dev.compressedblocks.CompressedBlocks.*;

/**
 * 压缩方块内容构建：由 {@link CompressedBlocks} 的静态初始化块原样迁出，
 * 仍在 CompressedBlocks 静态块的同一位置调用，保证字段初始化/注册顺序与数值不变。
 */
final class CompressedContent {
    private CompressedContent() {
    }

    /** 原 CompressedBlocks 静态初始化块体，逐行原样搬移（仅外层声明变化）。 */
    static void init() {
        // 1. 储存方块（51 材料 × 9 重）：统一硬度公式 + 1200 爆炸抗性
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
                if (m.key().equals("netherrack")) {
                    NETHER_SOIL_LEVELS.put(block, level);
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
        // 5c. 盆栽 + 漏斗盆栽（原版配色，主 mod 版）：只能种原版系作物/甘蔗/树木；
        // 漏斗盆栽成熟自动收割入下方容器（兼容储物抽屉）；压缩植物/压缩盆栽在附属 mod
        POT = new CompressedPotBlock(BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id("pot")))
            .mapColor(MapColor.TERRACOTTA_ORANGE)
            .strength(1.5F, STORAGE_BLAST)
            .sound(SoundType.STONE)
            .noOcclusion());
        HOPPER_POT = new CompressedPotBlock(BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id("hopper_pot")))
            .mapColor(MapColor.TERRACOTTA_ORANGE)
            .strength(1.5F, STORAGE_BLAST)
            .sound(SoundType.STONE)
            .noOcclusion(), true);
        // POT_TYPE 由各加载器入口构建后注入（1.21.11 原版构造器私有，Fabric/NeoForge 各有公开构建路径）
        // 普通盆栽与漏斗盆栽放压缩工具栏（压缩盆栽创造栏由附属 mod 独有）
        for (String name : new String[] {"pot", "hopper_pot"}) {
            Block block = name.equals("pot") ? POT : HOPPER_POT;
            BlockItem item = new BlockItem(block,
                new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))
                    .useBlockDescriptionPrefix());
            BLOCKS.add(new BlockReg(name, block, name));
            ITEMS.add(new ItemReg(name, item, Tab.TOOLS, true));
        }
        // 5d. 刷石机（3 压缩等级）：每秒产出 1 个对应等级原石/压缩原石，压入下方容器
        String[] generatorTiers = {"cobblestone_generator", "2x_cobblestone_generator",
            "3x_cobblestone_generator"};
        for (int i = 0; i < generatorTiers.length; i++) {
            String name = generatorTiers[i];
            CobblestoneGeneratorBlock block = new CobblestoneGeneratorBlock(i + 1,
                BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.5F, STORAGE_BLAST)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .noOcclusion());
            BlockItem item = new BlockItem(block,
                new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))
                    .useBlockDescriptionPrefix());
            BLOCKS.add(new BlockReg(name, block, name));
            ITEMS.add(new ItemReg(name, item, Tab.TOOLS, true));
            GENERATOR_BLOCKS.add(block);
        }
        // 5f. 压缩熔炉（4 个压缩等级）：单路 2x/3x/6x/27x、整机 6x/18x/54x/243x 与阶梯权限
        for (int tier = 1; tier <= FURNACE_TIERS; tier++) {
            String name = LEVEL_PREFIX[tier - 1] + "_compressed_furnace";
            CompressedFurnaceBlock block = new CompressedFurnaceBlock(tier,
                BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.5F, STORAGE_BLAST)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT) ? 13 : 0));
            BlockItem item = new BlockItem(block,
                new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))
                    .useBlockDescriptionPrefix());
            BLOCKS.add(new BlockReg(name, block, name));
            ITEMS.add(new ItemReg(name, item, Tab.TOOLS, true));
            FURNACE_BLOCKS.add(block);
        }
        // 5g. 压缩漏斗（3 个压缩等级）：16 / 64 / 576 传输速率，9 格槽位
        for (int tier = 1; tier <= HOPPER_TIERS; tier++) {
            String name = LEVEL_PREFIX[tier - 1] + "_compressed_hopper";
            CompressedHopperBlock block = new CompressedHopperBlock(tier,
                BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, STORAGE_BLAST)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion());
            BlockItem item = new BlockItem(block,
                new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))
                    .useBlockDescriptionPrefix());
            BLOCKS.add(new BlockReg(name, block, name));
            ITEMS.add(new ItemReg(name, item, Tab.TOOLS, true));
            HOPPER_BLOCKS.add(block);
        }
        // 5h. 木质漏斗体系（4 个等级）：基础木漏斗（速度为原版一半），以及一至三重压缩木漏斗（速度对应压缩铁漏斗的一半）
        for (int tier = 0; tier < WOODEN_HOPPER_TIERS; tier++) {
            String name = tier == 0 ? "wooden_hopper" : LEVEL_PREFIX[tier - 1] + "_compressed_wooden_hopper";
            float blast = tier == 0 ? 3.0F : STORAGE_BLAST;
            WoodenHopperBlock block = new WoodenHopperBlock(tier,
                BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, blast)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
            BlockItem item = new BlockItem(block,
                new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id(name)))
                    .useBlockDescriptionPrefix());
            BLOCKS.add(new BlockReg(name, block, name));
            ITEMS.add(new ItemReg(name, item, Tab.TOOLS, true));
            WOODEN_HOPPER_BLOCKS.add(block);
        }
        // 5e. 压缩箱子/压缩潜影盒（单一等级）：243 格滚动存储，压缩工具栏；
        // 箱子破坏洒出内容，潜影盒破坏内容随物品保留；不能拼大箱子（右键各开各的菜单）
        COMPRESSED_CHEST = new CompressedChestBlock(BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id("compressed_chest")))
            .mapColor(MapColor.WOOD)
            .strength(2.5F, STORAGE_BLAST)
            .sound(SoundType.WOOD)
            .noOcclusion());
        BlockItem chestItem = new BlockItem(COMPRESSED_CHEST, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, id("compressed_chest")))
            .useBlockDescriptionPrefix());
        BLOCKS.add(new BlockReg("compressed_chest", COMPRESSED_CHEST, "compressed_chest"));
        ITEMS.add(new ItemReg("compressed_chest", chestItem, Tab.TOOLS, true));
        COMPRESSED_SHULKER = new CompressedShulkerBlock(BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id("compressed_shulker_box")))
            .mapColor(MapColor.COLOR_PURPLE)
            .strength(2.5F, STORAGE_BLAST)
            .sound(SoundType.STONE)
            .noOcclusion());
        BlockItem shulkerItem = new BlockItem(COMPRESSED_SHULKER, new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, id("compressed_shulker_box")))
            .useBlockDescriptionPrefix());
        BLOCKS.add(new BlockReg("compressed_shulker_box", COMPRESSED_SHULKER, "compressed_shulker_box"));
        ITEMS.add(new ItemReg("compressed_shulker_box", shulkerItem, Tab.TOOLS, true));
        buildCompatMetals();
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
        // 7. 压缩护甲（石制 36 件 + 木质 36 件 = 4 部位 × 9 重 × 2 线，共 72 件）：铁级基准；九重石甲满套免疫一切伤害
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
        //     不毁仍从 6 重开始；9 重木甲有效 8 重（头盔夜视+水下呼吸、护腿抗性 II、胸甲飞行），无 9 重石甲的不死/急迫档
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

    /** 注册压缩金属包：22 材质 × 9 级；挖掘档 stone/iron/diamond；发光金属带光。 */
    private static void buildCompatMetals() {
        for (CompatMetal m : COMPAT_METALS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = LEVEL_PREFIX[level - 1] + "_" + m.key() + "_block";
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, STORAGE_BLAST)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL);
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
            }
        }
    }

    private static Item buildTool(ToolLine line, int level, int toolIndex, String name) {
        boolean stone = line.kind() == ToolKind.STONE;
        float[] speedLadder = stone ? STONE_SPEED : WOOD_SPEED;
        float speed = speedLadder[level - 1];
        float[] stats = line.stats()[toolIndex];
        // 全部工具：总伤害（含玩家基础 1 点）= 对应原版工具 × 1.6^重（×2 太夸张，用户定档 1.6）
        float bonus = (1.0F + stats[0] + line.damageBonus()) * (float) Math.pow(1.6, level)
            - 1.0F - stats[0];
        ToolMaterial material = new ToolMaterial(
            stone ? stoneIncorrectTag(level) : woodIncorrectTag(level),
            durabilityFor(level, line.kind()),
            speed,
            bonus,
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
        double damage = stats[0] + bonus;
        TOOL_REGS.add(new ToolReg(name, item, durabilityFor(level, line.kind()), speed, unbreakable, damage));
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
}
