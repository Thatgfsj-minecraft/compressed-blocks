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
    /** 可种植物品（原版系种子/树苗/甘蔗）的运行时描述。 */
    private record Plantable(BlockState state, int level, String itemId) {
    }

    public CompressedPotBlock(Properties props) {
        super(props);
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
                if (soilLevel >= pl.level()) {
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
                    CompressedHooks.sendPotHint(sp, pl.level(), soilLevel);
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

    /** 持有物是否土壤（返回等级，非土壤 = 0）：压缩泥土/沙子带各自重数；原版/mod 泥土系/沙子系 = 1。 */
    static int soilLevelOf(BlockState state) {
        Integer dirt = CompressedBlocks.dirtLevel(state);
        if (dirt != null) {
            return dirt;
        }
        Integer sand = CompressedBlocks.sandLevel(state);
        if (sand != null) {
            return sand;
        }
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) ? 1 : 0;
    }

    /** 可种植：只收**原版系**作物/树苗/甘蔗（压缩植物请用附属 mod 的压缩盆栽）。 */
    private static Plantable plantableOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            BlockState def = bi.getBlock().defaultBlockState();
            if (def.is(BlockTags.SAPLINGS) || def.is(BlockTags.CROPS)
                || bi.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock) {
                return new Plantable(def, 0, itemId(stack));
            }
        }
        return null;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
