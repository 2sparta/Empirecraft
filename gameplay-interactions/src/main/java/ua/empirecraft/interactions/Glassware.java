package ua.empirecraft.interactions;

import java.util.Comparator;
import net.dries007.tfc.common.capabilities.Capabilities;
import net.dries007.tfc.common.items.GlassBottleItem;
import net.dries007.tfc.util.Drinkable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;
import umpaz.farmersrespite.common.crafting.KettlePouringRecipe;
import umpaz.farmersrespite.common.registry.FRRecipeTypes;

public final class Glassware {
    public static final TagKey<Fluid> BOTTLE_FLUIDS = TagKey.create(Registries.FLUID,
        new ResourceLocation("empirecraft_interactions", "glass_bottle_fluids"));
    private static final TagKey<Fluid> KETTLE_DRINKS = TagKey.create(Registries.FLUID,
        new ResourceLocation("empirecraft_interactions", "kettle_drinks"));
    private static final TagKey<Fluid> FRESH_WATER = TagKey.create(Registries.FLUID, new ResourceLocation("tfc", "any_fresh_water"));
    private static final String BOILED = "empirecraft_interactions:boiled";
    private static final ResourceLocation RAW_WATER = new ResourceLocation("legendarysurvivaloverhaul", "raw_water_bottle");
    private static final ResourceLocation CLEAN_WATER = new ResourceLocation("legendarysurvivaloverhaul", "purified_water_bottle");

    private Glassware() {}

    public static boolean bottle(ItemStack stack) {
        return stack.getItem() instanceof GlassBottleItem;
    }

    private static IFluidHandlerItem handler(ItemStack singleBottle) {
        return singleBottle.getCapability(Capabilities.FLUID_ITEM).orElseThrow(
            () -> new IllegalArgumentException("Expected a single TFC glass bottle"));
    }

    public static FluidStack contents(ItemStack stack) {
        if (!bottle(stack)) return FluidStack.EMPTY;
        return stack.getCapability(Capabilities.FLUID_ITEM).map(cap -> cap.getFluidInTank(0)).orElse(FluidStack.EMPTY);
    }

    public static boolean isKettleDrink(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().is(KETTLE_DRINKS);
    }

    public static boolean isFreshWater(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().is(FRESH_WATER);
    }

    public static boolean isBoiled(FluidStack fluid) {
        return isFreshWater(fluid) && fluid.hasTag() && fluid.getTag().getBoolean(BOILED);
    }

    private static KettlePouringRecipe serving(Level level, FluidStack fluid) {
        if (level == null || !isKettleDrink(fluid)) return null;
        return level.getRecipeManager().getAllRecipesFor(FRRecipeTypes.KETTLE_POURING.get()).stream()
            .filter(recipe -> recipe.getFluid().isSame(fluid.getFluid()) && !recipe.getOutput().isEmpty())
            .min(Comparator.comparing((KettlePouringRecipe recipe) -> !recipe.getContainer().is(Items.GLASS_BOTTLE))
                .thenComparing(KettlePouringRecipe::getId)).orElse(null);
    }

    public static ItemStack transfer(Level level, FluidTank tank, ItemStack input, ItemStack output) {
        if (level == null || level.isClientSide) return ItemStack.EMPTY;
        var bottle = input.copyWithCount(1);
        var cap = handler(bottle);
        var inside = cap.getFluidInTank(0);
        var available = tank.getFluid();
        if (inside.isEmpty()) {
            if (available.isEmpty()) return ItemStack.EMPTY;
            var serving = serving(level, available);
            if (serving == null && isKettleDrink(available)) return ItemStack.EMPTY;
            int amount = serving == null ? Math.min(available.getAmount(), cap.getTankCapacity(0)) : serving.getAmount();
            if (amount <= 0 || available.getAmount() < amount || cap.fill(new FluidStack(available, amount), FluidAction.EXECUTE) != amount
                || !fits(output, bottle)) return ItemStack.EMPTY;
            tank.drain(amount, FluidAction.EXECUTE);
        } else {
            // The kettle brews with fresh water; its recipes use Minecraft's water fluid.
            var poured = isFreshWater(inside) ? new FluidStack(Fluids.WATER, inside.getAmount(), inside.getTag()) : inside;
            if (tank.fill(poured, FluidAction.SIMULATE) != poured.getAmount()) return ItemStack.EMPTY;
            cap.drain(inside.getAmount(), FluidAction.EXECUTE);
            if (!fits(output, bottle)) return ItemStack.EMPTY;
            tank.fill(poured, FluidAction.EXECUTE);
        }
        input.shrink(1);
        return bottle;
    }

    private static boolean fits(ItemStack output, ItemStack result) {
        return output.isEmpty() || ItemHandlerHelper.canItemStacksStack(output, result)
            && output.getCount() < output.getMaxStackSize();
    }

    public static ItemStack boiledWater(ItemStack original) {
        var bottle = original.copyWithCount(1);
        if (!bottle(bottle)) return ItemStack.EMPTY;
        var cap = handler(bottle);
        var water = cap.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
        if (!isFreshWater(water)) return ItemStack.EMPTY;
        water.getOrCreateTag().putBoolean(BOILED, true);
        return cap.fill(water, FluidAction.EXECUTE) == water.getAmount() ? bottle : ItemStack.EMPTY;
    }

    public static ItemStack waterDisplay(ItemStack original) {
        var bottle = original.copyWithCount(1);
        if (!bottle(bottle)) return original;
        var cap = handler(bottle);
        cap.fill(new FluidStack(Fluids.WATER, cap.getTankCapacity(0)), FluidAction.EXECUTE);
        return bottle;
    }

    public static boolean drink(ItemStack bottle, Level level, LivingEntity consumer) {
        var fluid = contents(bottle);
        var serving = serving(level, fluid);
        boolean water = isFreshWater(fluid);
        if (serving == null && !water) return false;
        if (!level.isClientSide && consumer instanceof Player player) {
            var cap = handler(bottle);
            if (serving != null) {
                if (fluid.getAmount() < serving.getAmount()) return true;
                ItemStack drink = serving.getOutput().copyWithCount(1);
                applyThirst(player, drink);
                // Use one serving to preserve the original effects, food, stats and advancements.
                // Its return value is the ordinary cup. The player's held stack is still the TFC bottle.
                drink.finishUsingItem(level, player);
                if (!player.getAbilities().instabuild) cap.drain(serving.getAmount(), FluidAction.EXECUTE);
            } else {
                int amount = Math.min(GlassBottleItem.DRINK_AMOUNT, fluid.getAmount());
                var item = ForgeRegistries.ITEMS.getValue(isBoiled(fluid) ? CLEAN_WATER : RAW_WATER);
                if (item != null) applyThirst(player, new ItemStack(item));
                Drinkable drinkable = Drinkable.get(fluid.getFluid());
                if (drinkable != null) drinkable.onDrink(player, amount);
                if (!player.getAbilities().instabuild) cap.drain(amount, FluidAction.EXECUTE);
            }
        }
        return true;
    }

    public static boolean fullServing(Level level, FluidStack fluid) {
        var serving = serving(level, fluid);
        return serving != null && fluid.getAmount() >= serving.getAmount();
    }

    private static void applyThirst(Player player, ItemStack drink) {
        TemperatureUtil.applyConsumableTemperature(player, ForgeRegistries.ITEMS.getKey(drink.getItem()));
        ThirstUtil.takeDrink(player, drink);
    }
}
