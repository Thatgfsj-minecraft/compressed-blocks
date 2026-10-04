package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.List;

/**
 * 压缩潜影盒（单一等级）：243 格滚动存储，无朝向整体方块。
 * 内容随物品保留——镜像原版潜影盒：破坏（生存/爆炸/创造）时构建带内容的物品
 * （BlockEntityTag.Items，放置时原版 BlockItem 自动写回方块实体），内容不落地。
 */
public class CompressedShulkerBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public CompressedShulkerBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(net.minecraft.world.level.BlockGetter reader) {
        return new ScrollingContainerBlockEntity(CompressedBlocksForge.SHULKER_TYPE, true);
    }

    /** 创造模式破坏：也掉落带内容的物品（原版潜影盒同款）。 */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()
            && level.getBlockEntity(pos) instanceof ScrollingContainerBlockEntity) {
            Block.popResource(level, pos, boxWithContents((ScrollingContainerBlockEntity) level.getBlockEntity(pos)));
            // 清空方块实体：防 getDrops/onRemove 再掉一份
            ((ScrollingContainerBlockEntity) level.getBlockEntity(pos)).clearContentsForCreativeDrop();
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** 生存/爆炸破坏：直接构建带内容物品（不走战利品表掉自身）。 */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Player
            && ((Player) builder.getOptionalParameter(LootContextParams.THIS_ENTITY)).isCreative()) {
            return Collections.emptyList();
        }
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ScrollingContainerBlockEntity) {
            return Collections.singletonList(
                boxWithContents((ScrollingContainerBlockEntity) builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)));
        }
        return Collections.emptyList();
    }

    private static ItemStack boxWithContents(ScrollingContainerBlockEntity be) {
        ItemStack box = new ItemStack(CompressedBlocks.itemByName("compressed_shulker_box"));
        CompoundTag tag = new CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(tag, be.allItems(), false);
        box.addTagElement("BlockEntityTag", tag);
        return box;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.MenuProvider) {
            if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer) {
                net.minecraftforge.fml.network.NetworkHooks.openGui(
                    (net.minecraft.server.level.ServerPlayer) player,
                    (net.minecraft.world.MenuProvider) level.getBlockEntity(pos), pos);
            }
            return InteractionResult.CONSUME;
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
