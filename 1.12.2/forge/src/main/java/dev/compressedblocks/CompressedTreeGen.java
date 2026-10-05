package dev.compressedblocks;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 压缩树生成器（1.12.2 WorldGenerator，代码树——1.16.5 的 configured feature 在本版不存在）。
 * 树形 = 原版橡树近似：直干（按树种基础高度 + 随机）+ 球形树冠（5×5 两层 + 3×3 顶 + 1 顶心），
 * 干/叶用同重数的压缩原木/压缩树叶。树叶不带 persistent 标记（原版距离衰减自动处理）。
 */
public final class CompressedTreeGen extends WorldGenerator {
    /** 树种基础干高（1.16.5 WOODS 的 trunkBase）。 */
    private static final Map<String, Integer> TRUNK_BASE = new HashMap<>();

    static {
        TRUNK_BASE.put("oak", 4);
        TRUNK_BASE.put("spruce", 6);
        TRUNK_BASE.put("birch", 5);
        TRUNK_BASE.put("jungle", 7);
        TRUNK_BASE.put("acacia", 5);
        TRUNK_BASE.put("dark_oak", 5);
    }

    private final String wood;
    private final int level;

    private CompressedTreeGen(String wood, int level) {
        this.wood = wood;
        this.level = level;
    }

    public static CompressedTreeGen of(String wood, int level) {
        if (!TRUNK_BASE.containsKey(wood)) {
            return null;
        }
        return new CompressedTreeGen(wood, level);
    }

    @Override
    public boolean generate(World world, Random rand, BlockPos pos) {
        Block log = CompressedBlocks.blockByName(levelPrefix() + "_" + wood + "_log");
        Block leaves = CompressedBlocks.blockByName(levelPrefix() + "_" + wood + "_leaves");
        if (log == null || leaves == null) {
            return false;
        }
        // 干底需落在可种土壤上
        IBlockState soil = world.getBlockState(pos.down());
        if (!soil.isTopSolid()) {
            return false;
        }
        int trunk = TRUNK_BASE.get(wood) + rand.nextInt(3);
        if (pos.getY() < 1 || pos.getY() + trunk + 2 > world.getHeight()) {
            return false;
        }
        // 树冠：顶层 3×3（去角随机）+ 中层 5×5（去角随机）+ 顶心
        for (int dy = trunk - 2; dy <= trunk; dy++) {
            int radius = dy == trunk ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (rand.nextFloat() < 0.5F || dy == trunk)) {
                        continue;
                    }
                    placeLeaf(world, pos.add(dx, dy, dz), leaves);
                }
            }
        }
        placeLeaf(world, pos.up(trunk + 1), leaves);
        // 干
        for (int dy = 0; dy < trunk; dy++) {
            setOrKeep(world, pos.up(dy), log.getDefaultState());
        }
        return true;
    }

    private String levelPrefix() {
        String[] prefixes = {"1x", "2x", "3x", "4x", "5x", "6x", "7x", "8x", "9x"};
        return prefixes[this.level - 1];
    }

    /** 树冠叶：只放进空气里，不覆盖已有方块。 */
    private void placeLeaf(World world, BlockPos pos, Block leaves) {
        if (world.isAirBlock(pos)) {
            world.setBlockState(pos, leaves.getDefaultState(), 2);
        }
    }

    /** 干：直接覆盖（含替换树苗自身位置）。 */
    private void setOrKeep(World world, BlockPos pos, net.minecraft.block.state.IBlockState state) {
        world.setBlockState(pos, state, 2);
    }
}
