package ua.empirecraft.armory;

import com.otectus.immersivesmithing.api.event.ItemSmithedEvent;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import com.otectus.immersivesmithing.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Armory.ID)
public final class SmithingAssembly {
    private SmithingAssembly() {}

    public static Block anvil() {
        return ModBlocks.SMITHS_ANVIL.get();
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ImmersiveSmithingAPI.registerRecipeGate((player, recipe) -> {
                if (!(recipe.resultItem() instanceof PartItem part)) return Optional.empty();
                var pattern = Armory.PATTERNS.get(part.form).get();
                if (player.getInventory().contains(new ItemStack(pattern))) return Optional.empty();
                return Optional.of(Component.translatable("armory.smithing_pattern", pattern.getDescription()));
            });
            ImmersiveSmithingAPI.registerMeltVeto(stack -> {
                boolean unmelted = stack.getTags().anyMatch(tag -> {
                    var id = tag.location();
                    return id.getNamespace().equals("forge") && (id.getPath().equals("raw_materials")
                        || id.getPath().startsWith("raw_materials/") || id.getPath().startsWith("storage_blocks/raw_"));
                });
                return unmelted ? Optional.of(Component.translatable("armory.smelt_in_tfc")) : Optional.empty();
            });
        });
    }

    @SubscribeEvent
    public static void migrate(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, "overgeared")) {
            if (mapping.getKey().getPath().equals("steel_block")) {
                var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc:metal/block/steel"));
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) mapping.remap(block);
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, "overgeared")) {
            var target = switch (mapping.getKey().getPath()) {
                case "smithing_anvil", "stone_anvil" -> "immersive_smithing:smiths_anvil";
                case "smithing_hammer" -> "immersive_smithing:iron_smithing_hammer";
                case "copper_smithing_hammer" -> "immersive_smithing:stone_smithing_hammer";
                case "wooden_tongs" -> "immersive_smithing:stone_smithing_tongs";
                case "steel_tongs", "steel_tong", "iron_tongs" -> "immersive_smithing:iron_smithing_tongs";
                case "steel_ingot", "heated_steel_ingot" -> "tfc:metal/ingot/steel";
                case "crude_steel", "heated_crude_steel" -> "tfc:metal/ingot/pig_iron";
                case "steel_nugget" -> "empirecraft_armory:steel_nugget";
                case "steel_block" -> "tfc:metal/block/steel";
                case "steel_plate" -> "tfc:metal/sheet/steel";
                case "iron_plate" -> "create:iron_sheet";
                case "copper_plate" -> "create:copper_sheet";
                case "copper_nugget" -> "create:copper_nugget";
                case "stone_pickaxe_head" -> "empirecraft_tfc:stone_pickaxe_head";
                case "rock" -> "tfc:rock/loose/granite";
                case "iron_arrow", "steel_arrow" -> "minecraft:arrow";
                case "steel_axe", "steel_pickaxe", "steel_shovel", "steel_hoe", "copper_axe", "copper_pickaxe",
                     "copper_shovel", "copper_hoe", "copper_sword" -> {
                    String[] name = mapping.getKey().getPath().split("_", 2);
                    yield "tfc:metal/" + name[1] + "/" + name[0];
                }
                case "steel_sword" -> "magistuarmory:steel_shortsword";
                default -> "";
            };
            if (!target.isEmpty()) {
                var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(target));
                if (item != null && item != net.minecraft.world.item.Items.AIR) mapping.remap(item);
            }
        }
    }

    @SubscribeEvent
    public static void finished(ItemSmithedEvent event) {
        if (event.getItem().getItem() instanceof PartItem) {
            // The station only issues a part once its native shaping and quenching is complete.
            var tag = event.getItem().getOrCreateTag();
            tag.putString("ForgingQuality", Parts.QUALITY.get(event.getQuality().quality().ordinal()));
            tag.putInt(Parts.TREATMENT, 2);
        }
    }
}
