package dev.compressedblocks;

import dev.compressedblocks.forge.CompressedBlocksForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 盆栽方块实体：盆内土壤 + 植物 + 生长进度（Botany Pots 机制）。
 * 土壤等级（原版系=1、压缩系=重数）每高 1 级生长提速 5%；成熟后右键收割或漏斗盆栽自动压箱补种。
 * 1.16.5：TileEntity NBT 保存，tick 走 TickableBlockEntity（仅服务端推进）。
 */
public class CompressedPotBlockEntity extends BlockEntity implements TickableBlockEntity {
    /** 基础生长时长：1200 tick（60 秒）——1.21.11 同值。 */
    public static final int GROWTH_TICKS = 1200;

    private final boolean hopper;
    private BlockState soil;
    private BlockState plant;
    /** 植物来源物品 id（补种用，如 minecraft:wheat_seeds）。 */
    private String plantItemId;
    private int growth;
    private int requiredGrowth = GROWTH_TICKS;
    /** 漏斗盆栽自动收割重试冷却（tick）。 */
    private int hopperCooldown;

    public CompressedPotBlockEntity(boolean hopper) {
        super(CompressedBlocksForge.POT_TYPE);
        this.hopper = hopper;
    }

    public boolean isHopper() {
        return this.hopper;
    }

    /** 盆内土壤（null = 空盆）。 */
    public BlockState soil() {
        return this.soil;
    }

    /** 盆内植物（null = 未种）。 */
    public BlockState plant() {
        return this.plant;
    }

    /** 当前生长进度（0-1）。 */
    public float growthFraction() {
        return this.requiredGrowth <= 0 ? 0.0F : Math.min(1.0F, this.growth / (float) this.requiredGrowth);
    }

    /** 是否成熟。 */
    public boolean grown() {
        return this.plant != null && this.growth >= this.requiredGrowth;
    }

    /** 所需生长 tick：土壤等级每高 1 级 -5%（除以 1+0.05×等级）。 */
    public int requiredGrowth() {
        return this.requiredGrowth;
    }

    /** 填土并按等级重算生长时长。 */
    public void setSoil(BlockState soil) {
        this.soil = soil;
        this.recalcRequiredGrowth();
        this.setChanged();
    }

    /** 种植；土壤族不符返回 false。 */
    public boolean setPlant(BlockState plant, String itemId) {
        this.plant = plant;
        this.plantItemId = itemId;
        this.growth = 0;
        this.setChanged();
        return true;
    }

    /** 取出植物（掉落种子/植物本体），土壤保留。 */
    public ItemStack takePlant() {
        if (this.plantItemId == null) {
            this.plant = null;
            this.growth = 0;
            this.setChanged();
            return ItemStack.EMPTY;
        }
        ItemStack seed = ItemStack.of(itemIdTag(this.plantItemId));
        this.plant = null;
        this.plantItemId = null;
        this.growth = 0;
        this.setChanged();
        return seed;
    }

    /** 取出土壤。 */
    public ItemStack takeSoil() {
        if (this.soil == null) {
            return ItemStack.EMPTY;
        }
        ItemStack soilItem = new ItemStack(this.soil.getBlock());
        this.soil = null;
        this.setChanged();
        return soilItem;
    }

    private void recalcRequiredGrowth() {
        int soilLevel = this.soil == null ? 0 : CompressedPotBlock.soilLevelOf(this.soil);
        float speed = 1.0F + soilLevel * 0.05F;
        this.requiredGrowth = Math.max(1, Math.round(GROWTH_TICKS / speed));
    }

    @Override
    public void tick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (this.plant == null || this.grown()) {
            if (this.hopper && this.grown()) {
                if (this.hopperCooldown > 0) {
                    this.hopperCooldown--;
                } else {
                    autoHarvest();
                }
            }
            return;
        }
        this.growth++;
        if (this.growth % 40 == 0) {
            this.setChanged(); // 节流落盘（1.21.11 同款策略）
        }
    }

    /** 漏斗盆栽：成熟自动收割，产物压入下方容器并补种；无容器则静默等待（绝不吞作物）。 */
    private void autoHarvest() {
        BlockPos below = this.getBlockPos().below();
        if (this.level.getBlockEntity(below) instanceof Container) {
            List<ItemStack> drops = harvest();
            if (drops.isEmpty()) {
                this.hopperCooldown = 20;
                return;
            }
            Container target = (Container) this.level.getBlockEntity(below);
            for (ItemStack drop : drops) {
                pushInto(target, drop);
            }
            this.hopperCooldown = 10;
        } else {
            this.hopperCooldown = 40; // 无容器：静默等待玩家
        }
    }

    /** 把产物压入容器（可堆叠优先，空槽其次），压不进的掉地上不消失。 */
    private void pushInto(Container target, ItemStack stack) {
        for (int i = 0; i < target.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = target.getItem(i);
            if (!slot.isEmpty() && slot.getItem() == stack.getItem()
                && net.minecraft.world.item.ItemStack.tagMatches(slot, stack)) {
                int room = Math.min(slot.getMaxStackSize(), target.getMaxStackSize()) - slot.getCount();
                int move = Math.min(room, stack.getCount());
                if (move > 0) {
                    slot.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < target.getContainerSize() && !stack.isEmpty(); i++) {
            if (target.getItem(i).isEmpty() && target.canPlaceItem(i, stack)) {
                target.setItem(i, stack.split(stack.getCount()));
            }
        }
        if (!stack.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(this.level,
                this.getBlockPos().getX() + 0.5, this.getBlockPos().getY() + 0.7,
                this.getBlockPos().getZ() + 0.5, stack);
        }
        target.setChanged();
    }

    /**
     * 收割：按植物类型产出（简化产量表，1.16.5 原版作物掉落曲线的近似），
     * 成熟作物自动补种（种子若有）；仅成熟时可收割。
     */
    public List<ItemStack> harvest() {
        List<ItemStack> out = new ArrayList<>();
        if (!this.grown()) {
            return out;
        }
        Random random = this.level.random;
        Block block = this.plant.getBlock();
        if (block == Blocks.WHEAT) {
            out.add(new ItemStack(Items.WHEAT, 1 + random.nextInt(2)));
            out.add(new ItemStack(Items.WHEAT_SEEDS, 1 + random.nextInt(2)));
            this.plant = Blocks.WHEAT.defaultBlockState();
        } else if (block == Blocks.BEETROOTS) {
            out.add(new ItemStack(Items.BEETROOT, 1 + random.nextInt(2)));
            out.add(new ItemStack(Items.BEETROOT_SEEDS, 1 + random.nextInt(2)));
            this.plant = Blocks.BEETROOTS.defaultBlockState();
        } else if (block == Blocks.CARROTS) {
            out.add(new ItemStack(Items.CARROT, 2 + random.nextInt(3)));
            this.plant = Blocks.CARROTS.defaultBlockState();
        } else if (block == Blocks.POTATOES) {
            out.add(new ItemStack(Items.POTATO, 2 + random.nextInt(3)));
            this.plant = Blocks.POTATOES.defaultBlockState();
        } else if (block == Blocks.MELON_STEM) {
            out.add(new ItemStack(Items.MELON_SLICE, 3 + random.nextInt(3)));
            this.plant = Blocks.MELON_STEM.defaultBlockState();
        } else if (block == Blocks.PUMPKIN_STEM) {
            out.add(new ItemStack(Items.PUMPKIN, 1 + random.nextInt(2)));
            this.plant = Blocks.PUMPKIN_STEM.defaultBlockState();
        } else if (block == Blocks.SUGAR_CANE) {
            out.add(new ItemStack(Items.SUGAR_CANE, 2 + random.nextInt(3)));
            this.plant = Blocks.SUGAR_CANE.defaultBlockState();
        } else if (block == Blocks.CACTUS) {
            out.add(new ItemStack(Items.CACTUS, 2 + random.nextInt(3)));
            this.plant = Blocks.CACTUS.defaultBlockState();
        } else if (block == Blocks.BAMBOO) {
            out.add(new ItemStack(Items.BAMBOO, 4 + random.nextInt(5)));
            this.plant = Blocks.BAMBOO.defaultBlockState();
        } else if (block == Blocks.NETHER_WART) {
            out.add(new ItemStack(Items.NETHER_WART, 2 + random.nextInt(3)));
            this.plant = Blocks.NETHER_WART.defaultBlockState();
        } else if (block == Blocks.COCOA) {
            out.add(new ItemStack(Items.COCOA_BEANS, 3));
            this.plant = Blocks.COCOA.defaultBlockState();
        } else if (block == Blocks.SWEET_BERRY_BUSH) {
            out.add(new ItemStack(Items.SWEET_BERRIES, 2 + random.nextInt(3)));
            this.plant = Blocks.SWEET_BERRY_BUSH.defaultBlockState();
        } else if (block.defaultBlockState().is(net.minecraft.tags.BlockTags.SAPLINGS)) {
            // 原木 + 树苗（树苗补种回去）
            out.add(new ItemStack(logOf(block), 3 + random.nextInt(4)));
            out.add(new ItemStack(block));
            this.plant = block.defaultBlockState();
        } else {
            // 兜底：掉植物本体
            if (this.plantItemId != null) {
                out.add(ItemStack.of(itemIdTag(this.plantItemId)));
            }
            this.plant = block.defaultBlockState();
        }
        this.growth = 0;
        this.setChanged();
        return out;
    }

    /** 树苗 → 对应原木（盆栽砍树产物）。 */
    private static net.minecraft.world.item.Item logOf(Block sapling) {
        if (sapling == Blocks.OAK_SAPLING) {
            return Items.OAK_LOG;
        }
        if (sapling == Blocks.SPRUCE_SAPLING) {
            return Items.SPRUCE_LOG;
        }
        if (sapling == Blocks.BIRCH_SAPLING) {
            return Items.BIRCH_LOG;
        }
        if (sapling == Blocks.JUNGLE_SAPLING) {
            return Items.JUNGLE_LOG;
        }
        if (sapling == Blocks.ACACIA_SAPLING) {
            return Items.ACACIA_LOG;
        }
        if (sapling == Blocks.DARK_OAK_SAPLING) {
            return Items.DARK_OAK_LOG;
        }
        return Items.STICK;
    }

    private static CompoundTag itemIdTag(String itemId) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        CompoundTag tag = new CompoundTag();
        if (rl != null) {
            tag.putString("id", rl.toString());
            tag.putByte("Count", (byte) 1);
        }
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        super.save(tag);
        if (this.soil != null) {
            tag.putString("Soil", net.minecraft.core.Registry.BLOCK.getKey(this.soil.getBlock()).toString());
        }
        if (this.plant != null) {
            tag.putString("Plant", net.minecraft.core.Registry.BLOCK.getKey(this.plant.getBlock()).toString());
        }
        if (this.plantItemId != null) {
            tag.putString("PlantItem", this.plantItemId);
        }
        tag.putInt("Growth", this.growth);
        tag.putInt("RequiredGrowth", this.requiredGrowth);
        return tag;
    }

    @Override
    public void load(BlockState state, CompoundTag tag) {
        super.load(state, tag);
        if (tag.contains("Soil")) {
            Block soilBlock = net.minecraft.core.Registry.BLOCK.get(
                net.minecraft.resources.ResourceLocation.tryParse(tag.getString("Soil")));
            this.soil = soilBlock == null ? null : soilBlock.defaultBlockState();
        }
        if (tag.contains("Plant")) {
            Block plantBlock = net.minecraft.core.Registry.BLOCK.get(
                net.minecraft.resources.ResourceLocation.tryParse(tag.getString("Plant")));
            this.plant = plantBlock == null ? null : plantBlock.defaultBlockState();
        }
        this.plantItemId = tag.contains("PlantItem") ? tag.getString("PlantItem") : null;
        this.growth = Math.max(0, tag.getInt("Growth"));
        this.requiredGrowth = Math.max(1, tag.contains("RequiredGrowth")
            ? tag.getInt("RequiredGrowth") : GROWTH_TICKS);
    }
}
