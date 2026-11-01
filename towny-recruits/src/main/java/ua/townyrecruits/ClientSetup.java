package ua.townyrecruits;
import com.talhanation.recruits.config.RecruitsClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
@Mod.EventBusSubscriber(modid="townyrecruits",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{RecruitsClientConfig.RecruitsLookLikeVillagers.set(false);RecruitsClientConfig.PlayVillagerAmbientSound.set(false);});}
}
