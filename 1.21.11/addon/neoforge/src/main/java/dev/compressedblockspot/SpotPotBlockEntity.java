package dev.compressedblockspot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * 压缩盆栽方块实体：土壤/作物/进度独立实现；生长需求 = 600 tick /（1 + 等级差×5%）。
 * 收获：作物走方块自身命名空间战利品表（含时运）；树苗找同命名空间 "{wood}_log"×4-6
 * （无则木棍）；其余（甘蔗等）=2-4×自身，并补发一份作物本体。hopper 变体自动收割入下方容器。
 */
public class SpotPotBlockEntity extends BlockEntity {
    /** 基础生长时长：30 秒（最慢档）。 */
    public static final int GROWTH_TICKS = 600;
    private static final int SAPLING_LOGS = 4;
    private static final int CANE_MIN = 2;

    private BlockState soil;
    private BlockState plant;
    private String plantItem;
    private int growth;

    public SpotPotBlockEntity(BlockPos pos, BlockState state) {
        super(CompressedPotsAddon.POT_TYPE, pos, state);
    }

    public BlockState soil() {
        return this.soil;
    }

    public BlockState plant() {
        return this.plant;
    }

    public ItemStack plantItemStack() {
        return itemStack(this.plantItem);
    }

    /** 本茬所需 tick：土壤等级每比植物高 1 级提速 5%（原版植物 0 级）。 */
    public int requiredGrowth() {
        if (this.plant == null || this.soil == null) {
            return GROWTH_TICKS;
        }
        int soilLevel = SpotPotBlock.soilLevelOf(this.soil);
        int plantLevel = SpotPotBlock.plantLevel(this.plant);
        float boost = 1.0F + Math.max(0, soilLevel - plantLevel) * 0.05F;
        return Math.max(1, Math.round(GROWTH_TICKS / boost));
    }

    public float growthFraction() {
        if (this.plant == null) {
            return 0.0F;
        }
        return Math.min(1.0F, this.growth / (float) requiredGrowth());
    }

    public boolean grown() {
        return this.plant != null && this.growth >= requiredGrowth();
    }

    public boolean isHopperPot() {
        return getBlockState().getBlock() instanceof SpotPotBlock pot && pot.hopper();
    }

    public void setSoil(BlockState state) {
        this.soil = state;
        setChanged();
    }

    public boolean setPlant(BlockState state, String itemName) {
        if (this.soil == null || this.plant != null) {
            return false;
        }
        this.plant = state;
        this.plantItem = itemName;
        this.growth = 0;
        setChanged();
        return true;
    }

    public ItemStack takePlant() {
        if (this.plant == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = itemStack(this.plantItem);
        this.plant = null;
        this.plantItem = null;
        this.growth = 0;
        setChanged();
        return stack;
    }

    public ItemStack takeSoil() {
        if (this.soil == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(this.soil.getBlock());
        this.soil = null;
        setChanged();
        return stack;
    }

    /** 收割并自动补种。 */
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
        setChanged();
    }

    /** 盆被破坏：补发土壤与作物。 */
    public void dropContents() {
        if (!(getLevel() instanceof ServerLevel server)) {
            return;
        }
        if (this.soil != null) {
            Block.popResource(server, getBlockPos(), new ItemStack(this.soil.getBlock()));
        }
        ItemStack plant = takePlant();
        if (!plant.isEmpty()) {
            Block.popResource(server, getBlockPos(), plant);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpotPotBlockEntity be) {
        if (be.plant == null) {
            return;
        }
        int required = be.requiredGrowth();
        if (be.growth < required) {
            be.growth++;
            be.setChanged();
        }
        if (be.growth >= required && be.isHopperPot() && hasContainerBelow(be)) {
            be.harvest(null);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SpotPotBlockEntity be) {
        if (be.plant != null && be.growth < be.requiredGrowth()) {
            be.growth++;
        }
    }

    private static boolean hasContainerBelow(SpotPotBlockEntity be) {
        if (be.getLevel() == null) {
            return false;
        }
        if (be.getLevel().getBlockEntity(be.getBlockPos().below()) instanceof Container) {
            return true;
        }
        return CompressedPotsAddon.ITEM_SINK != null
            && CompressedPotsAddon.ITEM_SINK.accepts(be.getLevel(), be.getBlockPos().below(),
                net.minecraft.core.Direction.UP);
    }

    private List<ItemStack> drops(ServerLevel server, Player player) {
        List<ItemStack> out = new ArrayList<>();
        Block block = this.plant.getBlock();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        String name = blockId.getPath();
        RandomSource random = server.random;
        if (this.plant.is(BlockTags.CROPS)) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                blockId.withPrefix("blocks/"));
            LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(getBlockPos()))
                .withParameter(LootContextParams.BLOCK_STATE, this.plant)
                .withParameter(LootContextParams.TOOL,
                    player == null ? ItemStack.EMPTY : player.getMainHandItem())
                .create(LootContextParamSets.BLOCK);
            server.getServer().reloadableRegistries().getLootTable(key).getRandomItems(params, out::add);
            return out;
        }
        if (block instanceof net.minecraft.world.level.block.SaplingBlock
            || (this.plant.is(BlockTags.SAPLINGS) && name.endsWith("_sapling"))) {
            String wood = name.endsWith("_sapling")
                ? name.substring(0, name.length() - "_sapling".length()) : name;
            var log = BuiltInRegistries.BLOCK.get(
                Identifier.fromNamespaceAndPath(blockId.getNamespace(), wood + "_log"));
            int count = SAPLING_LOGS + random.nextInt(3);
            out.add(log.filter(h -> h.value().asItem() != Items.AIR)
                .map(h -> new ItemStack(h.value().asItem(), count))
                .orElse(new ItemStack(Items.STICK, count)));
        } else {
            ItemStack cane = itemStack(this.plantItem);
            cane.setCount(CANE_MIN + random.nextInt(3));
            out.add(cane);
        }
        ItemStack plant = itemStack(this.plantItem);
        if (!plant.isEmpty()) {
            out.add(plant);
        }
        return out;
    }

    /** hopper 输出：产物合并进下方容器（原版 Container 或大容量容器钩子），放不下返回 false（就地掉落）。 */
    private boolean insertIntoBelow(ItemStack stack) {
        if (getLevel().getBlockEntity(getBlockPos().below()) instanceof Container container) {
            int size = container.getContainerSize();
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(stack, slot)) {
                    continue;
                }
                // 大容量容器（储物抽屉等）单格可远超 64：按容器声明的逐物品上限与无参上限取大者
                int cap = Math.max(Math.min(slot.getMaxStackSize(), container.getMaxStackSize()),
                    container.getMaxStackSize(stack));
                int room = cap - slot.getCount();
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
        }
        // 非原版 Container / 大容量余量：走加载器注入的插入钩子（储物抽屉等）
        if (!stack.isEmpty() && CompressedPotsAddon.ITEM_SINK != null) {
            long moved = CompressedPotsAddon.ITEM_SINK.insert(getLevel(), getBlockPos().below(),
                net.minecraft.core.Direction.UP, stack);
            if (moved > 0) {
                stack.shrink((int) Math.min(moved, stack.getCount()));
            }
        }
        return stack.isEmpty();
    }

    private static ItemStack itemStack(String id) {
        Identifier rl = id == null ? null : Identifier.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.get(rl).map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (this.soil != null) {
            out.putString("Soil", BuiltInRegistries.BLOCK.getKey(this.soil.getBlock()).toString());
        }
        if (this.plant != null) {
            out.putString("Plant", BuiltInRegistries.BLOCK.getKey(this.plant.getBlock()).toString());
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

    private static BlockState blockState(String id) {
        Identifier rl = id == null || id.isEmpty() ? null : Identifier.tryParse(id);
        if (rl == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.get(rl).map(h -> h.value().defaultBlockState()).orElse(null);
    }
}
