package dev.compressedblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceMenu;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * 四阶压缩熔炉方块实体：
 * 1重（1x）：3x 速度，权限原版 + 1~3 重；
 * 2重（2x）：9x 速度，权限原版 + 1~6 重；
 * 3重（3x）：27x 速度，权限原版 + 1~9 重；
 * 4重（4x）：81x 极致速度，全通烧炼。
 *
 * 燃料消耗与收益模型：
 * 燃烧时间按真实秒数（tick 数）结算，不惩罚性缩减燃料寿命，产出效率等比提升；
 * 空转保护：无待烧物时降速为 1x 消耗，不提前引燃新燃料，防止燃料浪费。
 */
public class CompressedFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
    private final int tier;
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> quickCheck =
        RecipeManager.createCheck(RecipeType.SMELTING);

    public CompressedFurnaceBlockEntity(BlockPos pos, BlockState state, int tier) {
        super(CompressedBlocks.FURNACE_TYPE[tier - 1], pos, state, RecipeType.SMELTING);
        this.tier = tier;
    }

    public int tier() {
        return this.tier;
    }

    public int getSpeed() {
        return switch (this.tier) {
            case 1 -> 3;
            case 2 -> 9;
            case 3 -> 27;
            case 4 -> 81;
            default -> 1;
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

    public boolean isItemAllowed(ItemStack stack) {
        int level = levelOfItem(stack);
        int maxAllowed = switch (this.tier) {
            case 1 -> 3;
            case 2 -> 6;
            case 3 -> 9;
            default -> Integer.MAX_VALUE;
        };
        return level <= maxAllowed;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.compressedblocks." + CompressedBlocks.LEVEL_PREFIX[this.tier - 1] + "_compressed_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return new FurnaceMenu(id, playerInventory, this, this.dataAccess);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == 2) {
            return false;
        }
        if (slot == 1) {
            return (this.level != null && this.level.fuelValues().isFuel(stack)) || stack.is(Items.BUCKET);
        }
        return isItemAllowed(stack);
    }

    public boolean isLit() {
        return this.dataAccess.get(0) > 0;
    }

    private boolean canBurn(RegistryAccess registryAccess, RecipeHolder<SmeltingRecipe> recipe, ItemStack input) {
        if (input.isEmpty() || !isItemAllowed(input) || recipe == null) {
            return false;
        }
        ItemStack result = recipe.value().assemble(new SingleRecipeInput(input), registryAccess);
        if (result.isEmpty()) {
            return false;
        }
        ItemStack resultSlot = this.items.get(2);
        if (resultSlot.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(resultSlot, result)) {
            return false;
        }
        return resultSlot.getCount() + result.getCount() <= Math.min(this.getMaxStackSize(), resultSlot.getMaxStackSize());
    }

    private boolean burn(RegistryAccess registryAccess, RecipeHolder<SmeltingRecipe> recipe, ItemStack input) {
        if (recipe == null || !canBurn(registryAccess, recipe, input)) {
            return false;
        }
        ItemStack result = recipe.value().assemble(new SingleRecipeInput(input), registryAccess);
        ItemStack resultSlot = this.items.get(2);
        if (resultSlot.isEmpty()) {
            this.items.set(2, result.copy());
        } else if (ItemStack.isSameItemSameComponents(resultSlot, result)) {
            resultSlot.grow(result.getCount());
        }
        input.shrink(1);
        return true;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, CompressedFurnaceBlockEntity be) {
        boolean wasLit = be.isLit();
        boolean changed = false;

        // 燃料时间自然按真实 tick 递减（1x 速度扣减，使玩家获得完整的提速收益与空转保护）
        if (be.isLit()) {
            int litTime = be.dataAccess.get(0);
            be.dataAccess.set(0, litTime - 1);
        }

        ItemStack input = be.items.get(0);
        ItemStack fuel = be.items.get(1);
        boolean hasInput = !input.isEmpty() && be.isItemAllowed(input);
        boolean hasFuel = !fuel.isEmpty();

        RecipeHolder<SmeltingRecipe> recipe = null;
        if (hasInput) {
            recipe = be.quickCheck.getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
        }
        boolean canBurn = be.canBurn(level.registryAccess(), recipe, input);

        if (!be.isLit()) {
            if (hasFuel && canBurn) {
                int duration = be.getBurnDuration(level.fuelValues(), fuel);
                if (duration > 0) {
                    be.dataAccess.set(0, duration);
                    be.dataAccess.set(1, duration);
                    changed = true;
                    Item fuelItem = fuel.getItem();
                    fuel.shrink(1);
                    if (fuel.isEmpty()) {
                        be.items.set(1, fuelItem.getCraftingRemainder());
                    }
                }
            }
        }

        int speed = be.getSpeed();
        int cookTimer = be.dataAccess.get(2);
        int recipeTotal = recipe != null ? recipe.value().cookingTime() : 200;
        be.dataAccess.set(3, recipeTotal);
        int cookTotal = recipeTotal;

        if (be.isLit() && canBurn) {
            cookTimer += speed;
            while (cookTimer >= cookTotal && canBurn) {
                cookTimer -= cookTotal;
                if (be.burn(level.registryAccess(), recipe, input)) {
                    be.setRecipeUsed(recipe);
                    changed = true;
                }
                input = be.items.get(0);
                if (input.isEmpty() || !be.isItemAllowed(input)) {
                    recipe = null;
                    canBurn = false;
                } else {
                    recipe = be.quickCheck.getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
                    canBurn = be.canBurn(level.registryAccess(), recipe, input);
                }
            }
            if (!canBurn && cookTimer > 0) {
                cookTimer = 0;
            }
            be.dataAccess.set(2, cookTimer);
        } else {
            // 空转保护 / 无待烧物：平滑降温
            if (cookTimer > 0) {
                cookTimer = Math.max(0, cookTimer - 2);
                be.dataAccess.set(2, cookTimer);
            }
        }

        boolean isLitNow = be.isLit();
        if (wasLit != isLitNow) {
            changed = true;
            state = state.setValue(AbstractFurnaceBlock.LIT, isLitNow);
            level.setBlock(pos, state, 3);
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }
}
