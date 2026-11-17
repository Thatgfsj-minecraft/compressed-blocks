package dev.compressedblockspot;

import dev.compressedblocks.CobblestoneGeneratorBlock;
import dev.compressedblocks.CobblestoneGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 附属 mod 的 1~9 重压缩刷石机：继承主 mod 刷石机（复用产物/输出逻辑，tier = 压缩等级 1..9），
 * 只把方块实体类型换成附属自己的 {@link CompressedPotsAddon#GENERATOR_TYPE}。
 *
 * <p>与主 mod 三档刷石机的区别仅在方块实体类型：主 mod 的 getTicker 只认
 * {@code CompressedBlocks.GENERATOR_TYPE}，因此这里覆盖 newBlockEntity/getTicker 接入附属类型。
 */
public class AddonCobblestoneGeneratorBlock extends CobblestoneGeneratorBlock {

    public AddonCobblestoneGeneratorBlock(int tier, Properties props) {
        super(tier, props);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CobblestoneGeneratorBlockEntity(CompressedPotsAddon.GENERATOR_TYPE, pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != CompressedPotsAddon.GENERATOR_TYPE) {
            return null;
        }
        return (BlockEntityTicker<T>) generatorTicker(CompressedPotsAddon.GENERATOR_TYPE, level);
    }
}
