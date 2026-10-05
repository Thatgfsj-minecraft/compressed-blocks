package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * 盆栽（主 mod，原版配色）：只能种植**原版系**的作物/甘蔗/树苗等（按方块类型识别），
 * 土壤可用原版泥土系/沙子系或压缩泥土/沙子——压缩土壤等级每高 1 级提速 5%。
 * 盆内存土壤与作物（Botany Pots 机制），成熟后右键收割（自动补种）；
 * 潜行空手取出作物/土壤；斧头右键拔出作物。
 * 漏斗盆栽（hopper=true）：成熟自动收割压入下方容器并补种。
 * 1.12.2 差异：无 BER 渲染（盆内植物不可见，功能不受影响，降级记录）。
 */
public class CompressedPotBlock extends Block implements ITileEntityProvider {
    /** 分层盆：底板 12×12×1 + 实心腰 10×10×3 + 空心口沿 12×12×2，与模型 elements 一致。 */
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(2 / 16.0, 0.0, 2 / 16.0, 14 / 16.0, 6 / 16.0, 14 / 16.0);

    /** 土壤族（位掩码）：泥土系 / 沙子系 / 下界系（下界岩、灵魂沙）。 */
    static final int SOIL_DIRT = 1;
    static final int SOIL_SAND = 2;
    static final int SOIL_NETHER = 4;

    /** 漏斗盆栽标记。 */
    private final boolean hopper;

    /** 附属 mod compressedblockspot 在场时解锁压缩系植物种植（同 jar 双 mod，构造期置位）。 */
    public static boolean SPOT_UNLOCKED = false;

    public CompressedPotBlock(boolean hopper) {
        super(Material.ROCK);
        this.hopper = hopper;
    }

    /** 是否漏斗盆栽（BE 据此自动收割）。 */
    public boolean isHopper() {
        return this.hopper;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new CompressedPotBlockEntity(this.hopper);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof CompressedPotBlockEntity)) {
            return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
        }
        CompressedPotBlockEntity pot = (CompressedPotBlockEntity) te;
        ItemStack stack = player.getHeldItem(hand);
        // 1. 斧头右键：拔出作物（掉落种子/植物本体），土壤保留
        if (!stack.isEmpty() && stack.getItem() instanceof ItemAxe && pot.plant() != null) {
            if (!world.isRemote) {
                ItemStack seed = pot.takePlant();
                if (!seed.isEmpty()) {
                    net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(
                        world, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, seed);
                    drop.setDefaultPickupDelay();
                    world.spawnEntity(drop);
                    world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_ITEM_PICKUP,
                        SoundCategory.BLOCKS, 1.0F, 0.8F);
                }
            }
            return true;
        }
        // 2. 成熟：收割（自动补种）
        if (pot.grown()) {
            if (!world.isRemote) {
                List<ItemStack> drops = pot.harvest();
                for (ItemStack drop : drops) {
                    net.minecraft.entity.item.EntityItem dropEntity = new net.minecraft.entity.item.EntityItem(
                        world, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, drop);
                    dropEntity.setDefaultPickupDelay();
                    world.spawnEntity(dropEntity);
                }
                world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            return true;
        }
        // 3. 空盆：持土壤方块 → 填土
        if (pot.soil() == null) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemBlock
                && soilLevelOf(((ItemBlock) stack.getItem()).getBlock().getDefaultState()) > 0) {
                if (!world.isRemote) {
                    pot.setSoil(((ItemBlock) stack.getItem()).getBlock().getDefaultState());
                    stack.shrink(1);
                    world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_GRAVEL_PLACE,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
                return true;
            }
            return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
        }
        // 4. 有土无植物：持原版系种子/树苗/甘蔗 → 种植
        if (pot.plant() == null) {
            Plantable pl = plantableOf(stack);
            if (pl != null) {
                int soilLevel = soilLevelOf(pot.soil());
                int soilMask = soilMaskOf(pot.soil());
                if ((soilMask & pl.soilMask) != 0 && soilLevel >= pl.level) {
                    if (!world.isRemote) {
                        if (pot.setPlant(pl.state, pl.itemId)) {
                            stack.shrink(1);
                            world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_GRASS_PLACE,
                                SoundCategory.BLOCKS, 1.0F, 1.0F);
                        }
                    }
                    return true;
                }
                if (!world.isRemote) {
                    CompressedHooks.sendPotHint(player, pl.level, soilLevel);
                }
                return true;
            }
            return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
        }
        // 5. 潜行空手：取出作物/土壤
        if (stack.isEmpty() && player.isSneaking()) {
            ItemStack taken = pot.plant() != null ? pot.takePlant() : pot.takeSoil();
            if (!taken.isEmpty() && !world.isRemote) {
                net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(
                    world, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, taken);
                drop.setDefaultPickupDelay();
                world.spawnEntity(drop);
                world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            return true;
        }
        return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
    }

    /** 持有物是否土壤（返回等级，非土壤 = 0）：压缩泥土/沙子/下界岩带各自重数；原版对应系 = 1。 */
    static int soilLevelOf(IBlockState state) {
        Integer dirt = CompressedBlocks.dirtLevel(state);
        if (dirt != null) {
            return dirt;
        }
        Integer sand = CompressedBlocks.sandLevel(state);
        if (sand != null) {
            return sand;
        }
        Integer nether = CompressedBlocks.netherSoilLevel(state);
        if (nether != null) {
            return nether;
        }
        Block b = state.getBlock();
        // 1.12.2 的灰化土/粗泥土是 DIRT 的 metadata 变体（无独立方块常量），只判主键
        if (b == Blocks.DIRT || b == Blocks.GRASS || b == Blocks.MYCELIUM
            || b == Blocks.SAND || b == Blocks.GRAVEL
            || b == Blocks.NETHERRACK || b == Blocks.SOUL_SAND) {
            return 1;
        }
        return 0;
    }

    /** 土壤族掩码（0 = 非土壤）：泥土系/沙子系/下界系。 */
    static int soilMaskOf(IBlockState state) {
        Block b = state.getBlock();
        if (CompressedBlocks.dirtLevel(state) != null
            || b == Blocks.DIRT || b == Blocks.GRASS || b == Blocks.MYCELIUM) {
            return SOIL_DIRT;
        }
        if (CompressedBlocks.sandLevel(state) != null
            || b == Blocks.SAND || b == Blocks.GRAVEL) {
            return SOIL_SAND;
        }
        if (CompressedBlocks.netherSoilLevel(state) != null
            || b == Blocks.NETHERRACK || b == Blocks.SOUL_SAND) {
            return SOIL_NETHER;
        }
        return 0;
    }

    /** 可种植描述（原版系）。 */
    static final class Plantable {
        final IBlockState state;
        final int level;
        final String itemId;
        final int soilMask;

        Plantable(IBlockState state, int level, String itemId, int soilMask) {
            this.state = state;
            this.level = level;
            this.itemId = itemId;
            this.soilMask = soilMask;
        }
    }

    /**
     * 可种植识别（只收**原版系**，压缩植物走附属 mod 的压缩盆栽）：土壤族按原版习性绑定：
     * 作物/树苗/可可 = 泥土系，甘蔗 = 泥土或沙子，仙人掌 = 沙子系，地狱疣 = 下界系。
     * 1.12.2 裁剪：无竹子/甜果丛（1.14+ 内容）。
     */
    static Plantable plantableOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
            return null;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        // 1.12.2 ResourceLocation 无 domain/path 访问器（stable_39）：用 toString 解析
        String keyStr = String.valueOf(Block.REGISTRY.getNameForObject(block));
        String domain = keyStr.contains(":") ? keyStr.substring(0, keyStr.indexOf(':')) : "minecraft";
        boolean vanilla = "minecraft".equals(domain);
        if (!vanilla && !(CompressedPotBlock.SPOT_UNLOCKED
            && CompressedBlocks.MOD_ID.equals(domain))) {
            return null;
        }
        IBlockState def = block.getDefaultState();
        if (block instanceof net.minecraft.block.BlockCrops
            || block instanceof net.minecraft.block.BlockSapling
            || block instanceof net.minecraft.block.BlockStem) {
            return new Plantable(def, 0, keyStr, SOIL_DIRT);
        }
        if (block instanceof CompressedCaneBlock) {
            return new Plantable(def, ((CompressedCaneBlock) block).level(), keyStr, SOIL_DIRT | SOIL_SAND);
        }
        if (block instanceof net.minecraft.block.BlockReed) {
            return new Plantable(def, 0, keyStr, SOIL_DIRT | SOIL_SAND);
        }
        if (block instanceof CompressedCropBlock) {
            return new Plantable(def, ((CompressedCropBlock) block).level(), keyStr, SOIL_DIRT);
        }
        if (block instanceof CompressedSaplingBlock) {
            return new Plantable(def, ((CompressedSaplingBlock) block).level(), keyStr, SOIL_DIRT);
        }
        if (block == net.minecraft.init.Blocks.CACTUS) {
            return new Plantable(def, 0, keyStr, SOIL_SAND);
        }
        if (block == net.minecraft.init.Blocks.NETHER_WART) {
            return new Plantable(def, 0, keyStr, SOIL_NETHER);
        }
        if (block == net.minecraft.init.Blocks.COCOA) {
            return new Plantable(def, 0, keyStr, SOIL_DIRT);
        }
        return null;
    }
}
