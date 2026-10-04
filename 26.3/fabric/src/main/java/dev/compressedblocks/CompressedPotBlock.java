package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 盆栽（主 mod，原版配色）：只能种植**原版系**的作物/甘蔗/树苗（#minecraft:crops、
 * #minecraft:saplings、原版甘蔗），土壤可用原版泥土系/沙子系或压缩泥土/沙子——
 * 压缩土壤等级每高 1 级提速 5%。盆内存土壤与作物（Botany Pots 机制），
 * 成熟后右键收割（自动补种，时运用手持物）；潜行空手取出作物/土壤。
 * （压缩植物与自动化漏斗盆栽在附属 mod compressedblockspot 中。）
 */
public class CompressedPotBlock extends Block implements EntityBlock {
    /** 分层盆：底板 12×12×1 + 实心腰 10×10×3 + 空心口沿 12×12×2（内孔 10×10），与模型 elements 一致。 */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(2, 0, 2, 14, 1, 14),
        Block.box(3, 1, 3, 13, 4, 13),
        Block.box(2, 4, 2, 14, 6, 3),
        Block.box(2, 4, 13, 14, 6, 14),
        Block.box(2, 4, 3, 3, 6, 13),
        Block.box(13, 4, 3, 14, 6, 13));
    /** 可种植物品（原版系种子/树苗/甘蔗等）的运行时描述。soilMask：可填土壤族（位掩码）。 */
    record Plantable(BlockState state, int level, String itemId, int soilMask) {
    }

    /** 土壤族（位掩码）：泥土系 / 沙子系 / 下界系（下界岩、灵魂沙）。 */
    static final int SOIL_DIRT = 1;
    static final int SOIL_SAND = 2;
    static final int SOIL_NETHER = 4;

    /** 漏斗盆栽标记。 */
    private final boolean hopper;

    public CompressedPotBlock(Properties props) {
        this(props, false);
    }

    /** hopper=true：漏斗盆栽，成熟自动收割入下方容器（原版配色，压缩植物仍在附属 mod）。 */
    public CompressedPotBlock(Properties props, boolean hopper) {
        super(props);
        this.hopper = hopper;
    }

    /** 是否漏斗盆栽（BE 据此自动收割）。 */
    public boolean isHopper() {
        return this.hopper;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompressedPotBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != CompressedBlocks.POT_TYPE) {
            return null;
        }
        // 双端 ticker（Botany Pots 同款）：客户端本地推进进度让渲染平滑，服务端权威计时
        return level.isClientSide()
            ? (BlockEntityTicker<T>) (BlockEntityTicker<CompressedPotBlockEntity>)
                CompressedPotBlockEntity::clientTick
            : (BlockEntityTicker<T>) (BlockEntityTicker<CompressedPotBlockEntity>)
                CompressedPotBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CompressedPotBlockEntity pot)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        // 26.3：AxeItem 类删除——斧头判定改走 minecraft:axes 物品标签
        if (stack.is(net.minecraft.tags.ItemTags.AXES) && pot.plant() != null) {
            // 斧头右键：拔出作物（掉落种子/植物本体），土壤保留
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            ItemStack seed = pot.takePlant();
            if (!seed.isEmpty()) {
                Block.popResource(level, pos, seed);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.8F);
            }
            return InteractionResult.CONSUME;
        }
        if (pot.grown()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            pot.harvest(player);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
        if (pot.soil() == null) {
            if (stack.getItem() instanceof BlockItem bi && soilLevelOf(bi.getBlock().defaultBlockState()) > 0) {
                if (level.isClientSide()) {
                    return InteractionResult.SUCCESS;
                }
                pot.setSoil(bi.getBlock().defaultBlockState());
                stack.shrink(1);
                level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (pot.plant() == null) {
            Plantable pl = plantableOf(stack);
            if (pl != null) {
                int soilLevel = soilLevelOf(pot.soil());
                int soilMask = soilMaskOf(pot.soil());
                if ((soilMask & pl.soilMask()) != 0 && soilLevel >= pl.level()) {
                    if (level.isClientSide()) {
                        return InteractionResult.SUCCESS;
                    }
                    if (pot.setPlant(pl.state(), pl.itemId())) {
                        stack.shrink(1);
                        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    return InteractionResult.CONSUME;
                }
                if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                    if ((soilMask & pl.soilMask()) == 0) {
                        CompressedHooks.sendPotSoilHint(sp, pl.soilMask(), pot.soil());
                    } else {
                        CompressedHooks.sendPotHint(sp, pl.level(), soilLevel);
                    }
                }
                return InteractionResult.CONSUME;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CompressedPotBlockEntity pot)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (pot.grown()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            pot.harvest(player);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
        if (!player.isShiftKeyDown()) {
            // 空手右键：动作栏（上方）显示盆内内容与生长进度
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                CompressedHooks.sendPotContents(sp, pot.soil(), pot.plantItemStack(), pot.growthFraction());
            }
            return InteractionResult.CONSUME;
        }
        ItemStack taken = pot.plant() != null ? pot.takePlant() : pot.takeSoil();
        if (!taken.isEmpty()) {
            Block.popResource(level, pos, taken);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.CONSUME;
    }

    /** 持有物是否土壤（返回等级，非土壤 = 0）：压缩泥土/沙子/下界岩带各自重数；
     *  原版泥土系/沙子系/下界岩/灵魂沙 = 1。 */
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
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
            || state.is(Blocks.NETHERRACK) || state.is(Blocks.SOUL_SAND)) {
            return 1;
        }
        return 0;
    }

    /** 土壤族掩码（0 = 非土壤）：泥土系/沙子系/下界系。 */
    static int soilMaskOf(BlockState state) {
        if (CompressedBlocks.dirtLevel(state) != null || state.is(BlockTags.DIRT)) {
            return SOIL_DIRT;
        }
        if (CompressedBlocks.sandLevel(state) != null || state.is(BlockTags.SAND)) {
            return SOIL_SAND;
        }
        if (CompressedBlocks.netherSoilLevel(state) != null
            || state.is(Blocks.NETHERRACK) || state.is(Blocks.SOUL_SAND)) {
            return SOIL_NETHER;
        }
        return 0;
    }

    /**
     * 可种植：只收**原版系**（压缩植物请用附属 mod 的压缩盆栽）。土壤族按原版习性绑定：
     * 作物/树苗/竹子/甜果丛/可可 = 泥土系，甘蔗 = 泥土或沙子，仙人掌 = 沙子系，地狱疣 = 下界系。
     */
    static Plantable plantableOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            // 压缩作物/树苗也挂在原版 #crops/#saplings 标签里（供附属盆栽识别），这里按命名空间挡掉
            if (!"minecraft".equals(BuiltInRegistries.BLOCK.getKey(bi.getBlock()).getNamespace())) {
                return null;
            }
            Block block = bi.getBlock();
            BlockState def = block.defaultBlockState();
            if (def.is(BlockTags.SAPLINGS) || def.is(BlockTags.CROPS)) {
                return new Plantable(def, 0, itemId(stack), SOIL_DIRT);
            }
            if (block instanceof net.minecraft.world.level.block.SugarCaneBlock) {
                return new Plantable(def, 0, itemId(stack), SOIL_DIRT | SOIL_SAND);
            }
            if (block instanceof net.minecraft.world.level.block.BambooStalkBlock
                || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                || block instanceof net.minecraft.world.level.block.CocoaBlock) {
                return new Plantable(def, 0, itemId(stack), SOIL_DIRT);
            }
            if (block instanceof net.minecraft.world.level.block.CactusBlock) {
                return new Plantable(def, 0, itemId(stack), SOIL_SAND);
            }
            if (block instanceof net.minecraft.world.level.block.NetherWartBlock) {
                return new Plantable(def, 0, itemId(stack), SOIL_NETHER);
            }
        }
        return null;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
