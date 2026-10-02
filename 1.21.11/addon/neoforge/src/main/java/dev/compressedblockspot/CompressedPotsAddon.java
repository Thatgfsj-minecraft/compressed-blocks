package dev.compressedblockspot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 压缩盆栽附属（compressedblockspot）：压缩盆栽 + 压缩漏斗盆栽。
 * 独立实现：通过方块 id 前缀解析压缩等级（"9x_dirt" → 9），不直接调用主 mod 类。
 * 土壤等级每比作物高 1 级提速 5%；漏斗盆栽成熟自动收割入下方容器。
 */
public final class CompressedPotsAddon {
    public static final String MOD_ID = "compressedblockspot";

    public static SpotPotBlock COMPRESSED_POT;
    public static SpotPotBlock HOPPER_POT;
    public static BlockEntityType<SpotPotBlockEntity> POT_TYPE;
    public static CreativeModeTab TAB;

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
    }

    private CompressedPotsAddon() {
    }
}
