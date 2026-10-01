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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 压缩盆栽（Botany Pots 机制）：盆内存土壤与作物，作物不是真实方块、盆上方永远为空，
 * 由方块实体渲染器按生长进度绘制。
 * 交互：空盆+压缩泥土/沙子→填土；填土+压缩作物种子/树苗/甘蔗（重数≤土壤重数）→种植；
 * 成熟后右键收割（自动补种，时运用手持物）；潜行空手=取出作物、再取土壤。
 * 漏斗盆栽：成熟自动收割并优先插入下方容器。
 */
public class CompressedPotBlock extends Block implements EntityBlock {
    /** 12×12 底面、6px 高、1px 薄壁。 */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);
    /** 可种植物品（压缩种子/树苗/甘蔗物品）的运行时描述。 */
    private record Plantable(BlockState state, int level, String enName, String itemId) {
    }

    private final boolean hopper;

    public CompressedPotBlock(boolean hopper, Properties props) {
        super(props);
        this.hopper = hopper;
    }

    public boolean hopper() {
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
        // 双端 ticker（Botany Pots 同款）：客户端本地推进进度让渲染平滑，服务端权威收割
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
        // 斧头右键：取出作物（树苗/甘蔗），掉在盆边
        if (stack.getItem() instanceof AxeItem && pot.plant() != null) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            ItemStack taken = pot.takePlant();
            if (!taken.isEmpty()) {
                Block.popResource(level, pos, taken);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
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
                    CompressedHooks.sendPotHint(sp, pl.enName(), pl.level(), soilLevel);
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

    /**
     * 持有物是否土壤（返回等级，非土壤 = 0）：压缩泥土/沙子带各自重数；
     * 原版/模组泥土系（#minecraft:dirt）与沙子系（#minecraft:sand）算 1 级。
     */
    private static int soilLevelOf(BlockState state) {
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

    /**
     * 持有物是否可种植：
     * 本模组种子/树苗/甘蔗按各自重数；通用兼容——#minecraft:saplings（原版与模组树苗）、
     * #minecraft:crops（原版与模组作物，含南瓜/西瓜茎/火把花/瓶子草）、原版甘蔗，一律 1 级。
     */
    private static Plantable plantableOf(ItemStack stack) {
        if (stack.getItem() instanceof CompressedSoilItem item) {
            return new Plantable(item.getBlock().defaultBlockState(), item.level(),
                item.enName(), itemId(stack));
        }
        if (stack.getItem() instanceof CompressedCaneItem item) {
            return new Plantable(item.getBlock().defaultBlockState(), item.level(),
                item.enName(), itemId(stack));
        }
        if (stack.getItem() instanceof BlockItem bi) {
            BlockState def = bi.getBlock().defaultBlockState();
            if (def.is(BlockTags.SAPLINGS) || def.is(BlockTags.CROPS)
                || bi.getBlock() instanceof SugarCaneBlock) {
                return new Plantable(def, 1, itemId(stack), itemId(stack));
            }
        }
        return null;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
