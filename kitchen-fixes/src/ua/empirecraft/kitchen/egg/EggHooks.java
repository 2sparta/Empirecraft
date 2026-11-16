package ua.empirecraft.kitchen.egg;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegistryObject;
/** Retire TFC's fried egg before registration and migrate saved stacks through Forge. */
public final class EggHooks {
    private static RegistryObject<Item> egg;
    private EggHooks() { }
    public static RegistryObject<Item> alias() {
        if (egg==null) {
            egg=RegistryObject.create(new ResourceLocation("naturalist:cooked_egg"),ForgeRegistries.ITEMS);
            MinecraftForge.EVENT_BUS.addListener(EggHooks::missing);
        }
        return egg;
    }
    private static void missing(MissingMappingsEvent event) {
        for (var mapping:event.getMappings(ForgeRegistries.Keys.ITEMS,"tfc"))
            if (mapping.getKey().equals(new ResourceLocation("tfc:food/cooked_egg"))) mapping.remap(egg.get());
    }
}
