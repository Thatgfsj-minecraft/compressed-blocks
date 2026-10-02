package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 刷石机（3 个压缩等级）：每秒产出 1 个对应等级的原石/压缩原石，压入下方容器；
 * 下方无容器时暂存在机器内（一组上限），满了就停滞。
 */
public class CobblestoneGeneratorBlock extends Block implements EntityBlock {
    private final int tier;

    public CobblestoneGeneratorBlock(int tier, Properties props) {
        super(props);
        this.tier = tier;
    }

    /** 压缩等级 1-3：产物分别为原石 / 一重压缩原石 / 二重压缩原石。 */
    public int tier() {
        return this.tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CobblestoneGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != CompressedBlocks.GENERATOR_TYPE) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<CobblestoneGeneratorBlockEntity>)
            CobblestoneGeneratorBlockEntity::serverTick;
    }
}
