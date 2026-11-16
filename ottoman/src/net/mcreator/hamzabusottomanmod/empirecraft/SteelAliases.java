package net.mcreator.hamzabusottomanmod.empirecraft;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@Mod.EventBusSubscriber(modid="hamzabus_ottoman_mod")
public final class SteelAliases {
    public static RegistryObject<Item> register(DeferredRegister<Item> register,String name,Supplier<? extends Item> factory){
        if(name.equals("steel_ingot")||name.equals("steel_nugget"))return RegistryObject.create(new ResourceLocation("overgeared",name),ForgeRegistries.ITEMS);
        if(name.equals("ottoman_shield_blocking"))return RegistryObject.create(new ResourceLocation("hamzabus_ottoman_mod:ottoman_shield"),ForgeRegistries.ITEMS);
        return register.register(name,factory);
    }
    @SubscribeEvent public static void missing(MissingMappingsEvent event){for(var mapping:event.getMappings(ForgeRegistries.Keys.ITEMS,"hamzabus_ottoman_mod")){
        String key=mapping.getKey().getPath();if(key.equals("steel_ingot")||key.equals("steel_nugget"))mapping.remap(ForgeRegistries.ITEMS.getValue(new ResourceLocation("overgeared",key)));else if(key.equals("ottoman_shield_blocking"))mapping.remap(ForgeRegistries.ITEMS.getValue(new ResourceLocation("hamzabus_ottoman_mod:ottoman_shield")));
    }}
}
