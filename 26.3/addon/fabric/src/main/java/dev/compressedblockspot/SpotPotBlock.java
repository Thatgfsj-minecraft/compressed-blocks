package dev.compressedblockspot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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
 * 压缩盆栽（附属）：可种压缩系与原版系植物；土壤等级每比作物高 1 级提速 5%；
 * hopper 变体成熟自动收割，产物优先压入下方容器。
 * 独立实现：压缩等级从方块 id 前缀解析（"9x_dirt" → 9），不调用主 mod 类。
 */
public class SpotPotBlock extends Block implements EntityBlock {
    /** 分层盆（与主 mod 同款）：底板 12×12×1 + 实心腰 10×10×3 + 空心口沿 12×12×2（内孔 10×10）。 */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(2, 0, 2, 14, 1, 14),
        Block.box(3, 1, 3, 13, 4, 13),
        Block.box(2, 4, 2, 14, 6, 3),
        Block.box(2, 4, 13, 14, 6, 14),
        Block.box(2, 4, 3, 3, 6, 13),
        Block.box(13, 4, 3, 14, 6, 13));

    private final boolean hopper;

    public SpotPotBlock(boolean hopper, Properties props) {
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
        return new SpotPotBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != CompressedPotsAddon.POT_TYPE) {
            return null;
        }
        return level.isClientSide()
            ? (BlockEntityTicker<T>) (BlockEntityTicker<SpotPotBlockEntity>) SpotPotBlockEntity::clientTick
            : (BlockEntityTicker<T>) (BlockEntityTicker<SpotPotBlockEntity>) SpotPotBlockEntity::serverTick;
    }

    /** 土壤等级：压缩方块解析 id 前缀；原版泥土系/沙子系 = 1；其余 0。 */
    static int soilLevelOf(BlockState state) {
        int level = CompressedPotsAddon.levelOf(state);
        if (level > 0) {
            return level;
        }
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) ? 1 : 0;
    }

    /** 植物等级：压缩植物解析前缀；原版/普通 mod 植物 = 0。 */
    static int plantLevel(BlockState state) {
        return CompressedPotsAddon.levelOf(state);
    }

    /** 持有物是否可种：任何 BlockItem 的方块（作物/树苗/甘蔗/压缩植物皆由放置方块定义）。 */
    static BlockState plantStateOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            BlockState def = bi.getBlock().defaultBlockState();
            // 可种判定：作物/树苗标签、原版甘蔗、或任意压缩方块（id 带等级前缀）
            if (def.is(BlockTags.CROPS) || def.is(BlockTags.SAPLINGS)
                || bi.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock
                || CompressedPotsAddon.levelOf(def) > 0) {
                return def;
            }
        }
        return null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpotPotBlockEntity pot)) {
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
            }
            return InteractionResult.CONSUME;
        }
        if (pot.grown()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            pot.harvest(player);
            return InteractionResult.CONSUME;
        }
        if (pot.soil() == null) {
            if (stack.getItem() instanceof BlockItem bi && soilLevelOf(bi.getBlock().defaultBlockState()) > 0) {
                if (level.isClientSide()) {
                    return InteractionResult.SUCCESS;
                }
                pot.setSoil(bi.getBlock().defaultBlockState());
                stack.shrink(1);
                return InteractionResult.CONSUME;
            }
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (pot.plant() == null) {
            BlockState plant = plantStateOf(stack);
            if (plant != null) {
                int soilLevel = soilLevelOf(pot.soil());
                int plantLevel = plantLevel(plant);
                if (soilLevel >= plantLevel) {
                    if (level.isClientSide()) {
                        return InteractionResult.SUCCESS;
                    }
                    if (pot.setPlant(plant, plantItemId(stack))) {
                        stack.shrink(1);
                    }
                    return InteractionResult.CONSUME;
                }
                // 等级不足：拒绝并提示（中文）
                if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.empty()
                        .append(net.minecraft.network.chat.Component.literal("[压缩盆栽] ")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                        .append(net.minecraft.network.chat.Component.literal("盆内需要 ")
                            .withStyle(net.minecraft.ChatFormatting.RED))
                        .append(net.minecraft.network.chat.Component.literal(plantLevel + " 重及以上的压缩土壤")
                            .withStyle(net.minecraft.ChatFormatting.GOLD))
                        .append(net.minecraft.network.chat.Component.literal("! 盆内土壤：")
                            .withStyle(net.minecraft.ChatFormatting.RED))
                        .append(net.minecraft.network.chat.Component.literal(soilLevel + " 重")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                    // 26.3：displayClientMessage(msg, overlay) 拆成 sendSystemMessage / sendOverlayMessage
                    sp.sendSystemMessage(msg);
                }
                return InteractionResult.CONSUME;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpotPotBlockEntity pot)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (pot.grown()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            pot.harvest(player);
            return InteractionResult.CONSUME;
        }
        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.empty()
                    .append(net.minecraft.network.chat.Component.literal("[压缩盆栽] ")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                if (pot.soil() == null && pot.plant() == null) {
                    msg = msg.copy().append(net.minecraft.network.chat.Component.literal("空盆")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                } else {
                    msg = msg.copy()
                        .append(pot.soil() == null
                            ? net.minecraft.network.chat.Component.literal("未填土")
                                .withStyle(net.minecraft.ChatFormatting.RED)
                            : pot.soil().getBlock().getName().copy()
                                .withStyle(net.minecraft.ChatFormatting.GOLD));
                    if (pot.plant() != null) {
                        msg = msg.copy()
                            .append(net.minecraft.network.chat.Component.literal(" ("
                                + Math.round(pot.growthFraction() * 100.0F) + "%)")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW));
                    }
                }
                sp.sendOverlayMessage(msg);
            }
            return InteractionResult.CONSUME;
        }
        ItemStack taken = pot.plant() != null ? pot.takePlant() : pot.takeSoil();
        if (!taken.isEmpty()) {
            Block.popResource(level, pos, taken);
        }
        return InteractionResult.CONSUME;
    }

    /** 盆被替换/破坏：掉落土壤与作物（BE 自带 preRemoveSideEffects 路径，onRemove 在 1.21.11 已不存在）。 */

    private static String plantItemId(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
