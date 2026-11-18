package ua.empirecraft.interactionstest;

import com.magistuarmory.block.PaviseBlock;
import java.util.List;
import me.mss1r.axiomata.ballistics.damage.StructuralDamageSystem;
import net.dries007.tfc.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.interactions.Glassware;
import umpaz.farmersrespite.common.block.entity.KettleBlockEntity;

public final class ShieldGlasswareProbe {
    public static void run(ServerLevel level, ServerPlayer player) {
        pavise(level);
        glassware(level, player);
    }

    private static void pavise(ServerLevel level) {
        var pos = new BlockPos(44, 74, 44);
        var lower = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("magistuarmory", "pavise")).defaultBlockState();
        var upper = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("magistuarmory", "pavise_upper_collision")).defaultBlockState();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        for (int rotation = 0; rotation < 16; rotation++) {
            var state = lower.setValue(PaviseBlock.ROTATION, rotation);
            level.setBlockAndUpdate(pos, state);
            level.setBlockAndUpdate(pos.above(), upper);
            var body = state.getCollisionShape(level, pos);
            var top = upper.getCollisionShape(level, pos.above());
            check(!body.isEmpty() && !top.isEmpty() && top.max(Direction.Axis.Y) < 0.75,
                "Thin two-part outline at rotation " + rotation);
            check(body.toAabbs().size() > 2 && top.toAabbs().size() > 2, "Leaning surface at rotation " + rotation);
        }
        var state = lower.setValue(PaviseBlock.ROTATION, 2);
        level.setBlockAndUpdate(pos, state);
        var shape = state.getCollisionShape(level, pos);
        var cornerStart = Vec3.atLowerCornerOf(pos).add(0.88, 0.5, 0.86);
        var cornerEnd = cornerStart.add(0.08, 0, 0.08);
        check(shape.clip(cornerStart, cornerEnd, pos) == null, "Diagonal shield leaves the opposite corner clear");
        var start = Vec3.atLowerCornerOf(pos.above()).add(.5, .2, -1);
        check(upper.getCollisionShape(level, pos.above()).clip(start, start.add(0, 0, 2), pos.above()) != null,
            "Upper board catches a shot crossing its surface");
        StructuralDamageSystem.applyImpact(level, pos.above(), .3f, 1);
        StructuralDamageSystem.applyImpact(level, pos, .2f, 1);
        check(Math.abs(StructuralDamageSystem.progress(level, pos, state) - .5f) < .001
            && Math.abs(StructuralDamageSystem.progress(level, pos.above(), upper) - .5f) < .001,
            "Upper and lower Siegeworks hits accumulate on one board");
    }

    private static void glassware(ServerLevel level, ServerPlayer player) {
        var pos = new BlockPos(42, 74, 44);
        var kettleBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("farmersrespite", "kettle"));
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, kettleBlock.defaultBlockState());
        var kettle = (KettleBlockEntity) level.getBlockEntity(pos);
        var fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation("farmersrespite", "green_tea"));
        var tank = kettle.getFluidTank();
        for (var glass : List.of("volcanic", "silica", "hematitic", "olivine")) {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfc", glass + "_glass_bottle"));
            var bottles = new ItemStack(item, 2);
            bottles.getOrCreateTag().putString("GlasswareProof", glass);
            tank.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
            tank.fill(new FluidStack(fluid, 750), FluidAction.EXECUTE);
            var full = kettle.fluidExtract(kettle, bottles, ItemStack.EMPTY);
            check(full.is(item) && bottles.getCount() == 1 && tank.getFluidAmount() == 500
                && Glassware.contents(full).getAmount() == 250, "Kettle bottles one exact serving in " + glass);
            check(kettle.fluidExtract(kettle, bottles, full).isEmpty() && bottles.getCount() == 1
                && tank.getFluidAmount() == 500, "Full output keeps the other bottle and the rest of the tea");
            var reloaded = ItemStack.of(full.save(new net.minecraft.nbt.CompoundTag()));
            player.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
            check(reloaded.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction()
                && player.isUsingItem(), "Filled TFC bottle starts actually drinking");
            var empty = reloaded.finishUsingItem(level, player);
            player.stopUsingItem();
            check(empty.is(item) && Glassware.contents(empty).isEmpty()
                && glass.equals(empty.getTag().getString("GlasswareProof")), "Drinking returns the exact " + glass + " bottle");
            check(player.getInventory().countItem(Items.GLASS_BOTTLE) == 0, "No duplicate vanilla bottle");
            var water = new ItemStack(item);
            water.getOrCreateTag().putString("GlasswareProof", glass);
            water.getCapability(Capabilities.FLUID_ITEM).orElseThrow(AssertionError::new)
                .fill(new FluidStack(Fluids.WATER, 173), FluidAction.EXECUTE);
            var manager = level.getRecipeManager();
            check(manager.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(empty), level).isEmpty(),
                "Empty bottle cannot be boiled");
            var boiled = manager.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(water), level).orElseThrow()
                .assemble(new SimpleContainer(water), level.registryAccess());
            check(boiled.is(item) && Glassware.isBoiled(Glassware.contents(boiled))
                && Glassware.contents(boiled).getAmount() == 173 && glass.equals(boiled.getTag().getString("GlasswareProof")),
                "Boiling preserves the actual bottle, amount and metadata");
            check(manager.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(boiled), level).isEmpty(),
                "Purified water cannot be boiled repeatedly");
            var remaining = boiled.finishUsingItem(level, player);
            check(Glassware.contents(remaining).getAmount() == 73 && Glassware.isBoiled(Glassware.contents(remaining)),
                "Partly drunk clean water stays clean");
            check(remaining.finishUsingItem(level, player).is(item) && Glassware.contents(remaining).isEmpty(),
                "Water leaves an empty " + glass + " bottle");
            if (glass.equals("volcanic")) furnace(level, pos.west(3), water);
        }
    }

    private static void furnace(ServerLevel level, BlockPos pos, ItemStack input) {
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        var block = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
        var glass = input.getItem();
        block.setItem(0, input);
        block.setItem(1, new ItemStack(Items.CHARCOAL));
        for (int tick = 0; tick < 240; tick++) {
            AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), block);
        }
        var output = block.getItem(2);
        check(block.getItem(0).isEmpty() && output.is(glass)
            && Glassware.isBoiled(Glassware.contents(output)), "The real furnace produces purified water in the same glass");
    }

    private static void check(boolean success, String label) {
        if (!success) throw new AssertionError(label);
    }
}
