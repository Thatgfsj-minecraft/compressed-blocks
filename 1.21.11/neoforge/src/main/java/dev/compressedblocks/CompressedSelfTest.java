package dev.compressedblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;

import static dev.compressedblocks.CompressedBlocks.*;

/**
 * 启动自检：由 {@link CompressedBlocks#selfTest(net.minecraft.server.level.ServerLevel)} 原样迁出；
 * 公开入口仍保留在 CompressedBlocks，各加载器调用点不变。
 */
final class CompressedSelfTest {
    private CompressedSelfTest() {
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

    // ------------------------------------------------------------------ 自检

    private static final BlockPos CHECK_POS = new BlockPos(0, 100, 0);

    private static TagKey<Item> vanillaItemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(path));
    }

    private static void assertTierTag(List<String> errors, String name, TagKey<Block> got, TagKey<Block> want) {
        if (!got.equals(want)) {
            errors.add(name + " incorrectBlocksForDrops " + got.location() + " != " + want.location());
        }
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
    static void selfTest(ServerLevel level) {
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
        if (BLOCKS.size() != 1326) {
            errors.add("block registry size " + BLOCKS.size() + " != 1326");
        }
        if (expectItems != 1563) {
            errors.add("item registry size " + expectItems + " != 1563");
        }
        if (FURNACE_BLOCKS.size() != FURNACE_TIERS) {
            errors.add("furnace blocks count " + FURNACE_BLOCKS.size() + " != " + FURNACE_TIERS);
        }

        // 压缩熔炉规格：lane 数 3/6/9/9，单路速度 2x/3x/6x/27x（整机 6x/18x/54x/243x）
        int[] wantLanes = {3, 6, 9, 9};
        int[] wantSpeedMilli = {2000, 3000, 6000, 27000};
        for (int tier = 1; tier <= FURNACE_TIERS; tier++) {
            if (CompressedFurnaceData.lanesForTier(tier) != wantLanes[tier - 1]) {
                errors.add("furnace lanes tier " + tier);
            }
            if (CompressedFurnaceData.cookSpeedMilli(tier) != wantSpeedMilli[tier - 1]) {
                errors.add("furnace speed tier " + tier);
            }
        }
        if (HOPPER_BLOCKS.size() != HOPPER_TIERS) {
            errors.add("hopper blocks count " + HOPPER_BLOCKS.size() + " != " + HOPPER_TIERS);
        }
        if (WOODEN_HOPPER_BLOCKS.size() != WOODEN_HOPPER_TIERS) {
            errors.add("wooden hopper blocks count " + WOODEN_HOPPER_BLOCKS.size() + " != " + WOODEN_HOPPER_TIERS);
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

        // 可燃物：压缩木制物品必须被 FuelValues 收录且燃烧时间符合 CompressedFuels 规则；
        // 盆栽不可燃。Fabric 走 FuelRegistryEvents、NeoForge 走 furnace_fuels 数据图，两边都要过。
        net.minecraft.world.level.block.entity.FuelValues fuels = level.fuelValues();
        int[] fuelSeen = {0};
        CompressedFuels.forEach((item, ticks) -> {
            fuelSeen[0]++;
            int actual = fuels.burnDuration(new ItemStack(item));
            if (actual != ticks) {
                errors.add("fuel " + BuiltInRegistries.ITEM.getKey(item).getPath()
                    + " burn " + actual + " != " + ticks);
            }
        });
        // 条目数对拍：防止规则被改小/命名失配后 forEach 静默少注册（NeoForge data map 漂移也会在这里暴露）
        if (fuelSeen[0] != CompressedFuels.EXPECTED_ITEM_COUNT) {
            errors.add("fuel entries " + fuelSeen[0] + " != " + CompressedFuels.EXPECTED_ITEM_COUNT);
        }
        // 反向断言：FuelValues 里属于本模组的燃料项，必须在规则表里（防止 data map/注册残留过时条目）
        for (Item fuelItem : fuels.fuelItems()) {
            var fuelId = BuiltInRegistries.ITEM.getKey(fuelItem);
            if (fuelId.getNamespace().equals(MOD_ID) && CompressedFuels.burnTimeFor(fuelId.getPath()) <= 0) {
                errors.add("unexpected fuel: " + fuelId.getPath());
            }
        }
        for (String notFuel : new String[] {"pot", "hopper_pot"}) {
            Item notFuelItem = itemByName(notFuel);
            if (notFuelItem != null && fuels.isFuel(new ItemStack(notFuelItem))) {
                errors.add(notFuel + " must not be fuel");
            }
        }
        // 压缩熔炉挖掘门：与原版 minecraft:furnace 一致 —— 任意等级镐（含木镐）即可挖掘掉落，
        // 只保留在 #mineable/pickaxe，绝不能进 needs_stone_tool / needs_iron_tool / needs_diamond_tool
        for (Block furnace : FURNACE_BLOCKS) {
            BlockState furnaceState = furnace.defaultBlockState();
            String furnaceName = BuiltInRegistries.BLOCK.getKey(furnace).getPath();
            if (!furnaceState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                errors.add(furnaceName + " not in #mineable/pickaxe");
            }
            for (TagKey<Block> tierTag : List.of(BlockTags.NEEDS_STONE_TOOL,
                BlockTags.NEEDS_IRON_TOOL, BlockTags.NEEDS_DIAMOND_TOOL)) {
                if (furnaceState.is(tierTag)) {
                    errors.add(furnaceName + " must not be in #" + tierTag.location());
                }
            }
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
        // 石线挖掘等级阶梯：6x 仍为石级，7x=铁，8x/9x=钻石级及以上
        assertTier(errors, "1x_cobblestone_pickaxe", Blocks.DIAMOND_ORE, false);
        assertTier(errors, "6x_cobblestone_pickaxe", Blocks.DIAMOND_ORE, false);
        assertTier(errors, "7x_cobblestone_pickaxe", Blocks.DIAMOND_ORE, true);
        assertTier(errors, "7x_cobblestone_pickaxe", Blocks.OBSIDIAN, false);
        assertTier(errors, "8x_cobblestone_pickaxe", Blocks.OBSIDIAN, true);
        assertTier(errors, "9x_cobblestone_pickaxe", Blocks.OBSIDIAN, true);
        // 木线挖掘等级阶梯：整体比石线低一级 —— 7 重=石级、8 重=铁级、9 重=钻石级
        // 1.21.11 原版标签（实读服务端数据包）：iron_ore ∈ #needs_stone_tool；
        // gold_ore/diamond_ore ∈ #needs_iron_tool；obsidian/ancient_debris ∈ #needs_diamond_tool
        assertTier(errors, "1x_wood_pickaxe", Blocks.IRON_ORE, false);
        assertTier(errors, "6x_wood_pickaxe", Blocks.IRON_ORE, false);
        assertTier(errors, "7x_wood_pickaxe", Blocks.IRON_ORE, true);
        assertTier(errors, "7x_wood_pickaxe", Blocks.GOLD_ORE, false);
        assertTier(errors, "8x_wood_pickaxe", Blocks.GOLD_ORE, true);
        assertTier(errors, "8x_wood_pickaxe", Blocks.DIAMOND_ORE, true);
        assertTier(errors, "8x_wood_pickaxe", Blocks.OBSIDIAN, false);
        assertTier(errors, "9x_wood_pickaxe", Blocks.OBSIDIAN, true);
        // 远古残骸在 1.21.11 属于 #needs_diamond_tool（钻石级即可挖）；原版没有下界合金专属方块，
        // 钻石级与下界合金级功能等价，故两条线九重都能挖远古残骸，差异只在 7/8 重。
        assertTier(errors, "9x_wood_pickaxe", Blocks.ANCIENT_DEBRIS, true);
        assertTier(errors, "9x_cobblestone_pickaxe", Blocks.ANCIENT_DEBRIS, true);
        // 挖掘等级阶梯身份断言：逐级比对 incorrectBlocksForDrops 标签对象（不依赖原版标签内容，防阶梯回退）
        List<TagKey<Block>> stoneWant = List.of(
            BlockTags.INCORRECT_FOR_STONE_TOOL, BlockTags.INCORRECT_FOR_STONE_TOOL,
            BlockTags.INCORRECT_FOR_STONE_TOOL, BlockTags.INCORRECT_FOR_STONE_TOOL,
            BlockTags.INCORRECT_FOR_STONE_TOOL, BlockTags.INCORRECT_FOR_STONE_TOOL,
            BlockTags.INCORRECT_FOR_IRON_TOOL, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL);
        List<TagKey<Block>> woodWant = List.of(
            BlockTags.INCORRECT_FOR_WOODEN_TOOL, BlockTags.INCORRECT_FOR_WOODEN_TOOL,
            BlockTags.INCORRECT_FOR_WOODEN_TOOL, BlockTags.INCORRECT_FOR_WOODEN_TOOL,
            BlockTags.INCORRECT_FOR_WOODEN_TOOL, BlockTags.INCORRECT_FOR_WOODEN_TOOL,
            BlockTags.INCORRECT_FOR_STONE_TOOL, BlockTags.INCORRECT_FOR_IRON_TOOL,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
        for (int lv = 1; lv <= LEVELS; lv++) {
            String prefix = LEVEL_PREFIX[lv - 1];
            assertTierTag(errors, prefix + "_cobblestone_pickaxe", stoneIncorrectTag(lv), stoneWant.get(lv - 1));
            assertTierTag(errors, prefix + "_wood_pickaxe", woodIncorrectTag(lv), woodWant.get(lv - 1));
            // 速度阶梯：木线 = 石线 × 0.95（独立复算，防止两张表抄写漂移）
            if (Math.abs(WOOD_SPEED[lv - 1] - STONE_SPEED[lv - 1] * 0.95F) > 0.001F) {
                errors.add(prefix + " wood speed " + WOOD_SPEED[lv - 1]
                    + " != stone*0.95 " + (STONE_SPEED[lv - 1] * 0.95F));
            }
        }
        // 效果阶梯抽查：7/8/9 重 → 护腿抗性 1/2/3 级，6 重及以下无效果
        if (resistanceAmplifier(7) != 0 || resistanceAmplifier(8) != 1
            || resistanceAmplifier(9) != 2
            || resistanceAmplifier(6) != -1 || resistanceAmplifier(5) != -1) {
            errors.add("armor effect ladder wrong");
        }
        selfTestHopperOverflow(errors, level);
        selfTestCompat(errors);
        if (errors.isEmpty()) {
            LOGGER.info("SELF-TEST PASS: blocks={} items={} ({} tools, {} armor, {} food)",
                BLOCKS.size(), ITEMS.size(), TOOL_REGS.size(), ARMOR_REGS.size(), FOOD_REGS.size());
        } else {
            throw new IllegalStateException("SELF-TEST FAILED: " + String.join("; ", errors));
        }
    }

    /**
     * 老存档 9 格缩容迁移自检：手工构造 0.3.4 开发窗口（11-14 21:20 ～ 11-15 12:57）写出的 9 格 Items，
     * 走 BlockEntity.loadStatic 真实加载路径（不写世界、不 tick）：
     * 加载期 level 必须为 null（旧实现根因），溢出先进入 pending；假 sink 调 flushOverflow 后
     * 可视槽保留 1/3 格、余量 8/6 堆依次交给 sink，二次 flush 不再重复掉落。
     */
    private static void selfTestHopperOverflow(List<String> errors, ServerLevel level) {
        int before = errors.size();
        Item[] visibleItems = {Items.STONE, Items.DIRT, Items.COBBLESTONE};
        Item[] overflowItems = {Items.OAK_PLANKS, Items.IRON_INGOT, Items.GOLD_INGOT,
            Items.DIAMOND, Items.EMERALD, Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL};
        int[] wantPending = {8, 6, 6, 6};
        for (int tier = 0; tier < WOODEN_HOPPER_TIERS; tier++) {
            int size = tier == 0 ? WoodenHopperBlockEntity.BASE_CONTAINER_SIZE
                : WoodenHopperBlockEntity.COMPRESSED_CONTAINER_SIZE;
            BlockState state = WOODEN_HOPPER_BLOCKS.get(tier).defaultBlockState();
            TagValueOutput out = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, level.registryAccess());
            BlockEntity.addEntityType(out, WOODEN_HOPPER_TYPE[tier]);
            ValueOutput.TypedOutputList<ItemStackWithSlot> items =
                out.list(ContainerHelper.TAG_ITEMS, ItemStackWithSlot.CODEC);
            for (int slot = 0; slot < 9; slot++) {
                boolean visible = slot < size;
                Item item = visible ? visibleItems[slot] : overflowItems[slot - size];
                items.add(new ItemStackWithSlot(slot, new ItemStack(item, visible ? 64 : 1)));
            }
            CompoundTag tag = out.buildResult();
            BlockEntity loaded = BlockEntity.loadStatic(CHECK_POS, state, tag, level.registryAccess());
            if (!(loaded instanceof WoodenHopperBlockEntity hopper)) {
                errors.add("hopper overflow tier " + tier + ": loadStatic returned " + loaded);
                continue;
            }
            if (hopper.getLevel() != null) {
                errors.add("hopper overflow tier " + tier + ": loadStatic set level (根因未复现)");
            }
            if (hopper.pendingOverflowCount() != wantPending[tier]) {
                errors.add("hopper overflow tier " + tier + " pending "
                    + hopper.pendingOverflowCount() + " != " + wantPending[tier]);
            }
            List<ItemStack> dropped = new ArrayList<>();
            hopper.flushOverflow(dropped::add);
            for (int slot = 0; slot < size; slot++) {
                ItemStack kept = hopper.getItem(slot);
                if (kept.getItem() != visibleItems[slot] || kept.getCount() != 64) {
                    errors.add("hopper overflow tier " + tier + " slot " + slot + " kept " + kept);
                }
            }
            if (dropped.size() != wantPending[tier]) {
                errors.add("hopper overflow tier " + tier + " dropped " + dropped.size()
                    + " != " + wantPending[tier]);
            } else {
                for (int i = 0; i < dropped.size(); i++) {
                    ItemStack stack = dropped.get(i);
                    if (stack.getItem() != overflowItems[i] || stack.getCount() != 1) {
                        errors.add("hopper overflow tier " + tier + " drop " + i + " = " + stack);
                    }
                }
            }
            List<ItemStack> again = new ArrayList<>();
            hopper.flushOverflow(again::add);
            if (!again.isEmpty()) {
                errors.add("hopper overflow tier " + tier + " second flush dropped " + again.size());
            }
            if (hopper.pendingOverflowCount() != 0) {
                errors.add("hopper overflow tier " + tier + " pending not cleared");
            }
        }

        // slot >= 9 的异常/未来扩容数据也不能静默丢：按同一规则进 pending，再由 flush 掉落
        TagValueOutput extraOut = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, level.registryAccess());
        BlockEntity.addEntityType(extraOut, WOODEN_HOPPER_TYPE[0]);
        ValueOutput.TypedOutputList<ItemStackWithSlot> extraItems =
            extraOut.list(ContainerHelper.TAG_ITEMS, ItemStackWithSlot.CODEC);
        extraItems.add(new ItemStackWithSlot(0, new ItemStack(Items.STONE, 1)));
        extraItems.add(new ItemStackWithSlot(9, new ItemStack(Items.DIAMOND, 1)));
        BlockEntity extraLoaded = BlockEntity.loadStatic(CHECK_POS,
            WOODEN_HOPPER_BLOCKS.get(0).defaultBlockState(), extraOut.buildResult(), level.registryAccess());
        if (!(extraLoaded instanceof WoodenHopperBlockEntity extra)) {
            errors.add("hopper overflow slot>=9: loadStatic returned " + extraLoaded);
        } else {
            if (extra.pendingOverflowCount() != 1) {
                errors.add("hopper overflow slot>=9 pending " + extra.pendingOverflowCount() + " != 1");
            }
            List<ItemStack> extraDropped = new ArrayList<>();
            extra.flushOverflow(extraDropped::add);
            if (extraDropped.size() != 1 || extraDropped.get(0).getItem() != Items.DIAMOND) {
                errors.add("hopper overflow slot>=9 dropped " + extraDropped);
            }
        }
        if (errors.size() == before) {
            LOGGER.info("SELF-TEST hopper legacy overflow PASS: pending=8/6, flush idempotent, slot>=9 kept");
        }
    }

    /** 压缩金属包抽查：22 材质 × 9 级全部注册且发光档正确。 */
    private static void selfTestCompat(List<String> errors) {
        long n = BuiltInRegistries.BLOCK.keySet().stream()
            .filter(i -> i.getNamespace().equals(MOD_ID))
            .filter(i -> i.getPath().endsWith("_block"))
            .filter(i -> {
                String p = i.getPath();
                for (String key : new String[] {"tin", "lead", "zinc", "plastic", "silver", "nickel",
                    "bronze", "brass", "electrum", "invar", "constantan", "steel", "manasteel",
                    "uranium", "osmium", "signalum", "enderium", "refined_obsidian",
                    "refined_glowstone", "lumium", "terrasteel", "elementium"}) {
                    if (p.endsWith("_" + key + "_block")) {
                        return true;
                    }
                }
                return false;
            }).count();
        if (n != COMPAT_METALS.size() * LEVELS) {
            errors.add("compat metal blocks " + n + " != " + COMPAT_METALS.size() * LEVELS);
        }
        Block lumium = blockByName("1x_lumium_block");
        if (lumium == null || lumium.defaultBlockState().getLightEmission() != 12) {
            errors.add("lumium light != 12");
        }
        Block glow = blockByName("1x_refined_glowstone_block");
        if (glow == null || glow.defaultBlockState().getLightEmission() != 15) {
            errors.add("refined glowstone light != 15");
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
}
