package dev.compressedblocks;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.Random;

/**
 * 压缩树叶（6 木 × 9 重）：原版凋落算法（随机刻距离衰减，1.12.2 走 Forge 的
 * beginLeavesDecay 钩子），掉落简化版 = 剪刀/丝触掉自身 + 5% 树苗 + 2% 木棍。
 * 1.12.2 差异：无 render_type 模型字段——渲染层由 canRenderInLayer 声明；无 distance 属性。
 */
public class CompressedLeavesBlock extends BlockLeaves {
    public CompressedLeavesBlock() {
        setDefaultState(this.blockState.getBaseState()
            .withProperty(DECAYABLE, Boolean.TRUE).withProperty(CHECK_DECAY, Boolean.FALSE));
    }

    /** 客户端透明渲染层（1.12.2 Forge 扩展 canRenderInLayer，替代 1.16 的模型 render_type 字段）。 */
    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    // ---- 1.12.2 BlockLeaves 要求的两种变体类型（供原版树苗逻辑/统计用，压缩树返回占位）

    @Override
    public BlockPlanks.EnumType getWoodType(int meta) {
        return BlockPlanks.EnumType.OAK;
    }

    @Override
    public List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune) {
        List<ItemStack> out = new java.util.ArrayList<ItemStack>();
        out.add(new ItemStack(this));
        return out;
    }

    // ---- 掉落：简化版树叶 loot（1.16.5 同款策略）

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        Random rand = world instanceof net.minecraft.world.World
            ? ((net.minecraft.world.World) world).rand : new Random();
        EntityPlayer harvester = harvesters.get();
        boolean silk = harvester != null && EnchantmentHelperHelper.hasSilkTouch(harvester);
        boolean shears = harvester != null && !harvester.getHeldItemMainhand().isEmpty()
            && harvester.getHeldItemMainhand().getItem() == Items.SHEARS;
        String key = String.valueOf(this.getRegistryName());
        String prefix = key.substring(key.indexOf(':') + 1, key.indexOf('_'));
        String wood = key.substring(key.indexOf('_') + 1, key.length() - "_leaves".length());
        if (silk || shears) {
            drops.add(new ItemStack(this));
            return;
        }
        if (rand.nextFloat() < 0.05F) {
            net.minecraft.block.Block sapling = CompressedBlocks.blockByName(prefix + "_" + wood + "_sapling");
            if (sapling != null) {
                drops.add(new ItemStack(sapling));
            }
        }
        if (rand.nextFloat() < 0.02F) {
            drops.add(new ItemStack(Items.STICK));
        }
    }

    /** EnchantmentHelper 引用隔离（避免部分开发环境类加载顺序问题）。 */
    private static final class EnchantmentHelperHelper {
        static boolean hasSilkTouch(EntityPlayer player) {
            ItemStack tool = player.getHeldItemMainhand();
            return !tool.isEmpty()
                && net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
                Enchantments.SILK_TOUCH, tool) > 0;
        }
    }

    // ---- 1.12.2 元数据映射（decayable/check_decay 两布尔位）

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {CHECK_DECAY, DECAYABLE});
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
            .withProperty(DECAYABLE, (meta & 4) == 0)
            .withProperty(CHECK_DECAY, (meta & 8) > 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = 0;
        if (!state.getValue(DECAYABLE)) {
            meta |= 4;
        }
        if (state.getValue(CHECK_DECAY)) {
            meta |= 8;
        }
        return meta;
    }
}
