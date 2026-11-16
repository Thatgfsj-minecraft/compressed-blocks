package dev.compressedblocks;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 压缩熔炉方块实体（并行多路版）。
 *
 * <p>每档槽位：1 重 3 入/3 燃/3 出（N=3）、2 重 6/6/6、3/4 重 9/9/9。
 * 每路独立配方缓存与进度（O(1) 批量结算，避免 81x 档位下每 tick 数百次配方 assemble），
 * 但整机共享一个 litTime 燃烧池：燃料按 lane 索引升序消耗（fuel_0 -> fuel_1 -> ...），
 * 按真实 tick 燃烧（不加速燃料消耗），因此档位越高单位燃料的产出越高。保留空转保护：
 * 没有任何一路可烧时不会点燃新燃料。</p>
 *
 * <p>容器槽交错布局见 {@link CompressedFurnaceData}；菜单数据槽 2N+1（进度千分比、
 * 活跃标志、燃烧池千分比），全部 <= 1000，天然满足数据包 short 传输范围。</p>
 */
public class CompressedFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
    private final int tier;
    private final int lanes;
    private final int cookSpeedMilli;
    /** 每路已烧进度（milli tick）。 */
    private final int[] cookTime;
    /** 每路当前配方总时长（milli tick，0 = 无有效配方）。 */
    private final int[] cookTotal;
    /** 全机共享燃烧池（真实 tick）。 */
    private int litTime;
    private int litTotal;
    /** 每 tick 复用的临时快照，避免每 tick 分配。 */
    private final boolean[] laneReady;
    private final ItemStack[] laneResult;
    private final RecipeHolder<SmeltingRecipe>[] laneRecipe;
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe>[] checks;
    private final int[] slotsUp;
    private final int[] slotsSide;
    private final int[] slotsDown;
    private final ContainerData menuData;

    @SuppressWarnings("unchecked")
    public CompressedFurnaceBlockEntity(BlockPos pos, BlockState state, int tier) {
        super(CompressedBlocks.FURNACE_TYPE[tier - 1], pos, state, RecipeType.SMELTING);
        this.tier = tier;
        this.lanes = CompressedFurnaceData.lanesForTier(tier);
        this.cookSpeedMilli = CompressedFurnaceData.cookSpeedMilli(tier);
        this.cookTime = new int[this.lanes];
        this.cookTotal = new int[this.lanes];
        this.laneReady = new boolean[this.lanes];
        this.laneResult = new ItemStack[this.lanes];
        Arrays.fill(this.laneResult, ItemStack.EMPTY);
        this.laneRecipe = new RecipeHolder[this.lanes];
        // 父类构造器先建了 3 格 items；这里整表替换为 3N 格（getContainerSize() 返回 items.size()）
        this.items = NonNullList.withSize(CompressedFurnaceData.slotCount(this.lanes), ItemStack.EMPTY);
        this.checks = (RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe>[])
            new RecipeManager.CachedCheck<?, ?>[this.lanes];
        for (int i = 0; i < this.lanes; i++) {
            // CachedCheck 只缓存 1 条 lastRecipe，必须每路一个，否则互相踩缓存导致全表扫描
            this.checks[i] = RecipeManager.createCheck(RecipeType.SMELTING);
        }
        this.slotsUp = new int[this.lanes];
        this.slotsSide = new int[this.lanes];
        this.slotsDown = new int[this.lanes * 2];
        for (int i = 0; i < this.lanes; i++) {
            this.slotsUp[i] = CompressedFurnaceData.slot(i, CompressedFurnaceData.ROLE_INPUT);
            this.slotsSide[i] = CompressedFurnaceData.slot(i, CompressedFurnaceData.ROLE_FUEL);
            this.slotsDown[i] = CompressedFurnaceData.slot(i, CompressedFurnaceData.ROLE_OUTPUT);
            this.slotsDown[this.lanes + i] = CompressedFurnaceData.slot(i, CompressedFurnaceData.ROLE_FUEL);
        }
        this.menuData = new ContainerData() {
            @Override
            public int get(int index) {
                if (index < 0 || index >= CompressedFurnaceData.dataCount(lanes)) {
                    return 0;
                }
                if (index == CompressedFurnaceData.litIndex(lanes)) {
                    return litPermille();
                }
                int lane = index / 2;
                if ((index & 1) == 0) {
                    return progressPermille(lane);
                }
                return laneReady[lane] ? 1 : 0;
            }

            @Override
            public void set(int index, int value) {
                // 服务端只读；客户端菜单写入的是自己的哑数据，不会回到这里
            }

            @Override
            public int getCount() {
                return CompressedFurnaceData.dataCount(lanes);
            }
        };
    }

    public int tier() {
        return this.tier;
    }

    public int lanes() {
        return this.lanes;
    }

    public int cookSpeedMilli() {
        return this.cookSpeedMilli;
    }

    public ContainerData menuData() {
        return this.menuData;
    }

    /** 档位可烧的压缩等级上限：1 重 <=3、2 重 <=6、3 重 <=9、4 重全通。 */
    public static int maxLevelForTier(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 6;
            case 3 -> 9;
            default -> Integer.MAX_VALUE;
        };
    }

    public static int levelOfItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals(CompressedBlocks.MOD_ID)) {
            return 0;
        }
        String path = id.getPath();
        int x = path.indexOf('x');
        if (x > 0 && x <= 2 && path.length() > x + 1 && path.charAt(x + 1) == '_') {
            String digits = path.substring(0, x);
            if (digits.chars().allMatch(Character::isDigit)) {
                try {
                    return Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    public static boolean isItemAllowed(int tier, ItemStack stack) {
        return levelOfItem(stack) <= maxLevelForTier(tier);
    }

    public boolean isItemAllowed(ItemStack stack) {
        return isItemAllowed(this.tier, stack);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.compressedblocks."
            + CompressedBlocks.LEVEL_PREFIX[this.tier - 1] + "_compressed_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return new CompressedFurnaceMenu(CompressedFurnaceMenus.forTier(this.tier), id, playerInventory,
            this, this.menuData, this.tier);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        int role = CompressedFurnaceData.roleOf(slot);
        if (role == CompressedFurnaceData.ROLE_OUTPUT) {
            return false;
        }
        if (role == CompressedFurnaceData.ROLE_FUEL) {
            return (this.level != null && this.level.fuelValues().isFuel(stack)) || stack.is(Items.BUCKET);
        }
        return isItemAllowed(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return this.slotsDown;
        }
        return side == Direction.UP ? this.slotsUp : this.slotsSide;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (side == Direction.DOWN) {
            int role = CompressedFurnaceData.roleOf(slot);
            if (role == CompressedFurnaceData.ROLE_OUTPUT) {
                return true;
            }
            if (role == CompressedFurnaceData.ROLE_FUEL) {
                return stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET);
            }
            return false;
        }
        return true;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack previous = this.items.get(slot);
        this.items.set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        if (CompressedFurnaceData.roleOf(slot) == CompressedFurnaceData.ROLE_INPUT
            && !ItemStack.isSameItemSameComponents(previous, stack)) {
            // 输入换了东西：该路进度清零（父类对槽 0 的同款语义，这里按 lane 生效）
            int lane = CompressedFurnaceData.laneOf(slot);
            this.cookTime[lane] = 0;
            this.cookTotal[lane] = 0;
        }
        this.setChanged();
    }

    public boolean isLitNow() {
        return this.litTime > 0;
    }

    private int progressPermille(int lane) {
        int total = this.cookTotal[lane];
        if (total <= 0) {
            return 0;
        }
        return Mth.clamp((int) ((long) this.cookTime[lane] * 1000L / total), 0, 1000);
    }

    private int litPermille() {
        if (this.litTotal <= 0) {
            return 0;
        }
        return Mth.clamp((int) ((long) this.litTime * 1000L / this.litTotal), 0, 1000);
    }

    /** 所有产出槽合计还能接收多少件 result（0 = 全部接不住）。 */
    private int outputRoom(ItemStack result) {
        int room = 0;
        for (int lane = 0; lane < this.lanes; lane++) {
            int slot = CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_OUTPUT);
            ItemStack out = this.items.get(slot);
            if (out.isEmpty()) {
                room += result.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(out, result)) {
                room += Math.max(0, out.getMaxStackSize() - out.getCount());
            }
        }
        return room;
    }

    /**
     * 把产物按"箱子式自动堆叠"放进产出槽：先填同类堆，再找空槽（均按 lane 升序）。
     *
     * @return 实际放入数量（调用方据此扣输入与结算经验）
     */
    private int insertOutput(ItemStack result, int count) {
        int remaining = count;
        // 第一遍：优先同类堆叠
        for (int lane = 0; lane < this.lanes && remaining > 0; lane++) {
            int slot = CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_OUTPUT);
            ItemStack out = this.items.get(slot);
            if (out.isEmpty() || !ItemStack.isSameItemSameComponents(out, result)) {
                continue;
            }
            int room = Math.max(0, out.getMaxStackSize() - out.getCount());
            int move = Math.min(room, remaining);
            if (move > 0) {
                out.grow(move);
                remaining -= move;
            }
        }
        // 第二遍：放进空槽
        for (int lane = 0; lane < this.lanes && remaining > 0; lane++) {
            int slot = CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_OUTPUT);
            ItemStack out = this.items.get(slot);
            if (!out.isEmpty()) {
                continue;
            }
            int move = Math.min(result.getMaxStackSize(), remaining);
            this.items.set(slot, result.copyWithCount(move));
            remaining -= move;
        }
        return count - remaining;
    }

    /** 比较器输出：只统计 N 个产出槽，避免 27 格稀释（与原版单槽熔炉同量级）。 */
    public int getComparatorOutput() {
        float sum = 0.0F;
        for (int lane = 0; lane < this.lanes; lane++) {
            ItemStack out = this.items.get(CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_OUTPUT));
            if (out.isEmpty()) {
                continue;
            }
            sum += (float) out.getCount() / (float) out.getMaxStackSize();
        }
        return Mth.lerpDiscrete(sum / this.lanes, 0, 15);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putIntArray("CookTime", this.cookTime);
        out.putIntArray("CookTotal", this.cookTotal);
        out.putInt("LitTime", this.litTime);
        out.putInt("LitTotal", this.litTotal);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int[] savedCookTime = in.getIntArray("CookTime").orElse(null);
        if (savedCookTime == null) {
            // 0.3.4 及更早的单槽存档：3 格 Items 已由交错布局落到 lane 0，进度单位是 tick -> milli
            this.cookTime[0] = in.getShortOr("cooking_time_spent", (short) 0) * CompressedFurnaceData.MILLI;
            this.cookTotal[0] = in.getShortOr("cooking_total_time", (short) 0) * CompressedFurnaceData.MILLI;
            this.litTime = in.getShortOr("lit_time_remaining", (short) 0);
            this.litTotal = in.getShortOr("lit_total_time", (short) 0);
            return;
        }
        System.arraycopy(savedCookTime, 0, this.cookTime, 0,
            Math.min(savedCookTime.length, this.cookTime.length));
        int[] savedCookTotal = in.getIntArray("CookTotal").orElse(new int[0]);
        System.arraycopy(savedCookTotal, 0, this.cookTotal, 0,
            Math.min(savedCookTotal.length, this.cookTotal.length));
        this.litTime = in.getIntOr("LitTime", 0);
        this.litTotal = in.getIntOr("LitTotal", 0);
    }

    /** 消耗燃料池：按 lane 索引升序找第一个可烧燃料。 */
    private static boolean ignite(CompressedFurnaceBlockEntity be, FuelValues fuels) {
        for (int lane = 0; lane < be.lanes; lane++) {
            int fuelSlot = CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_FUEL);
            ItemStack fuel = be.items.get(fuelSlot);
            if (fuel.isEmpty()) {
                continue;
            }
            int duration = fuels.burnDuration(fuel);
            if (duration <= 0) {
                continue;
            }
            be.litTime = duration;
            be.litTotal = duration;
            Item item = fuel.getItem();
            fuel.shrink(1);
            if (fuel.isEmpty()) {
                be.items.set(fuelSlot, item.getCraftingRemainder());
            }
            return true;
        }
        return false;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, CompressedFurnaceBlockEntity be) {
        boolean wasLit = be.isLitNow();
        boolean changed = false;
        int lanes = be.lanes;

        // 1) 共享燃烧池按真实 tick 递减（不随速度加速，档位越高燃料效率越高）
        if (be.litTime > 0) {
            be.litTime--;
            changed = true;
        }

        // 2) 每路配方/输出快照（一次 assemble，避免每件重算）
        FuelValues fuels = level.fuelValues();
        RegistryAccess registries = level.registryAccess();
        boolean anyCanSmelt = false;
        for (int lane = 0; lane < lanes; lane++) {
            int inputSlot = CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_INPUT);
            ItemStack input = be.items.get(inputSlot);
            RecipeHolder<SmeltingRecipe> recipe = null;
            ItemStack result = ItemStack.EMPTY;
            if (!input.isEmpty() && be.isItemAllowed(input)) {
                recipe = be.checks[lane].getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
                if (recipe != null) {
                    result = recipe.value().assemble(new SingleRecipeInput(input), registries);
                }
            }
            boolean ready = recipe != null && !result.isEmpty() && be.outputRoom(result) > 0;
            be.laneRecipe[lane] = recipe;
            be.laneResult[lane] = ready ? result : ItemStack.EMPTY;
            be.laneReady[lane] = ready;
            be.cookTotal[lane] = ready ? recipe.value().cookingTime() * CompressedFurnaceData.MILLI : 0;
            anyCanSmelt |= ready;
        }

        // 3) 空转保护：只有至少一路可烧且燃烧池为空时才点燃新燃料
        if (be.litTime == 0 && anyCanSmelt && ignite(be, fuels)) {
            changed = true;
        }

        // 4) 各路独立推进（O(1) 批量结算）
        boolean lit = be.litTime > 0;
        for (int lane = 0; lane < lanes; lane++) {
            if (lit && be.laneReady[lane]) {
                be.cookLane(lane);
                changed = true;
            } else if (lit) {
                if (be.cookTime[lane] != 0) {
                    be.cookTime[lane] = 0;
                    changed = true;
                }
            } else if (be.cookTime[lane] > 0) {
                // 未点燃：原版同款平滑降温（BURN_COOL_SPEED = 2 tick）
                be.cookTime[lane] = Math.max(0, be.cookTime[lane] - 2 * CompressedFurnaceData.MILLI);
                changed = true;
            }
        }

        // 5) LIT 聚合 + 比较器/邻居刷新（setChanged 内部会 updateNeighbourForOutputSignal）
        boolean litNow = be.litTime > 0;
        if (wasLit != litNow) {
            state = state.setValue(AbstractFurnaceBlock.LIT, litNow);
            level.setBlock(pos, state, 3);
            changed = true;
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    private void cookLane(int lane) {
        int total = this.cookTotal[lane];
        ItemStack result = this.laneResult[lane];
        RecipeHolder<SmeltingRecipe> recipe = this.laneRecipe[lane];
        if (total <= 0 || result.isEmpty() || recipe == null) {
            return;
        }
        this.cookTime[lane] += this.cookSpeedMilli;
        int produced = this.cookTime[lane] / total;
        if (produced <= 0) {
            return;
        }
        int inputSlot = CompressedFurnaceData.slot(lane, CompressedFurnaceData.ROLE_INPUT);
        ItemStack input = this.items.get(inputSlot);
        int count = Math.min(produced, Math.min(this.outputRoom(result), input.getCount()));
        if (count <= 0) {
            // 输出满/输入空：与原版一致，进度清零（点燃中的燃料继续烧完）
            this.cookTime[lane] = 0;
            return;
        }
        int inserted = this.insertOutput(result, count);
        if (inserted <= 0) {
            this.cookTime[lane] = 0;
            return;
        }
        this.cookTime[lane] -= inserted * total;
        input.shrink(inserted);
        for (int i = 0; i < inserted; i++) {
            this.setRecipeUsed(recipe);
        }
    }
}
