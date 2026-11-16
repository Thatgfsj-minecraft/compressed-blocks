package dev.compressedblocks;

/**
 * 压缩熔炉多路并行改造的公共常量与槽位/数据编码（方块实体与菜单共用）。
 *
 * <p>容器槽采用「交错」布局：lane i 的 输入 = 3i、燃料 = 3i+1、产出 = 3i+2。
 * 这样 0.3.4 及更早的单槽存档（Items 槽 0/1/2 = 输入/燃料/产出）天然落到 lane 0，
 * 无需额外迁移代码。菜单槽位列表则按「输入区 -> 燃料区 -> 产出区」分区排列，
 * 便于 quickMoveStack 使用连续区间。</p>
 */
public final class CompressedFurnaceData {
    /** 每路固定 3 格。 */
    public static final int SLOTS_PER_LANE = 3;
    public static final int ROLE_INPUT = 0;
    public static final int ROLE_FUEL = 1;
    public static final int ROLE_OUTPUT = 2;
    /** 进度定点单位：1.0x 速度 = 每 tick 1000 milli。 */
    public static final int MILLI = 1000;

    private CompressedFurnaceData() {
    }

    /** 1 重 3 路、2 重 6 路、3/4 重 9 路。 */
    public static int lanesForTier(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 6;
            default -> 9;
        };
    }

    /**
     * 单路速度（milli / 真实 tick）：2x / 3x / 6x / 27x（2026-11-17 用户要求整体 ×2），
     * 整机吞吐 = 单路 × lane 数（3/6/9/9）= 6x / 18x / 54x / 243x。
     */
    public static int cookSpeedMilli(int tier) {
        return switch (tier) {
            case 1 -> 2000;
            case 2 -> 3000;
            case 3 -> 6000;
            default -> 27000;
        };
    }

    public static int slotCount(int lanes) {
        return lanes * SLOTS_PER_LANE;
    }

    public static int slot(int lane, int role) {
        return lane * SLOTS_PER_LANE + role;
    }

    public static int roleOf(int containerSlot) {
        return containerSlot % SLOTS_PER_LANE;
    }

    public static int laneOf(int containerSlot) {
        return containerSlot / SLOTS_PER_LANE;
    }

    /** 菜单数据槽数量：2N + 2（进度/活跃 2N 个 + 燃烧池剩余 tick 低/高各 1）。 */
    public static int dataCount(int lanes) {
        return 2 * lanes + 2;
    }

    /** 单槽数据最大跨度（short 传输）。 */
    public static final int DATA_CHUNK = 1 << 15;

    /** 2i：lane i 进度千分比（0..1000）。 */
    public static int progressIndex(int lane) {
        return 2 * lane;
    }

    /** 2i+1：lane i 当前能否烧（1/0），供界面着色。 */
    public static int activeIndex(int lane) {
        return 2 * lane + 1;
    }

    /** 2N：燃烧池剩余 tick 的低 15 位（配合 litHighIndex 还原完整 tick，支持数天~数年级燃料）。 */
    public static int litLowIndex(int lanes) {
        return 2 * lanes;
    }

    /** 2N+1：燃烧池剩余 tick 的高位。 */
    public static int litHighIndex(int lanes) {
        return 2 * lanes + 1;
    }

    /** ContainerData 经 short 传输，单值上限。 */
    public static final int MAX_DATA_VALUE = 32767;
}
