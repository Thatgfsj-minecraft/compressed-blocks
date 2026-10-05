package dev.compressedblocks;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 盆栽方块实体：盆内土壤 + 植物 + 生长进度（Botany Pots 机制）。
 * 土壤等级（原版系=1、压缩系=重数）每高 1 级生长提速 5%；成熟后右键收割或漏斗盆栽自动压箱补种。
 * 1.12.2：TileEntity + ITickable；方块/物品名走 ResourceLocation 注册表查询。
 * 降级记录：1.12.2 原版无甜果丛/竹子/火把花等（1.14+ 内容），收割表相应裁剪。
 */
public class CompressedPotBlockEntity extends TileEntity implements ITickable {
    /** 基础生长时长：1200 tick（60 秒）——1.21.11 同值。 */
    public static final int GROWTH_TICKS = 1200;

    private final boolean hopper;
    private IBlockState soil;
    private IBlockState plant;
    /** 植物来源物品 id（补种用，如 minecraft:wheat_seeds）。 */
    private String plantItemId;
    private int growth;
    private int requiredGrowth = GROWTH_TICKS;
    /** 漏斗盆栽自动收割重试冷却（tick）。 */
    private int hopperCooldown;

    public CompressedPotBlockEntity(boolean hopper) {
        this.hopper = hopper;
    }

    public boolean isHopper() {
        return this.hopper;
    }

    /** 盆内土壤（null = 空盆）。 */
    public IBlockState soil() {
        return this.soil;
    }

    /** 盆内植物（null = 未种）。 */
    public IBlockState plant() {
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

    /** 所需生长 tick。 */
    public int requiredGrowth() {
        return this.requiredGrowth;
    }

    /** 填土并按等级重算生长时长。 */
    public void setSoil(IBlockState soil) {
        this.soil = soil;
        this.recalcRequiredGrowth();
        this.markDirty();
    }

    /** 种植；土壤族不符返回 false。 */
    public boolean setPlant(IBlockState plant, String itemId) {
        this.plant = plant;
        this.plantItemId = itemId;
        this.growth = 0;
        this.markDirty();
        return true;
    }

    /** 取出植物（掉落种子/植物本体），土壤保留。 */
    public ItemStack takePlant() {
        if (this.plantItemId == null) {
            this.plant = null;
            this.growth = 0;
            this.markDirty();
            return ItemStack.EMPTY;
        }
        ItemStack seed = itemIdStack(this.plantItemId);
        this.plant = null;
        this.plantItemId = null;
        this.growth = 0;
        this.markDirty();
        return seed;
    }

    /** 取出土壤。 */
    public ItemStack takeSoil() {
        if (this.soil == null) {
            return ItemStack.EMPTY;
        }
        ItemStack soilItem = new ItemStack(this.soil.getBlock());
        this.soil = null;
        this.markDirty();
        return soilItem;
    }

    private void recalcRequiredGrowth() {
        int soilLevel = this.soil == null ? 0 : CompressedPotBlock.soilLevelOf(this.soil);
        float speed = 1.0F + soilLevel * 0.05F;
        this.requiredGrowth = Math.max(1, Math.round(GROWTH_TICKS / speed));
    }

    @Override
    public void update() {
        if (this.world == null || this.world.isRemote) {
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
            this.markDirty(); // 节流落盘（1.21.11 同款策略）
        }
    }

    /** 漏斗盆栽：成熟自动收割，产物压入下方容器并补种；无容器则静默等待（绝不吞作物）。 */
    private void autoHarvest() {
        BlockPos below = getPos().down();
        TileEntity te = this.world.getTileEntity(below);
        if (te instanceof IInventory) {
            List<ItemStack> drops = harvest();
            if (drops.isEmpty()) {
                this.hopperCooldown = 20;
                return;
            }
            IInventory target = (IInventory) te;
            for (ItemStack drop : drops) {
                pushInto(target, drop);
            }
            this.hopperCooldown = 10;
        } else {
            this.hopperCooldown = 40; // 无容器：静默等待玩家
        }
    }

    /** 把产物压入容器（可堆叠优先，空槽其次），压不进的掉地上不消失。 */
    private void pushInto(IInventory target, ItemStack stack) {
        for (int i = 0; i < target.getSizeInventory() && !stack.isEmpty(); i++) {
            ItemStack slot = target.getStackInSlot(i);
            if (!slot.isEmpty() && slot.getItem() == stack.getItem()
                && ItemStack.areItemStackTagsEqual(slot, stack)) {
                int room = Math.min(slot.getMaxStackSize(), target.getInventoryStackLimit()) - slot.getCount();
                int move = Math.min(room, stack.getCount());
                if (move > 0) {
                    slot.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < target.getSizeInventory() && !stack.isEmpty(); i++) {
            if (target.getStackInSlot(i).isEmpty() && target.isItemValidForSlot(i, stack)) {
                target.setInventorySlotContents(i, stack.copy());
                stack.setCount(0);
            }
        }
        if (!stack.isEmpty()) {
            net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(
                this.world, getPos().getX() + 0.5, getPos().getY() + 0.7, getPos().getZ() + 0.5, stack);
            drop.setDefaultPickupDelay();
            this.world.spawnEntity(drop);
        }
        target.markDirty();
    }

    /**
     * 收割：按植物类型产出（简化产量表，1.16.5 原版作物掉落曲线的近似），
     * 成熟作物自动补种（种子若有）；仅成熟时可收割。
     * 1.12.2：原版方块常量名不同（CROPS/BLOCKS），甜果丛/竹子/可可树形裁剪。
     */
    public List<ItemStack> harvest() {
        List<ItemStack> out = new ArrayList<>();
        if (!this.grown()) {
            return out;
        }
        Random random = this.world.rand;
        Block block = this.plant.getBlock();
        if (block == net.minecraft.init.Blocks.WHEAT) {
            out.add(new ItemStack(net.minecraft.init.Items.WHEAT, 1 + random.nextInt(2)));
            out.add(new ItemStack(net.minecraft.init.Items.WHEAT_SEEDS, 1 + random.nextInt(2)));
            this.plant = net.minecraft.init.Blocks.WHEAT.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.BEETROOTS) {
            out.add(new ItemStack(net.minecraft.init.Items.BEETROOT, 1 + random.nextInt(2)));
            out.add(new ItemStack(net.minecraft.init.Items.BEETROOT_SEEDS, 1 + random.nextInt(2)));
            this.plant = net.minecraft.init.Blocks.BEETROOTS.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.CARROTS) {
            out.add(new ItemStack(net.minecraft.init.Items.CARROT, 2 + random.nextInt(3)));
            this.plant = net.minecraft.init.Blocks.CARROTS.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.POTATOES) {
            out.add(new ItemStack(net.minecraft.init.Items.POTATO, 2 + random.nextInt(3)));
            this.plant = net.minecraft.init.Blocks.POTATOES.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.MELON_STEM) {
            // 1.12.2 无西瓜片/南瓜物品（均为方块，1.13+ 才有独立 Item）：掉方块
            out.add(new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.MELON_BLOCK), 3 + random.nextInt(3)));
            this.plant = net.minecraft.init.Blocks.MELON_STEM.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.PUMPKIN_STEM) {
            out.add(new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PUMPKIN), 1 + random.nextInt(2)));
            this.plant = net.minecraft.init.Blocks.PUMPKIN_STEM.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.REEDS) {
            out.add(new ItemStack(net.minecraft.init.Items.REEDS, 2 + random.nextInt(3)));
            this.plant = net.minecraft.init.Blocks.REEDS.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.CACTUS) {
            out.add(new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.CACTUS), 2 + random.nextInt(3)));
            this.plant = net.minecraft.init.Blocks.CACTUS.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.NETHER_WART) {
            out.add(new ItemStack(net.minecraft.init.Items.NETHER_WART, 2 + random.nextInt(3)));
            this.plant = net.minecraft.init.Blocks.NETHER_WART.getDefaultState();
        } else if (block == net.minecraft.init.Blocks.COCOA) {
            out.add(new ItemStack(net.minecraft.init.Items.DYE, 3, 3));
            this.plant = net.minecraft.init.Blocks.COCOA.getDefaultState();
        } else {
            // 原版系树苗：原木 + 树苗（树苗补种回去）
            ItemPair pair = saplingLogOf(block);
            if (pair != null) {
                out.add(new ItemStack(pair.log, 3 + random.nextInt(4)));
                out.add(new ItemStack(block));
                this.plant = block.getDefaultState();
            } else {
                // 兜底：掉植物本体
                if (this.plantItemId != null) {
                    ItemStack back = itemIdStack(this.plantItemId);
                    if (!back.isEmpty()) {
                        out.add(back);
                    }
                }
                this.plant = block.getDefaultState();
            }
        }
        this.growth = 0;
        this.markDirty();
        return out;
    }

    /** 1.12.2 甘蔗/物品常量隔离（REEDS/REEDS 命名与 1.16 的 SUGAR_CANE/SUGAR_CANE 不同）。 */
    private static final class NameRefs {
        // 1.12.2：Blocks.REEDS = 甘蔗、Items.REEDS = 甘蔗物品
    }

    /** 树苗 → 对应原木（盆栽砍树产物）；仅原版系树苗返回非 null。 */
    private static ItemPair saplingLogOf(Block sapling) {
        // 1.12.2 ResourceLocation 无 domain/path 访问器：用 toString 解析
        String id = String.valueOf(Block.REGISTRY.getNameForObject(sapling));
        String domain = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        if (!"minecraft".equals(domain)) {
            return null;
        }
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (!path.endsWith("_sapling")) {
            return null;
        }
        String wood = path.substring(0, path.length() - "_sapling".length());
        net.minecraft.item.Item log = net.minecraft.item.Item.getByNameOrId(
            "minecraft:" + wood + "_log");
        if (log == null) {
            return null;
        }
        return new ItemPair(log);
    }

    private static final class ItemPair {
        final net.minecraft.item.Item log;

        ItemPair(net.minecraft.item.Item log) {
            this.log = log;
        }
    }

    private static ItemStack itemIdStack(String itemId) {
        net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(itemId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (this.soil != null) {
            tag.setString("Soil", String.valueOf(Block.REGISTRY.getNameForObject(this.soil.getBlock())));
        }
        if (this.plant != null) {
            tag.setString("Plant", String.valueOf(Block.REGISTRY.getNameForObject(this.plant.getBlock())));
        }
        if (this.plantItemId != null) {
            tag.setString("PlantItem", this.plantItemId);
        }
        tag.setInteger("Growth", this.growth);
        tag.setInteger("RequiredGrowth", this.requiredGrowth);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Soil")) {
            Block soilBlock = Block.REGISTRY.getObject(new ResourceLocation(tag.getString("Soil")));
            this.soil = soilBlock == null ? null : soilBlock.getDefaultState();
        }
        if (tag.hasKey("Plant")) {
            Block plantBlock = Block.REGISTRY.getObject(new ResourceLocation(tag.getString("Plant")));
            this.plant = plantBlock == null ? null : plantBlock.getDefaultState();
        }
        this.plantItemId = tag.hasKey("PlantItem") ? tag.getString("PlantItem") : null;
        this.growth = Math.max(0, tag.getInteger("Growth"));
        this.requiredGrowth = Math.max(1, tag.hasKey("RequiredGrowth")
            ? tag.getInteger("RequiredGrowth") : GROWTH_TICKS);
    }
}
