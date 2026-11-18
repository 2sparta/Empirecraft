package ua.empirecraft.workshop;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
@Mod.EventBusSubscriber(modid=Workshop.ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ChineseMigration {
    private static final Set<String> FAMILIES=Set.of("dagger_axe","glaive","guduo","sky_piercing_halberd","song_javelin","tang_dynasty_heng_saber");
    @SubscribeEvent public static void missing(MissingMappingsEvent event){
        for(var mapping:event.getMappings(ForgeRegistries.Keys.ITEMS,"chineseweapons")){
            String path=mapping.getKey().m_135815_();
            if(path.startsWith("diamond_")&&FAMILIES.contains(path.substring(8))){
                var target=ForgeRegistries.ITEMS.getValue(new ResourceLocation("chineseweapons","iron_"+path.substring(8)));if(target!=null)mapping.remap(target);
            }
        }
    }
}
