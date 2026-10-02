package dev.compressedblocks;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 压缩潜影盒（单一等级）：243 格滚动存储，无朝向整体方块。
 * 内容随物品保留——镜像原版潜影盒：创造破坏走 playerWillDestroy 直接掉落带内容物品，
 * 生存/爆炸破坏走 getDrops 绕过战利品表直接构建（DataComponents.CONTAINER，上限 256 格 ≥ 243）。
 */
public class CompressedShulkerBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public CompressedShulkerBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScrollingContainerBlockEntity(CompressedBlocks.SHULKER_TYPE, pos, state, true);
    }

    /** 创造模式破坏：也掉落带内容的物品（原版潜影盒同款）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()
            && level.getBlockEntity(pos) instanceof ScrollingContainerBlockEntity be) {
            Block.popResource(level, pos, boxWithContents(be));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 生存/爆炸破坏：直接从方块实体构建掉落物，内容不落地。 */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
            instanceof ScrollingContainerBlockEntity be) {
            return List.of(boxWithContents(be));
        }
        return super.getDrops(state, builder);
    }

    private static ItemStack boxWithContents(ScrollingContainerBlockEntity be) {
        ItemStack box = new ItemStack(CompressedBlocks.itemByName("compressed_shulker_box"));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(be.allItems()));
        return box;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MenuProvider menu) {
            player.openMenu(menu);
            return InteractionResult.CONSUME;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return this.useWithoutItem(state, level, pos, player, hit);
    }
}
