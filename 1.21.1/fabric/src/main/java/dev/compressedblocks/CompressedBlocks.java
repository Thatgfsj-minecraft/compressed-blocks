package dev.compressedblocks;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 压缩方块核心（1.21.1）：纯数据驱动，无任何交互逻辑。
 * 与 1.21.11 的差异：ResourceLocation（非 Identifier）、Tier 体系工具（无 ToolMaterial）、
 * Unbreakable 是 record(boolean)、方块/物品无需 setId、Registry.get 直接返回值。
 * 本类不 import 任何加载器类（组织约定：core 每版本双加载器逐字节一致）。
 */
public final class CompressedBlocks {
    public static final String MOD_ID = "compressedblocks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final int LEVELS = 9;
    /** 六重及以上：不可破坏（原版 unbreakable 组件）。 */
    public static final int UNBREAKABLE_FROM = 6;

    private static final String[] LEVEL_PREFIX = {
        "compressed", "double_compressed", "triple_compressed", "quadruple_compressed", "quintuple_compressed",
        "sextuple_compressed", "septuple_compressed", "octuple_compressed", "nonuple_compressed"
    };

    /** 挖掘速度阶梯：一重=铁 6.0、二重=钻 8.0、三重=金 12.0，四重=三重×1.5、五重=四重×1.5……逐级连乘。 */
    private static final float[] SPEED = {6.0F, 8.0F, 12.0F, 18.0F, 27.0F, 40.5F, 60.75F, 91.125F, 136.6875F};

    public enum ToolKind { NONE, STONE, WOOD }

    /** 创造物品栏落位。 */
    public enum Tab { BLOCKS, TOOLS, INGREDIENTS }

    /**
     * 基础材料。数值来自 1.21.1 反编译原版 Blocks.java。
     * 原木 = logProperties：strength(2.0)+WOOD 声音、无需工具。
     */
    private static final List<Material> MATERIALS = List.of(
        new Material("cobblestone", ToolKind.STONE, SoundType.STONE, MapColor.STONE, true, 2.0F, 6.0F),
        new Material("stone", ToolKind.STONE, SoundType.STONE, MapColor.STONE, true, 1.5F, 6.0F),
        new Material("cobbled_deepslate", ToolKind.STONE, SoundType.DEEPSLATE, MapColor.DEEPSLATE, true, 3.5F, 6.0F),
        new Material("deepslate", ToolKind.STONE, SoundType.DEEPSLATE, MapColor.DEEPSLATE, true, 3.0F, 6.0F),
        new Material("oak_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("spruce_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("birch_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("jungle_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("acacia_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("dark_oak_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("mangrove_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("cherry_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("pale_oak_log", ToolKind.WOOD, SoundType.WOOD, MapColor.WOOD, false, 2.0F, 2.0F),
        new Material("dirt", ToolKind.NONE, SoundType.GRAVEL, MapColor.DIRT, false, 0.5F, 0.5F),
        new Material("sand", ToolKind.NONE, SoundType.SAND, MapColor.SAND, false, 0.5F, 0.5F),
        new Material("gravel", ToolKind.NONE, SoundType.GRAVEL, MapColor.STONE, false, 0.6F, 0.6F),
        new Material("netherrack", ToolKind.NONE, SoundType.NETHERRACK, MapColor.NETHER, true, 0.4F, 0.4F),
        new Material("end_stone", ToolKind.NONE, SoundType.STONE, MapColor.SAND, true, 3.0F, 9.0F),
        new Material("obsidian", ToolKind.NONE, SoundType.STONE, MapColor.COLOR_BLACK, true, 50.0F, 1200.0F)
    );

    private static final String[] TOOLS = {"pickaxe", "axe", "shovel", "hoe", "sword"};

    /** 原版石级工具 {attackDamage, attackSpeed}（Items.java 960-973）。 */
    private static final float[][] STONE_TOOL_STATS = {
        {1.0F, -2.8F}, {7.0F, -3.2F}, {1.5F, -3.0F}, {-1.0F, -2.0F}, {3.0F, -2.4F}
    };

    /** 原版木级工具 {attackDamage, attackSpeed}（Items.java 945-958）。 */
    private static final float[][] WOOD_TOOL_STATS = {
        {1.0F, -2.8F}, {6.0F, -3.2F}, {1.5F, -3.0F}, {0.0F, -3.0F}, {3.0F, -2.4F}
    };

    public record Material(String key, ToolKind toolKind, SoundType sound, MapColor color,
                           boolean requiresTool, float hardness, float blast) {
    }

    public record BlockEntry(String name, Block block, BlockItem item) {
    }

    /** 工具/木棍物品；工具附耐久/速度/伤害期望值供启动自检比对。 */
    public record ItemEntry(String name, Item item, Tab tab, int expectedDurability, float expectedSpeed,
                            boolean expectedUnbreakable, double expectedAttackDamage) {
        public ItemEntry(String name, Item item, Tab tab) {
            this(name, item, tab, 0, 0.0F, false, 0.0);
        }
    }

    private static final List<BlockEntry> BLOCKS = new ArrayList<>();
    private static final List<ItemEntry> ITEMS = new ArrayList<>();

    public static List<BlockEntry> blocks() {
        return BLOCKS;
    }

    public static List<ItemEntry> items() {
        return ITEMS;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 第 n 重耐久：9ⁿ × 基础；六重起不可破坏，直接给满值（配合 unbreakable 组件）。 */
    public static int durabilityFor(int level, ToolKind kind) {
        if (level >= UNBREAKABLE_FROM) {
            return Integer.MAX_VALUE;
        }
        long base = kind == ToolKind.STONE ? 131 : 59;
        long value = base;
        for (int i = 0; i < level; i++) {
            value *= 9;
        }
        return (int) value;
    }

    /** 1.21.1 的 Tier：耐久=9ⁿ、速度=阶梯、伤害加成=原版+重数、修复材料=该材料压缩方块标签。 */
    private record CompressedTier(int uses, float speed, float damageBonus, TagKey<Block> incorrect,
                                  int enchantValue, Ingredient repair) implements Tier {
        @Override
        public int getUses() {
            return this.uses;
        }

        @Override
        public float getSpeed() {
            return this.speed;
        }

        @Override
        public float getAttackDamageBonus() {
            return this.damageBonus;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return this.incorrect;
        }

        @Override
        public int getEnchantmentValue() {
            return this.enchantValue;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return this.repair;
        }
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

    static {
        for (Material m : MATERIALS) {
            for (int level = 1; level <= LEVELS; level++) {
                String name = LEVEL_PREFIX[level - 1] + "_" + m.key();
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .mapColor(m.color())
                    .sound(m.sound())
                    .strength(m.hardness() * level, m.blast() * level);
                if (m.requiresTool()) {
                    props = props.requiresCorrectToolForDrops();
                }
                Block block = new Block(props);
                BlockItem item = new BlockItem(block, new Item.Properties());
                BLOCKS.add(new BlockEntry(name, block, item));
            }
        }
        for (Material m : MATERIALS) {
            if (m.toolKind() == ToolKind.NONE) {
                continue;
            }
            // 排序：材料 → 工具类型 → 等级（压缩木剑 1-9 一组、压缩石剑 1-9 一组……）
            for (int t = 0; t < TOOLS.length; t++) {
                for (int level = 1; level <= LEVELS; level++) {
                    String name = LEVEL_PREFIX[level - 1] + "_" + m.key() + "_" + TOOLS[t];
                    int durability = durabilityFor(level, m.toolKind());
                    boolean unbreakable = level >= UNBREAKABLE_FROM;
                    Item item = buildTool(m, level, t, name, unbreakable);
                    if (level == LEVELS) {
                        CompressedHooks.registerLevel9Tool(item);
                    }
                    float[] stats = m.toolKind() == ToolKind.STONE ? STONE_TOOL_STATS[t] : WOOD_TOOL_STATS[t];
                    double expectedDamage = stats[0] + (m.toolKind() == ToolKind.STONE ? 1.0F : 0.0F) + level;
                    ITEMS.add(new ItemEntry(name, item, Tab.TOOLS, durability, SPEED[level - 1], unbreakable,
                        expectedDamage));
                }
            }
        }
        for (int level = 1; level <= LEVELS; level++) {
            String name = LEVEL_PREFIX[level - 1] + "_stick";
            ITEMS.add(new ItemEntry(name,
                new Item(new Item.Properties()),
                Tab.INGREDIENTS));
        }
    }

    private static Item buildTool(Material m, int level, int toolIndex, String name, boolean unbreakable) {
        ToolKind kind = m.toolKind();
        boolean stone = kind == ToolKind.STONE;
        float[] stats = stone ? STONE_TOOL_STATS[toolIndex] : WOOD_TOOL_STATS[toolIndex];
        // 1.21.1 工具的耐久/速度/伤害加成/修复材料/挖掘等级（7/8/9 重升铁/钻/下界合金）全部来自 Tier
        Tier tier = new CompressedTier(
            durabilityFor(level, kind),
            SPEED[level - 1],
            (stone ? 1.0F : 0.0F) + level,
            incorrectTagFor(level, stone),
            stone ? 5 : 15,
            Ingredient.of(TagKey.create(Registries.ITEM, id("repair_" + m.key())))
        );
        Item.Properties props = new Item.Properties();
        if (unbreakable) {
            props = props.component(DataComponents.UNBREAKABLE, new Unbreakable(true));
        }
        return switch (TOOLS[toolIndex]) {
            case "pickaxe" -> new PickaxeItem(tier, props.attributes(PickaxeItem.createAttributes(tier, stats[0], stats[1])));
            case "sword" -> new SwordItem(tier, props.attributes(SwordItem.createAttributes(tier, (int) stats[0], stats[1])));
            case "axe" -> new AxeItem(tier, props.attributes(AxeItem.createAttributes(tier, stats[0], stats[1])));
            case "shovel" -> new ShovelItem(tier, props.attributes(ShovelItem.createAttributes(tier, stats[0], stats[1])));
            case "hoe" -> new HoeItem(tier, props.attributes(HoeItem.createAttributes(tier, (int) stats[0], stats[1])));
            default -> throw new IllegalStateException("unknown tool: " + TOOLS[toolIndex]);
        };
    }

    /** 图标：九重压缩圆石（与 mod 图标一致）。 */
    public static Item iconItem() {
        for (BlockEntry b : BLOCKS) {
            if (b.name().equals("nonuple_compressed_cobblestone")) {
                return b.item();
            }
        }
        return BLOCKS.get(0).item();
    }

    /** 物品栏内容与顺序：方块（材料×等级）→ 工具（材料×工具类型×等级）→ 压缩木棍。 */
    public static void acceptTabItems(CreativeModeTab.Output output) {
        for (BlockEntry b : BLOCKS) {
            output.accept(b.item());
        }
        for (ItemEntry e : ITEMS) {
            if (e.tab() == Tab.TOOLS) {
                output.accept(e.item());
            }
        }
        for (ItemEntry e : ITEMS) {
            if (e.tab() == Tab.INGREDIENTS) {
                output.accept(e.item());
            }
        }
    }

    /** 独立创造物品栏"压缩"。 */
    public static final CreativeModeTab TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
        .title(Component.translatable("itemGroup.compressedblocks"))
        .icon(() -> new ItemStack(iconItem()))
        .displayItems((parameters, output) -> acceptTabItems(output))
        .build();

    private static TagKey<Item> vanillaItemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(path));
    }

    private static ItemEntry itemEntry(String name) {
        for (ItemEntry e : ITEMS) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        return null;
    }

    private static void assertTier(List<String> errors, String name, Block state, boolean expectHarvest) {
        ItemEntry e = itemEntry(name);
        if (e == null) {
            errors.add("missing item for tier check: " + name);
            return;
        }
        boolean got = new ItemStack(e.item()).isCorrectToolForDrops(state.defaultBlockState());
        if (got != expectHarvest) {
            errors.add(name + " harvest " + state + " = " + got + ", want " + expectHarvest);
        }
    }

    /**
     * 启动自检：注册计数、翻译键、耐久公式、unbreakable、速度阶梯、攻击伤害、挖掘等级阶梯、
     * 附魔标签逐项比对服务端权威数据。任何版本升级/映射变化都会在这里第一时间爆掉（日志搜 SELF-TEST）。
     */
    public static void selfTest() {
        List<String> errors = new ArrayList<>();
        long blockCount = BuiltInRegistries.BLOCK.keySet().stream().filter(i -> i.getNamespace().equals(MOD_ID)).count();
        long itemCount = BuiltInRegistries.ITEM.keySet().stream().filter(i -> i.getNamespace().equals(MOD_ID)).count();
        if (blockCount != BLOCKS.size()) {
            errors.add("block count " + blockCount + " != " + BLOCKS.size());
        }
        if (itemCount != BLOCKS.size() + ITEMS.size()) {
            errors.add("item count " + itemCount + " != " + (BLOCKS.size() + ITEMS.size()));
        }
        for (BlockEntry b : BLOCKS) {
            if (BuiltInRegistries.BLOCK.get(id(b.name())) != b.block()) {
                errors.add("block not registered: " + b.name());
            }
            if (!(BuiltInRegistries.ITEM.get(id(b.name())) instanceof BlockItem bi) || bi.getBlock() != b.block()) {
                errors.add("block item mismatch: " + b.name());
            }
        }
        for (ItemEntry e : ITEMS) {
            if (BuiltInRegistries.ITEM.get(id(e.name())) != e.item()) {
                errors.add("item not registered: " + e.name());
            }
            if (e.expectedSpeed() == 0.0F) {
                if (!e.item().getDescriptionId().equals("item." + MOD_ID + "." + e.name())) {
                    errors.add("stick translation key mismatch: " + e.name());
                }
                continue; // 压缩木棍：无耐久/工具组件
            }
            if (!e.item().getDescriptionId().equals("item." + MOD_ID + "." + e.name())) {
                errors.add("item translation key mismatch: " + e.name());
            }
            ItemStack stack = new ItemStack(e.item());
            if (stack.getMaxDamage() != e.expectedDurability()) {
                errors.add(e.name() + " durability " + stack.getMaxDamage() + " != " + e.expectedDurability());
            }
            boolean unbreakable = stack.get(DataComponents.UNBREAKABLE) != null;
            if (unbreakable != e.expectedUnbreakable()) {
                errors.add(e.name() + " unbreakable " + unbreakable + " != " + e.expectedUnbreakable());
            }
            if (stack.isDamageableItem() == e.expectedUnbreakable()) {
                errors.add(e.name() + " isDamageableItem inconsistent with unbreakable");
            }
            Tool tool = stack.get(DataComponents.TOOL);
            if (tool == null) {
                errors.add(e.name() + " missing TOOL component");
            } else {
                float speed = Float.NaN;
                for (Tool.Rule rule : tool.rules()) {
                    if (rule.speed().isPresent()) {
                        speed = rule.speed().get();
                    }
                }
                // 剑不参与挖掘速度阶梯（原版剑只有对 sword_efficient 方块的 1.5 速度规则）
                float wantSpeed = e.name().endsWith("_sword") ? 1.5F : e.expectedSpeed();
                if (Math.abs(speed - wantSpeed) > 0.001F) {
                    errors.add(e.name() + " speed " + speed + " != " + wantSpeed);
                }
                // 攻击伤害 = 原版参数 + 材料加成 + 重数（一重即 +1）
                ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                double damage = Double.NaN;
                if (mods != null) {
                    for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                        if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                            damage = entry.modifier().amount();
                        }
                    }
                }
                if (Math.abs(damage - e.expectedAttackDamage()) > 0.001) {
                    errors.add(e.name() + " attack damage " + damage + " != " + e.expectedAttackDamage());
                }
            }
        }
        TagKey<Item> mining = vanillaItemTag("enchantable/mining");
        TagKey<Item> durability = vanillaItemTag("enchantable/durability");
        TagKey<Item> sharpWeapon = vanillaItemTag("enchantable/sharp_weapon");
        // 附魔生效前提：必须挂在原版 enchantable/* 物品标签里
        for (ItemEntry e : ITEMS) {
            if (e.expectedSpeed() == 0.0F) {
                continue;
            }
            ItemStack stack = new ItemStack(e.item());
            if (!stack.is(durability)) {
                errors.add(e.name() + " not in #enchantable/durability");
            }
            if (e.name().endsWith("_pickaxe") && !stack.is(mining)) {
                errors.add(e.name() + " not in #enchantable/mining");
            }
            if ((e.name().endsWith("_sword") || e.name().endsWith("_axe")) && !stack.is(sharpWeapon)) {
                errors.add(e.name() + " not in #enchantable/sharp_weapon");
            }
        }
        // 挖掘等级阶梯：6 重仍为石级，7 重=铁，8 重/9 重=钻石级及以上
        assertTier(errors, "compressed_cobblestone_pickaxe", Blocks.DIAMOND_ORE, false);
        assertTier(errors, "sextuple_compressed_cobblestone_pickaxe", Blocks.DIAMOND_ORE, false);
        assertTier(errors, "septuple_compressed_cobblestone_pickaxe", Blocks.DIAMOND_ORE, true);
        assertTier(errors, "septuple_compressed_cobblestone_pickaxe", Blocks.OBSIDIAN, false);
        assertTier(errors, "octuple_compressed_cobblestone_pickaxe", Blocks.OBSIDIAN, true);
        assertTier(errors, "nonuple_compressed_cobblestone_pickaxe", Blocks.OBSIDIAN, true);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("SELF-TEST FAILED: " + String.join("; ", errors));
        }
        LOGGER.info("SELF-TEST PASS: blocks={} items={} ({} tools, {} sticks)",
            BLOCKS.size(), BLOCKS.size() + ITEMS.size(),
            ITEMS.size() - LEVELS, LEVELS);
    }

    private CompressedBlocks() {
    }
}
