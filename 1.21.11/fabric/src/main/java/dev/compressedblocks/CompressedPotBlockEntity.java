package dev.compressedblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * 压缩盆栽方块实体：土壤与作物都存盆内（Botany Pots 机制——作物不是真实方块，
 * 盆上方永远为空）。growth 服务端计时、客户端本地同步推进（双端 ticker，进度平滑、
 * 无每刻发包）；数据变化（填土/种植/收割/取出）经 getUpdatePacket 全量同步。
 * 成熟后玩家右键收割并自动补种；漏斗盆栽成熟自动收割，产物优先插入下方容器
 * （原版箱子/木桶/漏斗都实现 Container），塞不下就地掉落。
 * 收获规则（用户定稿）：作物=原版战利品表（时运用收割者手持物）；树苗=同重数原木 4-6 根+树苗；
 * 甘蔗=2-4×自身。
 */
public class CompressedPotBlockEntity extends BlockEntity {
    /** 基础生长时长：30 秒（最慢档——土壤等级 = 作物等级时）。 */
    public static final int GROWTH_TICKS = 600;
    /** 树苗盆栽原木数量基准（原版橡树 4-6 根）。 */
    private static final int SAPLING_LOGS = 4;
    /** 甘蔗盆栽收获份数下限（原版一株成熟甘蔗约 2 节）。 */
    private static final int CANE_MIN = 2;

    private BlockState soil;
    private BlockState plant;
    private String plantItem;
    private int growth;

    public CompressedPotBlockEntity(BlockPos pos, BlockState state) {
        super(CompressedBlocks.POT_TYPE, pos, state);
    }

    public BlockState soil() {
        return this.soil;
    }

    public BlockState plant() {
        return this.plant;
    }

    /** 盆内作物对应的物品（取出/展示用），未种植 = EMPTY。 */
    public ItemStack plantItemStack() {
        return itemStack(this.plantItem);
    }

    /**
     * 本茬所需生长 tick：土壤等级每比作物高 1 级提速 5%（原版/mod 植物记 0 级），
     * 例如 9 级地种 1 级作物提速 40%、种原版树提速 45%；基础 = 最慢档 600 tick。
     */
    public int requiredGrowth() {
        if (this.plant == null || this.soil == null) {
            return GROWTH_TICKS;
        }
        int soilLevel = CompressedPotBlock.soilLevelOf(this.soil);
        int plantLevel = plantLevelOf(this.plant);
        float boost = 1.0F + Math.max(0, soilLevel - plantLevel) * 0.05F;
        return Math.max(1, Math.round(GROWTH_TICKS / boost));
    }

    /** 作物的等级：压缩植物各自的重数；原版/模组植物无等级记 0。 */
    private static int plantLevelOf(BlockState state) {
        if (state.getBlock() instanceof CompressedSaplingBlock s) {
            return s.level();
        }
        if (state.getBlock() instanceof CompressedCropBlock c) {
            return c.level();
        }
        if (state.getBlock() instanceof CompressedCaneBlock c) {
            return c.level();
        }
        return 0;
    }

    /** 生长进度 0..1（未种植 = 0）。 */
    public float growthFraction() {
        return this.plant == null ? 0.0F : Math.min(1.0F, this.growth / (float) requiredGrowth());
    }

    public boolean grown() {
        return this.plant != null && this.growth >= requiredGrowth();
    }

    public boolean isHopperPot() {
        return getBlockState().getBlock() instanceof CompressedPotBlock pot && pot.hopper();
    }

    public void setSoil(BlockState state) {
        this.soil = state;
        sync();
    }

    /** 种植（等级门由方块侧校验）；已有作物或没填土时失败。 */
    public boolean setPlant(BlockState state, String itemName) {
        if (this.soil == null || this.plant != null) {
            return false;
        }
        this.plant = state;
        this.plantItem = itemName;
        this.growth = 0;
        sync();
        return true;
    }

    /** 取出作物（潜行空手右键）。 */
    public ItemStack takePlant() {
        if (this.plant == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = itemStack(this.plantItem);
        this.plant = null;
        this.plantItem = null;
        this.growth = 0;
        sync();
        return stack;
    }

    /** 取出土壤（作物已取出后潜行空手右键）。 */
    public ItemStack takeSoil() {
        if (this.soil == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(this.soil.getBlock());
        this.soil = null;
        sync();
        return stack;
    }

    /** 收割并自动补种（作物保留在盆内、进度清零）。 */
    public void harvest(Player player) {
        if (!(getLevel() instanceof ServerLevel server) || this.plant == null) {
            return;
        }
        for (ItemStack drop : drops(server, player)) {
            if (isHopperPot() && insertIntoBelow(drop)) {
                continue;
            }
            Block.popResource(server, getBlockPos(), drop);
        }
        this.growth = 0;
        sync();
    }

    /** 盆被破坏：补发盆内土壤与作物（盆本体走战利品表）。 */
    public void dropContents(ServerLevel server) {
        if (this.soil != null) {
            Block.popResource(server, getBlockPos(), new ItemStack(this.soil.getBlock()));
        }
        ItemStack plant = takePlant();
        if (!plant.isEmpty()) {
            Block.popResource(server, getBlockPos(), plant);
        }
    }

    /** 盆被替换/破坏（1.21.11 移除路径，原版容器同款钩子）：掉落盆内土壤与作物。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (getLevel() instanceof ServerLevel server) {
            dropContents(server);
        }
    }

    /** 服务端生长计时：漏斗盆栽仅在下方有容器时自动收割入箱，否则原地等待玩家右键（与普通盆栽一致）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CompressedPotBlockEntity be) {
        if (be.plant == null) {
            return;
        }
        int required = be.requiredGrowth();
        if (be.growth < required) {
            be.growth++;
            be.setChanged();
        }
        if (be.growth >= required && be.isHopperPot() && be.hasContainerBelow()) {
            be.harvest(null);
        }
    }

    /** 下方方块是否是容器（箱子/木桶/漏斗等）。 */
    private boolean hasContainerBelow() {
        return getLevel() != null && getLevel().getBlockEntity(getBlockPos().below()) instanceof Container;
    }

    /** 客户端本地推进进度（Botany Pots 同款）：渲染平滑长大，不发包。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, CompressedPotBlockEntity be) {
        if (be.plant != null && be.growth < be.requiredGrowth()) {
            be.growth++;
        }
    }

    /**
     * 收获掉落：作物（压缩+原版+模组，#minecraft:crops）走方块自身命名空间的战利品表（含时运）；
     * 压缩树苗=同重数原木×4-6+树苗；通用树苗（#minecraft:saplings）找同命名空间 "{wood}_log"
     * ×4-6，找不到给木棍；其余（甘蔗等）=2-4×自身；最后一律补发一份作物本体（自动补种不消耗）。
     */
    private List<ItemStack> drops(ServerLevel server, Player player) {
        List<ItemStack> out = new ArrayList<>();
        Block block = this.plant.getBlock();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        String name = blockId.getPath();
        if (block instanceof CompressedCropBlock || this.plant.is(BlockTags.CROPS)) {
            // 原版作物战利品表带 age=7 条件：收割走满龄状态
            BlockState lootState = this.plant;
            if (block instanceof CompressedCropBlock crop) {
                lootState = crop.getStateForAge(crop.getMaxAge());
            }
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                blockId.withPrefix("blocks/"));
            LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(getBlockPos()))
                .withParameter(LootContextParams.BLOCK_STATE, lootState)
                .withParameter(LootContextParams.TOOL,
                    player == null ? ItemStack.EMPTY : player.getMainHandItem())
                .create(LootContextParamSets.BLOCK);
            server.getServer().reloadableRegistries().getLootTable(key)
                .getRandomItems(params, out::add);
            return out;
        }
        if (block instanceof CompressedSaplingBlock) {
            String logName = name.substring(0, name.length() - "_sapling".length()) + "_log";
            var log = BuiltInRegistries.BLOCK.get(CompressedBlocks.id(logName));
            log.ifPresent(h -> out.add(new ItemStack(h.value().asItem(),
                SAPLING_LOGS + server.random.nextInt(3))));
        } else if (this.plant.is(BlockTags.SAPLINGS) && name.endsWith("_sapling")) {
            String wood = name.substring(0, name.length() - "_sapling".length());
            var log = BuiltInRegistries.BLOCK.get(
                Identifier.fromNamespaceAndPath(blockId.getNamespace(), wood + "_log"));
            int count = SAPLING_LOGS + server.random.nextInt(3);
            out.add(log.filter(h -> h.value().asItem() != net.minecraft.world.item.Items.AIR)
                .map(h -> new ItemStack(h.value().asItem(), count))
                .orElse(new ItemStack(net.minecraft.world.item.Items.STICK, count)));
        } else {
            ItemStack cane = itemStack(this.plantItem);
            cane.setCount(CANE_MIN + server.random.nextInt(3));
            out.add(cane);
        }
        ItemStack plant = itemStack(this.plantItem);
        if (!plant.isEmpty()) {
            out.add(plant);
        }
        return out;
    }

    /**
     * 漏斗盆栽输出：手动把产物合并进下方容器（箱子/木桶/漏斗都实现 Container）：
     * 先叠加同类槽位，再放空槽；放不下返回 false（改就地掉落）。
     */
    private boolean insertIntoBelow(ItemStack stack) {
        if (getLevel().getBlockEntity(getBlockPos().below()) instanceof Container container) {
            int size = container.getContainerSize();
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(stack, slot)) {
                    continue;
                }
                int room = Math.min(slot.getMaxStackSize(), container.getMaxStackSize()) - slot.getCount();
                if (room <= 0) {
                    continue;
                }
                int move = Math.min(room, stack.getCount());
                slot.grow(move);
                stack.shrink(move);
            }
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                if (container.getItem(i).isEmpty() && container.canPlaceItem(i, stack)) {
                    container.setItem(i, stack.split(stack.getCount()));
                }
            }
            if (size > 0) {
                container.setChanged();
            }
            return stack.isEmpty();
        }
        return false;
    }

    private void sync() {
        setChanged();
        if (getLevel() instanceof ServerLevel server) {
            server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    private static ItemStack itemStack(String id) {
        Identifier rl = id == null ? null : Identifier.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.get(rl).map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static BlockState blockState(String id) {
        Identifier rl = id == null || id.isEmpty() ? null : Identifier.tryParse(id);
        if (rl == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.get(rl).map(h -> h.value().defaultBlockState()).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (this.soil != null) {
            out.putString("Soil", blockId(this.soil));
        }
        if (this.plant != null) {
            out.putString("Plant", blockId(this.plant));
            if (this.plantItem != null) {
                out.putString("Seed", this.plantItem);
            }
            out.putInt("Growth", this.growth);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.soil = blockState(in.getStringOr("Soil", ""));
        this.plant = blockState(in.getStringOr("Plant", ""));
        String seed = in.getStringOr("Seed", "");
        this.plantItem = seed.isEmpty() ? null : seed;
        this.growth = Math.max(0, in.getIntOr("Growth", 0));
        if (this.plant == null) {
            this.plantItem = null;
            this.growth = 0;
        }
    }

    /** 全量同步盆内数据（Botany Pots 同款：覆写 getUpdatePacket，默认 null 永不同步）。 */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        this.saveAdditional(out);
        return out.buildResult();
    }
}
