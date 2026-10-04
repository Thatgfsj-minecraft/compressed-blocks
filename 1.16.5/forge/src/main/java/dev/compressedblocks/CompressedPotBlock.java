package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 盆栽（主 mod，原版配色）：只能种植**原版系**的作物/甘蔗/树苗等（按方块类型识别），
 * 土壤可用原版泥土系/沙子系或压缩泥土/沙子——压缩土壤等级每高 1 级提速 5%。
 * 盆内存土壤与作物（Botany Pots 机制），成熟后右键收割（自动补种）；
 * 潜行空手取出作物/土壤；斧头右键拔出作物。
 * 漏斗盆栽（hopper=true）：成熟自动收割压入下方容器并补种。
 * 1.16.5 差异：无 BER 渲染（盆内植物不可见，功能不受影响；见 backlog）。
 */
public class CompressedPotBlock extends Block implements EntityBlock {
    /** 分层盆：底板 12×12×1 + 实心腰 10×10×3 + 空心口沿 12×12×2，与模型 elements 一致。 */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(2, 0, 2, 14, 1, 14),
        Block.box(3, 1, 3, 13, 4, 13),
        Block.box(2, 4, 2, 14, 6, 3),
        Block.box(2, 4, 13, 14, 6, 14),
        Block.box(2, 4, 3, 3, 6, 13),
        Block.box(13, 4, 3, 14, 6, 13));

    /** 土壤族（位掩码）：泥土系 / 沙子系 / 下界系（下界岩、灵魂沙）。 */
    static final int SOIL_DIRT = 1;
    static final int SOIL_SAND = 2;
    static final int SOIL_NETHER = 4;

    /** 漏斗盆栽标记。 */
    private final boolean hopper;

    /** 附属 mod compressedblockspot 在场时解锁压缩系植物种植（同 jar 双 mod，构造期置位）。 */
    public static boolean SPOT_UNLOCKED = false;

    public CompressedPotBlock(Properties props) {
        this(props, false);
    }

    /** hopper=true：漏斗盆栽，成熟自动收割入下方容器并补种。 */
    public CompressedPotBlock(Properties props, boolean hopper) {
        super(props);
        this.hopper = hopper;
    }

    /** 是否漏斗盆栽（BE 据此自动收割）。 */
    public boolean isHopper() {
        return this.hopper;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(net.minecraft.world.level.BlockGetter reader) {
        return new CompressedPotBlockEntity(this.hopper);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CompressedPotBlockEntity)) {
            return super.use(state, level, pos, player, hand, hit);
        }
        CompressedPotBlockEntity pot = (CompressedPotBlockEntity) be;
        ItemStack stack = player.getItemInHand(hand);
        // 1. 斧头右键：拔出作物（掉落种子/植物本体），土壤保留
        if (stack.getItem() instanceof AxeItem && pot.plant() != null) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            ItemStack seed = pot.takePlant();
            if (!seed.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, seed);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.8F);
            }
            return InteractionResult.CONSUME;
        }
        // 2. 成熟：收割（自动补种）
        if (pot.grown()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            List<ItemStack> drops = pot.harvest();
            for (ItemStack drop : drops) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, drop);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
        // 3. 空盆：持土壤方块 → 填土
        if (pot.soil() == null) {
            if (stack.getItem() instanceof BlockItem
                && soilLevelOf(((BlockItem) stack.getItem()).getBlock().defaultBlockState()) > 0) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                pot.setSoil(((BlockItem) stack.getItem()).getBlock().defaultBlockState());
                stack.shrink(1);
                level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
            return super.use(state, level, pos, player, hand, hit);
        }
        // 4. 有土无植物：持原版系种子/树苗/甘蔗 → 种植
        if (pot.plant() == null) {
            Plantable pl = plantableOf(stack);
            if (pl != null) {
                int soilLevel = soilLevelOf(pot.soil());
                int soilMask = soilMaskOf(pot.soil());
                if ((soilMask & pl.soilMask) != 0 && soilLevel >= pl.level) {
                    if (level.isClientSide) {
                        return InteractionResult.SUCCESS;
                    }
                    if (pot.setPlant(pl.state, pl.itemId)) {
                        stack.shrink(1);
                        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    return InteractionResult.CONSUME;
                }
                if (!level.isClientSide && player instanceof ServerPlayer) {
                    CompressedHooks.sendPotHint((ServerPlayer) player, pl.level, soilLevel);
                }
                return InteractionResult.CONSUME;
            }
            return super.use(state, level, pos, player, hand, hit);
        }
        // 5. 潜行空手：取出作物/土壤；普通空手：动作栏显示进度
        if (stack.isEmpty() && player.isShiftKeyDown()) {
            ItemStack taken = pot.plant() != null ? pot.takePlant() : pot.takeSoil();
            if (!taken.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, taken);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.CONSUME;
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    /** 持有物是否土壤（返回等级，非土壤 = 0）：压缩泥土/沙子/下界岩带各自重数；原版对应系 = 1。 */
    static int soilLevelOf(BlockState state) {
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
        if (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL
            || b == Blocks.COARSE_DIRT || b == Blocks.MYCELIUM
            || b == Blocks.SAND || b == Blocks.RED_SAND || b == Blocks.GRAVEL
            || b == Blocks.NETHERRACK || b == Blocks.SOUL_SAND) {
            return 1;
        }
        return 0;
    }

    /** 土壤族掩码（0 = 非土壤）：泥土系/沙子系/下界系。 */
    static int soilMaskOf(BlockState state) {
        Block b = state.getBlock();
        if (CompressedBlocks.dirtLevel(state) != null
            || b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL
            || b == Blocks.COARSE_DIRT || b == Blocks.MYCELIUM) {
            return SOIL_DIRT;
        }
        if (CompressedBlocks.sandLevel(state) != null
            || b == Blocks.SAND || b == Blocks.RED_SAND || b == Blocks.GRAVEL) {
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
        final BlockState state;
        final int level;
        final String itemId;
        final int soilMask;

        Plantable(BlockState state, int level, String itemId, int soilMask) {
            this.state = state;
            this.level = level;
            this.itemId = itemId;
            this.soilMask = soilMask;
        }
    }

    /**
     * 可种植识别（只收**原版系**，压缩植物走附属 mod 的压缩盆栽）：土壤族按原版习性绑定：
     * 作物/树苗/竹子/甜果丛/可可 = 泥土系，甘蔗 = 泥土或沙子，仙人掌 = 沙子系，地狱疣 = 下界系。
     */
    static Plantable plantableOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) {
            Block block = ((BlockItem) stack.getItem()).getBlock();
            ResourceKeyCheck check = namespaceOf(block);
            if (!check.vanilla && !(CompressedPotBlock.SPOT_UNLOCKED && "compressedblocks".equals(
                net.minecraft.core.Registry.BLOCK.getKey(block).getNamespace()))) {
                return null;
            }
            BlockState def = block.defaultBlockState();
            if (block instanceof net.minecraft.world.level.block.CropBlock
                || block instanceof net.minecraft.world.level.block.SaplingBlock
                || def.is(BlockTags.SAPLINGS) || def.is(BlockTags.CROPS)) {
                return new Plantable(def, 0, check.id, SOIL_DIRT);
            }
            if (block instanceof net.minecraft.world.level.block.SugarCaneBlock) {
                return new Plantable(def, 0, check.id, SOIL_DIRT | SOIL_SAND);
            }
            if (block instanceof net.minecraft.world.level.block.BambooBlock
                || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                || block instanceof net.minecraft.world.level.block.CocoaBlock) {
                return new Plantable(def, 0, check.id, SOIL_DIRT);
            }
            if (block instanceof net.minecraft.world.level.block.CactusBlock) {
                return new Plantable(def, 0, check.id, SOIL_SAND);
            }
            if (block instanceof net.minecraft.world.level.block.NetherWartBlock) {
                return new Plantable(def, 0, check.id, SOIL_NETHER);
            }
            if (block instanceof net.minecraft.world.level.block.StemBlock) {
                return new Plantable(def, 0, check.id, SOIL_DIRT);
            }
            if (block instanceof CompressedCaneBlock) {
                return new Plantable(def, ((CompressedCaneBlock) block).level(), check.id, SOIL_DIRT | SOIL_SAND);
            }
            if (block instanceof CompressedCropBlock) {
                return new Plantable(def, ((CompressedCropBlock) block).level(), check.id, SOIL_DIRT);
            }
            if (block instanceof CompressedSaplingBlock) {
                return new Plantable(def, ((CompressedSaplingBlock) block).level(), check.id, SOIL_DIRT);
            }
        }
        return null;
    }

    /** 命名空间检查（1.16.5：Registry.BLOCK.getKey）。 */
    private static ResourceKeyCheck namespaceOf(Block block) {
        net.minecraft.resources.ResourceLocation key = net.minecraft.core.Registry.BLOCK.getKey(block);
        return new ResourceKeyCheck("minecraft".equals(key.getNamespace()), key.toString());
    }

    private static final class ResourceKeyCheck {
        final boolean vanilla;
        final String id;

        ResourceKeyCheck(boolean vanilla, String id) {
            this.vanilla = vanilla;
            this.id = id;
        }
    }
}
