package ua.townyrecruits.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.townyrecruits.map.ArmyMapData;

/** Contains no JourneyMap classes, so the mod also works on clients without the optional map. */
@Mod.EventBusSubscriber(modid="townyrecruits",value=Dist.CLIENT)
public final class ArmyMapClient {
    public interface Layer { void update(ArmyMapData.Snapshot snapshot); void clear(); void reload(); }
    private static final ArmyMapData.View VIEW=new ArmyMapData.View();
    private static Layer layer;private static Connection connection;private static ResourceLocation dimension;
    private static ArmyMapData.Snapshot applied;private static long ticks;
    private ArmyMapClient() {}
    public static void bind(Layer next){if(layer!=null)layer.clear();layer=next;applied=null;}
    private static boolean context(Connection source) {
        var mc=Minecraft.m_91087_();
        if(mc.f_91073_==null||mc.m_91403_()==null||mc.m_91403_().m_104910_()!=source)return false;
        var dim=mc.f_91073_.m_46472_().m_135782_();
        if(connection!=source||!dim.equals(dimension)){clear();connection=source;dimension=dim;}
        return true;
    }
    public static void receive(ArmyMapData.Snapshot snapshot,Connection source){if(context(source))VIEW.accept(snapshot,dimension,ticks);}
    public static void clear(){VIEW.clear();if(layer!=null)layer.clear();applied=null;connection=null;dimension=null;}
    public static void reload(){if(layer!=null)layer.reload();applied=null;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;ticks++;var mc=Minecraft.m_91087_();
        if(mc.m_91403_()==null||!context(mc.m_91403_().m_104910_())){if(connection!=null||applied!=null)clear();return;}
        var current=VIEW.current(dimension,ticks);
        if(layer!=null&&current!=applied){if(current==null)layer.clear();else layer.update(current);applied=current;}
    }
}
