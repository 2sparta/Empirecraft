package ua.empirecraft.civic;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.LoggerFactory;

/** No network messages or client registration: catches originate in Starcatcher's server completion path. */
@Mod("empirecraft_civic")
public final class CivicEvents {
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger("EmpirecraftCivic");
    private static long ticks;private static Object plugin;private static Method deliver;
    public CivicEvents(){MinecraftForge.EVENT_BUS.addListener(CivicEvents::tick);}
    private static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||++ticks%100!=0)return;
        try{
            Object manager=Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);
            Object next=manager.getClass().getMethod("getPlugin",String.class).invoke(manager,"EmpirecraftFeudal");
            if(next==null||!Boolean.TRUE.equals(next.getClass().getMethod("isEnabled").invoke(next))){plugin=null;deliver=null;return;}
            String version=ModList.get().getModContainerById("starcatcher").orElseThrow().getModInfo().getVersion().toString();
            if(Boolean.TRUE.equals(next.getClass().getMethod("fishingBridgeReady",String.class).invoke(next,version))){deliver=next.getClass().getMethod("fishingCatch",UUID.class,UUID.class,String.class,long.class,double.class,double.class,double.class);plugin=next;}
        }catch(ClassNotFoundException ignored){}catch(ReflectiveOperationException error){if(ticks%1200==0)LOG.error("Cannot bind city fishing tournaments",error);}
    }
    public static void caught(Player player,Object fish,float percentile){
        if(!(player instanceof ServerPlayer serverPlayer)||plugin==null||deliver==null||!Float.isFinite(percentile)||percentile<0||percentile>100)return;
        try{
            Object info=fish.getClass().getMethod("catchInfo").invoke(fish);
            if(!info.getClass().getMethod("fishEntryType").invoke(info).toString().equals("FISH"))return;
            Class<?> attachments=Class.forName("com.wdiscute.starcatcher.registry.SCDataAttachments");
            Object attachment=attachments.getMethod("get",Entity.class,Supplier.class).invoke(null,player,attachments.getField("FISHING_BOB").get(null));
            if(Boolean.TRUE.equals(attachment.getClass().getMethod("isEmpty").invoke(attachment)))return;
            UUID id=(UUID)attachment.getClass().getMethod("getUuid").invoke(attachment);
            Entity bobber=serverPlayer.serverLevel().getEntity(id);
            if(!(bobber instanceof Projectile projectile)||projectile.getOwner()!=player||bobber.isRemoved()||!bobber.getClass().getName().equals("com.wdiscute.starcatcher.bobentity.FishingBobEntity")||bobber.getClass().getField("fpToFish").get(bobber)!=fish)return;
            Object dimensions=fish.getClass().getMethod("sizeWeight").invoke(fish);
            double grams=((Number)dimensions.getClass().getMethod("getWeightForPercentile",float.class).invoke(dimensions,percentile)).doubleValue();
            if(!Double.isFinite(grams)||grams<0||grams>10000000)return;
            Class<?> api=Class.forName("com.wdiscute.starcatcher.fish.FishApi");
            Object key=api.getMethod("getKey",Level.class,fish.getClass()).invoke(null,serverPlayer.level(),fish);
            if(key==null)return;
            deliver.invoke(plugin,player.getUUID(),id,key.toString(),Math.round(grams*1000),bobber.getX(),bobber.getY(),bobber.getZ());
        }catch(ReflectiveOperationException|RuntimeException error){LOG.error("City fishing catch could not be recorded for {}",player.getUUID(),error);}
    }
}
