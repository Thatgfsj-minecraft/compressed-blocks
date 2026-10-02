package dev.compressedblockspot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.SoundType;

/**
 * 压缩盆栽附属（compressedblockspot）：压缩盆栽 + 压缩漏斗盆栽。
 * 独立实现：通过方块 id 前缀解析压缩等级（"9x_dirt" → 9），不直接调用主 mod 类；
 * 运行时与主 mod 通过 id/标签交互（pot 材质标签、压缩泥土/植物 id）。
 * 土壤等级每比作物高 1 级提速 5%；漏斗盆栽成熟自动收割入下方容器。
 */
public final class CompressedPotsAddon {
    public static final String MOD_ID = "compressedblockspot";
    public static final String MAIN_NS = "compressedblocks";

    public static SpotPotBlock COMPRESSED_POT;
    public static SpotPotBlock HOPPER_POT;
    public static BlockEntityType<SpotPotBlockEntity> POT_TYPE;

    /** 大容量容器插入钩子（加载器入口注入：Fabric=Transfer API），兼容储物抽屉等。 */
    public interface ItemSink {
        boolean accepts(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                        net.minecraft.core.Direction side);

        long insert(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                    net.minecraft.core.Direction side, net.minecraft.world.item.ItemStack stack);
    }

    public static ItemSink ITEM_SINK;

    public static final List<Block> BLOCKS = new ArrayList<>();

    private static final String[] POT_IDS = {"compressed_pot", "compressed_hopper_pot"};

    public static net.minecraft.resources.Identifier id(String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 压缩等级解析："9x_dirt" → 9；无前缀 → 0（原版）。 */
    public static int levelOf(net.minecraft.world.level.block.state.BlockState state) {
        return levelOfId(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static int levelOfId(net.minecraft.resources.Identifier blockId) {
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

    private static void register() {
        COMPRESSED_POT = new SpotPotBlock(false, BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id("compressed_pot")))
            .mapColor(MapColor.TERRACOTTA_BROWN)
            .strength(1.5F, 1200.0F)
            .sound(SoundType.STONE)
            .noOcclusion());
        HOPPER_POT = new SpotPotBlock(true, BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id("compressed_hopper_pot")))
            .mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 1200.0F)
            .sound(SoundType.STONE)
            .noOcclusion());
        // 类型在客户端/服务端各自的入口注入（构造器私有）
        for (String name : POT_IDS) {
            Block block = name.equals("compressed_pot") ? COMPRESSED_POT : HOPPER_POT;
            BlockItem item = new BlockItem(block,
                new Item.Properties()
                    .setId(ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id(name)))
                    .useBlockDescriptionPrefix());
            Registry.register(BuiltInRegistries.BLOCK, id(name), block);
            Registry.register(BuiltInRegistries.ITEM, id(name), item);
            BLOCKS.add(block);
        }
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 4)
            .title(net.minecraft.network.chat.Component.translatable("itemGroup." + MOD_ID + ".pots"))
            .icon(() -> new net.minecraft.world.item.ItemStack(COMPRESSED_POT))
            .displayItems((parameters, output) -> {
                output.accept(COMPRESSED_POT);
                output.accept(HOPPER_POT);
                // 本栏仅附属安装时存在：主 mod 的压缩甘蔗/树苗也归这里展示
                for (Item item : BuiltInRegistries.ITEM) {
                    net.minecraft.resources.Identifier key = BuiltInRegistries.ITEM.getKey(item);
                    if (key.getNamespace().equals(MAIN_NS)
                            && (key.getPath().endsWith("_cane") || key.getPath().endsWith("_sapling"))) {
                        output.accept(item);
                    }
                }
            })
            .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("pots"), tab);
    }

    public static void init() {
        register();
        // POT_TYPE 由 fabric 入口经 FabricBlockEntityTypeBuilder 构建注入
    }

    private CompressedPotsAddon() {
    }
}
