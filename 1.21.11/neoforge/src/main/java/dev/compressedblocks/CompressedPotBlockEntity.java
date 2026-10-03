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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * （原版箱子/木桶/漏斗都实现 Container），无容器/塞不下静默保持成熟等待；
 * 收获规则（用户逐项审定 2026-10-03）：作物类走定稿产量表（≈原版 -10%~-30%）+压缩土壤翻倍；
 * 树苗类=对应原木 3-8 + 树苗 0-3（木头类不翻倍）。
 */
public class CompressedPotBlockEntity extends BlockEntity {
    /** 基础生长时长：30 秒（最慢档——土壤等级 = 作物等级时）。 */
    public static final int GROWTH_TICKS = 600;
    /** 树苗盆栽原木数量（用户定稿 3-8，木头类不参与压缩土壤翻倍）。 */
    private static final int SAPLING_LOGS_MIN = 3;
    private static final int SAPLING_LOGS_SPAN = 6;

    private BlockState soil;
    private BlockState plant;
    private String plantItem;
    private int growth;
    /** 漏斗盆栽收割重试冷却（失败后 20 tick 再试）。 */
    private int harvestCooldown;

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

    /** 收割并自动补种（作物保留在盆内、进度清零）；产物掉在盆边，时运用收割者手持物。 */
    public void harvest(Player player) {
        if (!(getLevel() instanceof ServerLevel server) || this.plant == null) {
            return;
        }
        for (ItemStack drop : drops(server, player)) {
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

    /** 服务端生长计时：普通盆栽成熟等玩家右键收割；漏斗盆栽自动收割入下方容器，
     *  无容器时静默等待（1 秒重试一次），玩家仍可随时右键收割（掉地上）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CompressedPotBlockEntity be) {
        if (be.plant == null) {
            return;
        }
        int required = be.requiredGrowth();
        if (be.growth < required) {
            be.growth++;
            be.setChanged();
            return;
        }
        if (be.isHopper()) {
            if (be.harvestCooldown > 0) {
                be.harvestCooldown--;
            } else if (level instanceof ServerLevel server) {
                if (be.tryAutoHarvest(server)) {
                    be.growth = 0;
                    be.sync();
                }
                // 成功防抖；失败 1 秒重试（无容器时避免每 tick 重掷产量表）
                be.harvestCooldown = 20;
            }
        }
    }

    private boolean isHopper() {
        return this.getBlockState().getBlock() instanceof CompressedPotBlock pot && pot.isHopper();
    }

    /** 漏斗盆栽自动收割：下方容器塞得下才收割补种；无容器/塞不下时保持成熟并静默等待
     *  （绝不掉落产物——否则成熟状态每 tick 重跑产量表会无限弹物品）。 */
    private boolean tryAutoHarvest(ServerLevel server) {
        BlockPos below = getBlockPos().below();
        BlockEntity target = server.getBlockEntity(below);
        if (target instanceof Container container) {
            List<ItemStack> drops = drops(server, null);
            if (!fitsInto(container, drops)) {
                return false;
            }
            return insertInto(server, below, drops);
        }
        if (CompressedBlocks.ITEM_SINK != null) {
            // 钩子目标（储物抽屉等非 Container 容器）：容量近乎无限，实插、余量静默丢弃；
            // 一个都没插进去（下方无目标/目标满）= 返回 false 静默保持成熟，绝不吞作物
            return insertInto(server, below, drops(server, null));
        }
        return false;
    }

    /** 容量模拟：所有产物都能塞进容器才返回 true（不改容器状态）。 */
    private static boolean fitsInto(Container container, List<ItemStack> drops) {
        long[] sim = new long[container.getContainerSize()];
        for (int i = 0; i < sim.length; i++) {
            sim[i] = container.getItem(i).getCount();
        }
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            int remaining = drop.getCount();
            for (int i = 0; i < sim.length && remaining > 0; i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(drop, slot)) {
                    continue;
                }
                int cap = Math.max(Math.min(slot.getMaxStackSize(), container.getMaxStackSize()),
                    container.getMaxStackSize(drop));
                int move = Math.min((int) Math.max(0, cap - sim[i]), remaining);
                sim[i] += move;
                remaining -= move;
            }
            for (int i = 0; i < sim.length && remaining > 0; i++) {
                if (!container.getItem(i).isEmpty()) {
                    continue;
                }
                int cap = Math.max(Math.min(drop.getMaxStackSize(), container.getMaxStackSize()),
                    container.getMaxStackSize(drop));
                int move = Math.min(cap, remaining);
                sim[i] += move;
                remaining -= move;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /** 把产物逐叠塞进目标容器：返回是否全部塞入。 */
    private boolean insertInto(ServerLevel server, BlockPos target, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            if (server.getBlockEntity(target) instanceof Container container) {
                int size = container.getContainerSize();
                for (int i = 0; i < size && !drop.isEmpty(); i++) {
                    ItemStack slot = container.getItem(i);
                    if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(drop, slot)) {
                        continue;
                    }
                    // 大容量容器（储物抽屉等）单格可远超 64：按容器声明的逐物品上限与无参上限取大者
                    int cap = Math.max(Math.min(slot.getMaxStackSize(), container.getMaxStackSize()),
                        container.getMaxStackSize(drop));
                    int room = cap - slot.getCount();
                    if (room <= 0) {
                        continue;
                    }
                    int move = Math.min(room, drop.getCount());
                    slot.grow(move);
                    drop.shrink(move);
                }
                for (int i = 0; i < size && !drop.isEmpty(); i++) {
                    if (container.getItem(i).isEmpty() && container.canPlaceItem(i, drop)) {
                        container.setItem(i, drop.split(drop.getCount()));
                    }
                }
                if (size > 0) {
                    container.setChanged();
                }
            }
            if (!drop.isEmpty() && CompressedBlocks.ITEM_SINK != null) {
                long moved = CompressedBlocks.ITEM_SINK.insert(server, target,
                    net.minecraft.core.Direction.UP, drop);
                if (moved > 0) {
                    drop.shrink((int) Math.min(moved, drop.getCount()));
                }
            }
            if (!drop.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 客户端本地推进进度（Botany Pots 同款）：渲染平滑长大，不发包。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, CompressedPotBlockEntity be) {
        if (be.plant != null && be.growth < be.requiredGrowth()) {
            be.growth++;
        }
    }

    /**
     * 收获掉落（用户逐项审定 2026-10-03）：作物类走定稿产量表+压缩土壤翻倍；
     * 压缩作物（附属盆栽）仍走方块自身命名空间的战利品表（含时运）；
     * 树苗类（含红树胚/杜鹃/开花杜鹃）= 对应原木 3-8 + 树苗 0-3，木头类**不吃翻倍**。
     */
    List<ItemStack> drops(ServerLevel server, Player player) {
        List<ItemStack> out = new ArrayList<>();
        Block block = this.plant.getBlock();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        String name = blockId.getPath();
        if (block instanceof CompressedCropBlock || isCropPlant(block)) {
            if (block instanceof CompressedCropBlock crop) {
                // 压缩作物战利品表带 age 条件：收割走满龄状态
                ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                    blockId.withPrefix("blocks/"));
                LootParams params = new LootParams.Builder(server)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(getBlockPos()))
                    .withParameter(LootContextParams.BLOCK_STATE, crop.getStateForAge(crop.getMaxAge()))
                    .withParameter(LootContextParams.TOOL,
                        player == null ? ItemStack.EMPTY : player.getMainHandItem())
                    .create(LootContextParamSets.BLOCK);
                server.getServer().reloadableRegistries().getLootTable(key)
                    .getRandomItems(params, out::add);
            } else {
                cropYields(server, out);
            }
            applySoilDoubling(server, out);
            return out;
        }
        if (block instanceof CompressedSaplingBlock || this.plant.is(BlockTags.SAPLINGS)) {
            // 树苗待遇（含红树胚/杜鹃，用户定稿）：对应原木 3-8 + 树苗 0-3，木头类不吃翻倍
            Identifier logId = block instanceof CompressedSaplingBlock
                ? CompressedBlocks.id(name.substring(0, name.length() - "_sapling".length()) + "_log")
                : Identifier.fromNamespaceAndPath(blockId.getNamespace(), woodOf(name) + "_log");
            var log = BuiltInRegistries.BLOCK.get(logId);
            int logs = SAPLING_LOGS_MIN + server.random.nextInt(SAPLING_LOGS_SPAN);
            out.add(log.filter(h -> h.value().asItem() != net.minecraft.world.item.Items.AIR)
                .map(h -> new ItemStack(h.value().asItem(), logs))
                .orElse(new ItemStack(net.minecraft.world.item.Items.STICK, logs)));
            ItemStack self = itemStack(this.plantItem);
            int saplings = server.random.nextInt(4);
            if (saplings > 0 && !self.isEmpty()) {
                out.add(new ItemStack(self.getItem(), saplings));
            }
            return out;
        }
        // 兜底（白名单全覆盖，不应到达）：自身一份
        ItemStack self = itemStack(this.plantItem);
        if (!self.isEmpty()) {
            out.add(self);
        }
        return out;
    }

    /** 树苗 → 原木名：*_sapling 去后缀；红树胚 → mangrove；杜鹃/开花杜鹃长成橡木树。 */
    private static String woodOf(String name) {
        if (name.endsWith("_sapling")) {
            return name.substring(0, name.length() - "_sapling".length());
        }
        if (name.equals("mangrove_propagule")) {
            return "mangrove";
        }
        return "oak";
    }

    /** 压缩土壤翻倍概率（用户定稿 2026-10-03）：1-9 重 = 15/20/25/30/35/40/45/50/60%，索引=重数。 */
    static final float[] SOIL_DOUBLE_CHANCE = {
        0.0F, 0.15F, 0.20F, 0.25F, 0.30F, 0.35F, 0.40F, 0.45F, 0.50F, 0.60F
    };

    /**
     * 作物定稿产量（用户逐项审定 2026-10-03，基准 = 原版一次收获 -10%~-30%）：
     * 土豆/胡萝卜 2-4；小麦=麦粒1-2+种子0-2；甜菜=根1-2+种子1-2；地狱疣 2-3；
     * 西瓜=片3-5；南瓜=1个80%/2个15%/3个5%；火把花=花1+种子0-1；瓶子草=荚1-2；
     * 甜浆果 1-3；可可豆 2-3；仙人掌 2-3；竹子 4-8；甘蔗 2-3；其余作物本体 1-2。
     */
    private void cropYields(ServerLevel server, List<ItemStack> out) {
        Block block = this.plant.getBlock();
        if (block == Blocks.WHEAT) {
            addRange(out, Items.WHEAT, 1, 2, server);
            addRange(out, Items.WHEAT_SEEDS, 0, 2, server);
        } else if (block == Blocks.BEETROOTS) {
            addRange(out, Items.BEETROOT, 1, 2, server);
            addRange(out, Items.BEETROOT_SEEDS, 1, 2, server);
        } else if (block == Blocks.POTATOES) {
            addRange(out, Items.POTATO, 2, 4, server);
        } else if (block == Blocks.CARROTS) {
            addRange(out, Items.CARROT, 2, 4, server);
        } else if (block == Blocks.MELON_STEM) {
            addRange(out, Items.MELON_SLICE, 3, 5, server);
        } else if (block == Blocks.PUMPKIN_STEM) {
            // 南瓜（用户定稿）：80% 1 个 / 15% 2 个 / 5% 3 个，与压缩土壤翻倍正常叠加
            int roll = server.random.nextInt(100);
            out.add(new ItemStack(Items.PUMPKIN, roll < 80 ? 1 : roll < 95 ? 2 : 3));
        } else if (block == Blocks.TORCHFLOWER_CROP) {
            out.add(new ItemStack(Items.TORCHFLOWER));
            addRange(out, Items.TORCHFLOWER_SEEDS, 0, 1, server);
        } else if (block == Blocks.PITCHER_CROP) {
            addRange(out, Items.PITCHER_POD, 1, 2, server);
        } else if (block == Blocks.SWEET_BERRY_BUSH) {
            addRange(out, Items.SWEET_BERRIES, 1, 3, server);
        } else if (block == Blocks.NETHER_WART) {
            addRange(out, Items.NETHER_WART, 2, 3, server);
        } else if (block == Blocks.COCOA) {
            addRange(out, Items.COCOA_BEANS, 2, 3, server);
        } else if (block == Blocks.CACTUS) {
            addRange(out, Items.CACTUS, 2, 3, server);
        } else if (block == Blocks.BAMBOO) {
            addRange(out, Items.BAMBOO, 4, 8, server);
        } else if (block == Blocks.SUGAR_CANE) {
            addRange(out, Items.SUGAR_CANE, 2, 3, server);
        } else {
            ItemStack self = itemStack(this.plantItem);
            if (!self.isEmpty()) {
                addRange(out, self.getItem(), 1, 2, server);
            }
        }
    }

    /** 作物类（吃压缩土壤翻倍）：#crops + 竹子/甜果丛/可可/仙人掌/地狱疣/甘蔗。 */
    private boolean isCropPlant(Block block) {
        return this.plant.is(BlockTags.CROPS)
            || block instanceof net.minecraft.world.level.block.SugarCaneBlock
            || block instanceof net.minecraft.world.level.block.BambooStalkBlock
            || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
            || block instanceof net.minecraft.world.level.block.CocoaBlock
            || block instanceof net.minecraft.world.level.block.CactusBlock
            || block instanceof net.minecraft.world.level.block.NetherWartBlock;
    }

    /** 产量区间 [min,max] 均匀取整（min=0 时可不出）。 */
    private static void addRange(List<ItemStack> out, Item item,
                                 int min, int max, ServerLevel server) {
        int count = min + server.random.nextInt(max - min + 1);
        if (count > 0) {
            out.add(new ItemStack(item, count));
        }
    }

    /** 压缩土壤加成：按土壤重数掷翻倍概率，命中则本茬全部产物数量 ×2。
     *  只认压缩泥土/沙子/下界岩（原版土壤无加成；树苗/木头类不走此方法）。 */
    private void applySoilDoubling(ServerLevel server, List<ItemStack> out) {
        if (this.soil == null || out.isEmpty()) {
            return;
        }
        Integer tier = CompressedBlocks.dirtLevel(this.soil);
        if (tier == null) {
            tier = CompressedBlocks.sandLevel(this.soil);
        }
        if (tier == null) {
            tier = CompressedBlocks.netherSoilLevel(this.soil);
        }
        if (tier != null && tier >= 1 && tier < SOIL_DOUBLE_CHANCE.length
            && server.random.nextFloat() < SOIL_DOUBLE_CHANCE[tier]) {
            for (ItemStack stack : out) {
                stack.grow(stack.getCount());
            }
        }
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
