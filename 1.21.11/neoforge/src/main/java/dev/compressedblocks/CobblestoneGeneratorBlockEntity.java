package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 刷石机方块实体：内部缓冲一份产物（上限 = 该物品当前堆叠上限，默认 64；装了改堆叠的 mod 会跟随变化），
 * 每 20 tick（1 秒）产出 1 个本等级产物并尝试压入周围容器（箱子/木桶/漏斗都实现 Container），
 * 放不下留在缓冲里。
 * 输出方向优先级：下、上、北、南、西、东。
 *
 * <p>防御式输出（issue #39）：
 * <ol>
 *   <li>每次推送前校验缓存方向：目标必须仍是可接收的容器（被拆/被替换/红石锁死即失效），
 *       失效立即清空缓存并重扫；</li>
 *   <li>当前方向本 tick 一个也放不进时，按优先级依次尝试其余方向，找到能收的就切换缓存方向
 *       （不再"一次扫描定终身"——这是对旧「只喂第一个命中容器」语义的有意变更，
 *       目的是保证产物总能送进可用容器）；</li>
 *   <li>插入统一走 {@link HopperBlockEntity#addItem}，尊重 WorldlyContainer 的
 *       {@code canPlaceItemThroughFace} 与各容器堆叠上限；</li>
 *   <li>六向都收不进且缓冲已满（count == 该物品堆叠上限）时，把整组产物以掉落物弹到机器上方
 *       （原版刷石机语义），不允许无限憋货；</li>
 *   <li>目标若为漏斗且红石锁死（enabled=false），跳过该方向；</li>
 *   <li>输出受阻时节流日志（约 200 tick 一条），记录原因/坐标/当前方向，便于线上定位。</li>
 * </ol>
 *
 * <p>产物随压缩等级：1 级=原石，2 级=一重压缩原石，3 级=二重压缩原石。
 */
public class CobblestoneGeneratorBlockEntity extends BlockEntity {
    /** 产出周期：20 tick = 1 秒。 */
    public static final int CYCLE_TICKS = 20;
    /** 「输出受阻」日志节流：200 tick（10 秒）最多一条。 */
    static final long BLOCKED_LOG_INTERVAL = 200L;
    /** 六向输出优先级。 */
    static final Direction[] SCAN_ORDER = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private final int tier;
    private ItemStack buffer = ItemStack.EMPTY;
    private int progress;
    /** 缓存的输出方向；null = 周围无可接收产物的容器。 */
    private Direction outDir;
    /** 是否已扫过（无容器也不重复扫，邻居更新/缓存失效时重来）。 */
    private boolean scanned;
    /** 上次「输出受阻」日志的 gameTime，仅用于节流，不持久化。 */
    private long lastBlockedLogTick = -BLOCKED_LOG_INTERVAL;

    public CobblestoneGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(CompressedBlocks.GENERATOR_TYPE, pos, state);
        this.tier = state.getBlock() instanceof CobblestoneGeneratorBlock gen ? gen.tier() : 1;
    }

    private ItemStack product() {
        return switch (this.tier) {
            case 3 -> itemOrCobble(CompressedBlocks.itemByName("2x_cobblestone"));
            case 2 -> itemOrCobble(CompressedBlocks.itemByName("1x_cobblestone"));
            default -> new ItemStack(Items.COBBLESTONE);
        };
    }

    private static ItemStack itemOrCobble(Item item) {
        return item == null ? new ItemStack(Items.COBBLESTONE) : new ItemStack(item);
    }

    /** 当前缓冲上限：跟随物品的实时堆叠上限（改堆叠 mod 生效，默认 64）。 */
    private int bufferLimit() {
        return this.buffer.isEmpty() ? 64 : this.buffer.getMaxStackSize();
    }

    /** 邻居方块更新时重扫：六向按优先级取第一个可接收产物的目标（与放置顺序无关，下方始终优先上方）。 */
    public void rescanOutput() {
        Level level = getLevel();
        this.outDir = null;
        this.scanned = true;
        if (level == null) {
            return;
        }
        for (Direction d : SCAN_ORDER) {
            if (isOutputTarget(level, getBlockPos().relative(d), d)) {
                this.outDir = d;
                return;
            }
        }
    }

    /** 目标格当前能否接收产物：原版 Container（且未红石锁死），或加载器注入的大容量容器钩子。 */
    private boolean isOutputTarget(Level level, BlockPos target, Direction dir) {
        if (isRedstoneLocked(level, target)) {
            return false;
        }
        if (level.getBlockEntity(target) instanceof Container) {
            return true;
        }
        return CompressedBlocks.ITEM_SINK != null
            && CompressedBlocks.ITEM_SINK.accepts(level, target, dir.getOpposite());
    }

    /** 漏斗类方块被红石锁死（enabled=false）时不接收产物。 */
    private static boolean isRedstoneLocked(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(HopperBlock.ENABLED) && !state.getValue(HopperBlock.ENABLED);
    }

    /** 服务端：每秒产出 1 个并尽量压入输出方向的容器。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  CobblestoneGeneratorBlockEntity be) {
        if (++be.progress >= CYCLE_TICKS) {
            be.progress = 0;
            be.produce();
        }
        be.pushOutput();
    }

    private void produce() {
        ItemStack product = product();
        if (this.buffer.isEmpty()) {
            this.buffer = product;
            setChanged();
        } else if (ItemStack.isSameItemSameComponents(this.buffer, product)
            && this.buffer.getCount() < this.buffer.getMaxStackSize()) {
            this.buffer.grow(1);
            setChanged();
        }
    }

    /** 把缓冲压入缓存方向上的容器；该方向放不进则回退尝试其余方向。 */
    private void pushOutput() {
        if (this.buffer.isEmpty() || !(getLevel() instanceof ServerLevel server)) {
            return;
        }
        if (!this.scanned) {
            rescanOutput(); // 放置/加载后首扫
        }
        // 1) 缓存方向校验：目标不再可接收（被拆/被替换/红石锁死）→ 立即失效重扫
        if (this.outDir != null
            && !isOutputTarget(server, getBlockPos().relative(this.outDir), this.outDir)) {
            this.outDir = null;
            this.scanned = false;
            rescanOutput();
        }
        // 2) 先试缓存方向，放不进再按优先级回退其余方向（不要一次扫描定终身）
        boolean moved = this.outDir != null && insertInto(server, this.outDir);
        if (!moved) {
            for (Direction d : SCAN_ORDER) {
                if (d == this.outDir) {
                    continue;
                }
                if (insertInto(server, d)) {
                    this.outDir = d;
                    this.scanned = true;
                    moved = true;
                    break;
                }
            }
        }
        if (moved) {
            if (this.buffer.isEmpty()) {
                this.buffer = ItemStack.EMPTY;
            }
            return;
        }
        // 3) 六向都放不进：缓冲已满（一组）则整组弹到机器上方，不允许无限憋货
        if (this.buffer.getCount() >= this.buffer.getMaxStackSize()) {
            Block.popResource(server, getBlockPos().above(), this.buffer.copy());
            this.buffer = ItemStack.EMPTY;
            setChanged();
        }
        // 4) 节流日志：记录受阻原因，方便线上定位用户现场
        logBlocked(server);
    }

    /**
     * 尝试把缓冲尽量塞进某方向的目标；返回是否成功搬动至少 1 个。
     * 原版容器走 {@link HopperBlockEntity#addItem}（尊重 WorldlyContainer 面限制与堆叠上限），
     * 非原版容器走加载器注入的插入钩子（储物抽屉等）。
     */
    private boolean insertInto(ServerLevel level, Direction dir) {
        if (this.buffer.isEmpty()) {
            return false;
        }
        BlockPos target = getBlockPos().relative(dir);
        if (isRedstoneLocked(level, target)) {
            return false;
        }
        if (level.getBlockEntity(target) instanceof Container container) {
            ItemStack moving = this.buffer.copy();
            ItemStack remainder = HopperBlockEntity.addItem(null, container, moving, dir.getOpposite());
            int moved = this.buffer.getCount() - remainder.getCount();
            if (moved > 0) {
                this.buffer.shrink(moved);
                if (this.buffer.isEmpty()) {
                    this.buffer = ItemStack.EMPTY;
                }
                container.setChanged();
                setChanged();
                return true;
            }
            return false;
        }
        if (CompressedBlocks.ITEM_SINK != null
            && CompressedBlocks.ITEM_SINK.accepts(level, target, dir.getOpposite())) {
            ItemStack moving = this.buffer.copy();
            long moved = CompressedBlocks.ITEM_SINK.insert(level, target, dir.getOpposite(), moving);
            if (moved > 0) {
                this.buffer.shrink((int) Math.min(moved, this.buffer.getCount()));
                if (this.buffer.isEmpty()) {
                    this.buffer = ItemStack.EMPTY;
                }
                setChanged();
                return true;
            }
        }
        return false;
    }

    private void logBlocked(ServerLevel level) {
        long now = level.getGameTime();
        if (now - this.lastBlockedLogTick < BLOCKED_LOG_INTERVAL) {
            return;
        }
        this.lastBlockedLogTick = now;
        CompressedBlocks.LOGGER.warn(
            "cobblestone_generator@({}, {}, {}) 输出受阻：{}（buffer={}，outDir={}，tier={}）",
            getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(),
            describeBlocked(level), this.buffer.getCount(), this.outDir, this.tier);
    }

    /** 归纳当前「放不进」的原因，供节流日志使用。 */
    private String describeBlocked(ServerLevel level) {
        if (this.outDir == null) {
            return "周围没有可接收产物的容器";
        }
        BlockPos target = getBlockPos().relative(this.outDir);
        if (isRedstoneLocked(level, target)) {
            return "目标漏斗被红石锁死（enabled=false）";
        }
        if (level.getBlockEntity(target) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty() && container.canPlaceItem(i, this.buffer)) {
                    return "目标有空位但插入被拒（canPlaceItem/面限制）";
                }
                if (ItemStack.isSameItemSameComponents(slot, this.buffer)
                    && slot.getCount() < Math.min(slot.getMaxStackSize(),
                        container.getMaxStackSize(this.buffer))) {
                    return "目标同种物品未满但插入被拒（canPlaceItemThroughFace 限制）";
                }
            }
            return "目标容器已满或无空位（满仓/异种物品占位）";
        }
        return "目标非原版 Container 且加载器插入钩子拒绝";
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!this.buffer.isEmpty()) {
            out.store("Buffer", ItemStack.CODEC, this.buffer);
        }
        out.putInt("Progress", this.progress);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ItemStack saved = in.read("Buffer", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if (!saved.isEmpty() && saved.getCount() > saved.getMaxStackSize()) {
            saved.setCount(saved.getMaxStackSize()); // 老存档/异常数据防御：截回当前堆叠上限
        }
        this.buffer = saved;
        this.progress = Math.max(0, in.getIntOr("Progress", 0));
        this.scanned = false; // 加载后首 tick 重扫一次
        this.outDir = null;
        this.lastBlockedLogTick = -BLOCKED_LOG_INTERVAL;
    }
}
