package dev.compressedblockspot;

import dev.compressedblocks.CobblestoneGeneratorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 压缩盆栽附属（compressedblockspot）：压缩盆栽 + 压缩漏斗盆栽 + 1~9 重压缩刷石机。
 * 盆栽独立实现：通过方块 id 前缀解析压缩等级（"9x_dirt" → 9），不直接调用主 mod 类。
 * 土壤等级每比作物高 1 级提速 5%；漏斗盆栽成熟自动收割入下方容器。
 * 刷石机继承主 mod 内核（{@link dev.compressedblocks.CobblestoneGeneratorBlock}），只换自己的方块实体类型。
 */
public final class CompressedPotsAddon {
    public static final String MOD_ID = "compressedblockspot";
    public static final String MAIN_NS = "compressedblocks";

    public static SpotPotBlock COMPRESSED_POT;
    public static SpotPotBlock HOPPER_POT;
    public static BlockEntityType<SpotPotBlockEntity> POT_TYPE;

    /** 附属 1~9 重压缩刷石机（tier = 产物压缩等级 1..9）与附属方块实体类型（由加载器入口构建注入）。 */
    public static final List<AddonCobblestoneGeneratorBlock> GENERATOR_BLOCKS = new ArrayList<>();
    public static BlockEntityType<CobblestoneGeneratorBlockEntity> GENERATOR_TYPE;

    public static CreativeModeTab TAB_ADDON_BLOCKS;
    public static CreativeModeTab TAB_ADDON_TOOLS;

    /** 大容量容器插入钩子（加载器入口注入：NeoForge=IItemHandler），兼容储物抽屉等。 */
    public interface ItemSink {
        boolean accepts(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                        net.minecraft.core.Direction side);

        long insert(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                    net.minecraft.core.Direction side, net.minecraft.world.item.ItemStack stack);
    }

    public static ItemSink ITEM_SINK;

    public static final List<Block> BLOCKS = new ArrayList<>();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 压缩等级解析："9x_dirt" → 9；无前缀 → 0（原版）。 */
    public static int levelOf(net.minecraft.world.level.block.state.BlockState state) {
        return levelOfId(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static int levelOfId(Identifier blockId) {
        String path = blockId.getPath();
        int x = path.indexOf('x');
        if (x > 0 && x <= 2) {
            String digits = path.substring(0, x);
            if (digits.chars().allMatch(Character::isDigit)) {
                try {
                    return Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    /** 主 mod 物品查找（创造栏图标用）；缺失时返回空栈。 */
    private static ItemStack mainItem(String path) {
        return BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath(MAIN_NS, path))
            .map(holder -> new ItemStack(holder.value()))
            .orElse(ItemStack.EMPTY);
    }

    /** 刷石机注册名（tier 从 1 起）。 */
    public static String generatorName(int tier) {
        return tier + "x_cobblestone_generator";
    }

    /** 构造方块（注册期内由桥接调用；真实注册由桥接完成）。 */
    public static void construct() {
        COMPRESSED_POT = new SpotPotBlock(false, BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id("compressed_pot")))
            .mapColor(MapColor.TERRACOTTA_BROWN)
            .strength(1.5F, 1200.0F)
            .sound(SoundType.STONE)
            .noOcclusion());
        HOPPER_POT = new SpotPotBlock(true, BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id("compressed_hopper_pot")))
            .mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 1200.0F)
            .sound(SoundType.STONE)
            .noOcclusion());
        BLOCKS.add(COMPRESSED_POT);
        BLOCKS.add(HOPPER_POT);
        // 9 台压缩刷石机：tier = n = 产物压缩等级（1x..9x 压缩圆石），复用主 mod 内核
        for (int tier = 1; tier <= 9; tier++) {
            String name = generatorName(tier);
            AddonCobblestoneGeneratorBlock block = new AddonCobblestoneGeneratorBlock(tier,
                BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(name)))
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.5F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .noOcclusion());
            GENERATOR_BLOCKS.add(block);
            BLOCKS.add(block);
        }
    }

    /** 构造物品（BLOCK 事件之后由桥接调用）。 */
    public static BlockItem itemFor(Block block, String name) {
        return new BlockItem(block,
            new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id(name)))
                .useBlockDescriptionPrefix());
    }

    /** 构造 BE 类型（BLOCK_ENTITY_TYPE 事件内由桥接调用）。 */
    public static void constructBlockEntityType() {
        POT_TYPE = new BlockEntityType<>(SpotPotBlockEntity::new,
            java.util.Set.of(COMPRESSED_POT, HOPPER_POT));
        // 附属刷石机 BE：附属方块 + 主 mod 内核（构造器注入附属类型，产物/输出逻辑复用）
        GENERATOR_TYPE = new BlockEntityType<>(
            (pos, state) -> new CobblestoneGeneratorBlockEntity(GENERATOR_TYPE, pos, state),
            java.util.Set.copyOf(GENERATOR_BLOCKS));
    }

    /** 构造创造栏（CREATIVE_MODE_TAB 注册事件内由桥接调用）。 */
    public static void constructTabs() {
        // 原「压缩盆栽」栏拆成拓展方块 / 拓展工具：主 mod 3 栏 + 附属 2 栏 = 5 栏
        TAB_ADDON_BLOCKS = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 3)
            .title(Component.translatable("itemGroup." + MOD_ID + ".addon_blocks"))
            .icon(() -> mainItem("9x_cane"))
            .displayItems((parameters, output) -> {
                // 本栏仅附属安装时存在：主 mod 的压缩甘蔗/树苗也归这里展示
                for (Item item : BuiltInRegistries.ITEM) {
                    Identifier key = BuiltInRegistries.ITEM.getKey(item);
                    if (key.getNamespace().equals(MAIN_NS)
                            && (key.getPath().endsWith("_cane") || key.getPath().endsWith("_sapling"))) {
                        output.accept(item);
                    }
                }
            })
            .build();
        TAB_ADDON_TOOLS = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 4)
            .title(Component.translatable("itemGroup." + MOD_ID + ".addon_tools"))
            .icon(() -> new ItemStack(GENERATOR_BLOCKS.get(GENERATOR_BLOCKS.size() - 1)))
            .displayItems((parameters, output) -> {
                output.accept(COMPRESSED_POT);
                output.accept(HOPPER_POT);
                for (AddonCobblestoneGeneratorBlock block : GENERATOR_BLOCKS) {
                    output.accept(block);
                }
            })
            .build();
    }

    private CompressedPotsAddon() {
    }
}
