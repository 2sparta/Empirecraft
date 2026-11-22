package ua.empirecraft.smithingtest;

import com.otectus.immersivesmithing.recipe.SmithingData;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.armory.AssemblyRecipe;
import ua.empirecraft.armory.Armory;
import ua.empirecraft.armory.SmithingAssembly;
import ua.empirecraft.armory.SmithsAnvilWorkshop;
import ua.empirecraft.armory.WorkshopEntity;

@Mod("empirecraft_smithing_test")
@Mod.EventBusSubscriber(modid = "empirecraft_smithing_test")
public final class Probe {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        var server = event.getServer();
        server.execute(() -> {
            try {
                var level = server.overworld();
                require(!ModList.get().isLoaded("overgeared"), "Overgeared is absent");
                var steel = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfc:metal/ingot/steel"));
                require(steel != null && steel != Items.AIR, "TFC steel");
                var cast = level.getRecipeManager().byKey(new ResourceLocation("tfc:casting/steel_ingot")).orElseThrow();
                require(cast.getResultItem(level.registryAccess()).is(steel), "TFC steel casting");
                require(ForgeRegistries.ITEMS.getValue(new ResourceLocation("empirecraft_tfc:golden_sword_blade_blank")) != Items.AIR,
                        "Gold mold output survives removing Overgeared");
                var steelPickaxe = new ResourceLocation("empirecraft_tfc:smithing_finished/steel_pickaxe");
                require(level.getRecipeManager().byKey(steelPickaxe).isPresent(), "Steel pickaxe assembly");
                require(com.otectus.immersivesmithing.api.ImmersiveSmithingAPI.meltVeto(new ItemStack(Items.RAW_IRON)).isPresent(),
                        "Raw iron still requires TFC smelting");
                SmithingData.rebuild(server);
                var smithing = SmithingData.current();
                long parts = smithing.recipes().stream().filter(r -> r.resultItem() instanceof ua.empirecraft.armory.PartItem).count();
                require(parts == Armory.METALS.length * Armory.FORMS.size(), "Forge component count " + parts);
                require(smithing.recipes().stream().noneMatch(r -> AssemblyRecipe.staged(r.result())), "Direct smithing cannot bypass assembly");
                var recipes = AssemblyRecipe.all(level);
                require(recipes.size() >= 100, "Assembly catalog " + recipes.size());
                for (String namespace : Set.of("magistuarmory", "thirtyyearswar", "hamzabus_ottoman_mod", "chineseweapons")) {
                    require(recipes.stream().anyMatch(r -> ForgeRegistries.ITEMS.getKey(r.result.getItem()).getNamespace().equals(namespace)), namespace + " armor");
                }
                var chosen = recipes.stream().filter(r -> r.result.is(Items.IRON_CHESTPLATE)).findFirst().orElseThrow();
                BlockPos pos = level.getSharedSpawnPos().offset(2, 4, 2);
                level.setBlockAndUpdate(pos, SmithingAssembly.anvil().defaultBlockState());
                var host = level.getBlockEntity(pos);
                require(host instanceof SmithsAnvilWorkshop, "Smith's Anvil wiring");
                var workshop = ((SmithsAnvilWorkshop) host).empirecraft$workshop();
                require(workshop.validStation() && workshop.select(recipes.indexOf(chosen)), "Select assembly at the anvil");
                var smith = FakePlayerFactory.getMinecraft(level);
                for (int phase = 0; phase < 4; phase++) {
                    int offset = 0;
                    for (var ingredient : chosen.stage(phase)) {
                        var item = Arrays.stream(ingredient.getItems()).findFirst().orElseThrow();
                        workshop.inventory.setStackInSlot(WorkshopEntity.FIRST + offset++, item.copyWithCount(1));
                    }
                    require(workshop.perform(0, smith), "Assembly phase " + (phase + 1));
                    if (phase == 0) {
                        CompoundTag serialized = host.saveWithoutMetadata();
                        require(serialized.contains("EmpirecraftAssembly"), "Persistent anvil inventory");
                        host.load(serialized);
                        require(workshop.currentStage() == 1, "Reload workpiece");
                    }
                }
                require(workshop.inventory.getStackInSlot(WorkshopEntity.OUTPUT).is(Items.IRON_CHESTPLATE), "Finished armor");
                require(!host.getUpdateTag().contains("EmpirecraftAssembly"), "Inventory never sent in block update");
                var retired = new WorkshopEntity(pos, level.getBlockState(pos));
                retired.setLevel(level);
                require(!retired.select(recipes.indexOf(chosen)) && !retired.perform(0, smith), "Legacy table cannot assemble");
                System.out.println("IMMERSIVE_SMITHING_PASS parts=" + parts + " assembly=" + recipes.size() + " phases=4");
            } catch (Throwable error) {
                System.out.println("IMMERSIVE_SMITHING_FAIL " + error);
                error.printStackTrace();
            } finally {
                server.halt(false);
            }
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
