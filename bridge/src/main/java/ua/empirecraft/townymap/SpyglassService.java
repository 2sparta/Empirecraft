package ua.empirecraft.townymap;

import com.google.gson.Gson;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Nearby contacts are ephemeral. Only an authenticated L press writes the personal optical log. */
final class SpyglassService {
    private static final Gson JSON=new Gson();
    private record Details(String name,String country,String town,String mode,String flag,long at) {}
    private static final class Session {
        ShipObservation.Log log;long retry,lastSave=Long.MIN_VALUE/2,focus=-1;boolean dirty,sent;long imageWindow;int imageRequests;
        int infoBudget;final LinkedHashMap<Long,Details> details=new LinkedHashMap<>(32,.75f,true);
        final Set<String> icons=new HashSet<>();
    }
    private MinecraftServer server;
    private ShipObservationStore store;
    private ShipIconAtlas atlas;
    private long ticks;
    private final Map<UUID,Session> sessions=new HashMap<>();
    static int range(ServerPlayer player){return ShipViewDistance.blocks(player); }
    static boolean nearby(ServerPlayer player,Ship ship){
        var b=ship.getWorldAABB();double r=range(player),x=player.getX(),z=player.getZ();
        return b!=null&&b.maxX()>=x-r&&b.minX()<=x+r&&b.maxZ()>=z-r&&b.minZ()<=z+r;
    }
    static Long target(ServerPlayer player){
        if(!player.isUsingItem()||!player.getUseItem().is(Items.SPYGLASS))return null;
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getViewVector(1).scale(range(player)));
        var hit=ShipSight.first(player.serverLevel(),player,eye,end,true,state->false);
        return hit==null||hit.state()==null||hit.ship()==null?null:hit.ship().getId();
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var current=ServerLifecycleHooks.getCurrentServer();if(current==null)return;
        if(server==null){server=current;paths(current);}
        if(server!=current)return;ticks++;atlas.tick(server);if(ticks%10!=0)return;
        for(var player:server.getPlayerList().getPlayers()){
            if(!StrategyNetwork.present(player))continue;
            var session=sessions.computeIfAbsent(player.getUUID(),id->new Session());
            if(session.log==null&&ticks>=session.retry)try{session.log=store.load(player.getUUID());}
            catch(java.io.IOException error){session.retry=ticks+1200;EmpireTownyMap.LOG.warn("Ship history unavailable; preserving its file",error);}
            session.infoBudget=4;var contacts=new ArrayList<ShipObservation>();long focus=-1;
            try{
                var ships=new ArrayList<Ship>();
                for(var ship:VSGameUtilsKt.getShipObjectWorld(player.serverLevel()).getLoadedShips())
                    if(ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(player.serverLevel()))&&nearby(player,ship))ships.add(ship);
                ships.sort(Comparator.comparingDouble(s->s.getTransform().getPositionInWorld().distanceSquared(player.getX(),player.getY(),player.getZ())));
                Long aimed=target(player);focus=aimed==null?-1:aimed;session.focus=focus;
                if(aimed!=null){var ship=VSGameUtilsKt.getShipObjectWorld(player.serverLevel()).getLoadedShips().getById(aimed);if(ship!=null)contacts.add(snapshot(player,ship));}
                for(var ship:ships){if(contacts.size()>=128)break;if(ship.getId()!=focus)contacts.add(snapshot(player,ship));}
            }catch(Exception|LinkageError unavailable){if(ticks%1200==0)EmpireTownyMap.LOG.warn("Ship contacts waiting for fleet APIs",unavailable);}
            StrategyNetwork.contacts(player,contacts,focus);
            session.focus=focus;
            if(session.log!=null){
                if(!session.sent){StrategyNetwork.send(player,JSON.toJson(session.log.snapshot()),focus);session.sent=true;}
                var all=new ArrayList<>(contacts);all.addAll(session.log.snapshot());int sent=0;
                for(var row:all){if(sent>=4)break;var icon=atlas.image(row.ship());if(icon!=null&&session.icons.add(row.ship()+"/"+icon.key())){StrategyNetwork.icon(player,row.ship(),icon);sent++;}}
                if(session.icons.size()>512)session.icons.clear();
                if(ticks%600==0&&session.dirty){store.save(player.getUUID(),session.log.snapshot());session.dirty=false;}
            }
        }
        if(ticks%600==0)store.retry();
    }
    private void paths(MinecraftServer current){var root=current.getWorldPath(LevelResource.ROOT).resolve("empirecraft");
        store=new ShipObservationStore(root.resolve("ship-observations"),error->EmpireTownyMap.LOG.error("Could not save ship observations; retaining data for retry",error));atlas=new ShipIconAtlas(root.resolve("ship-icons"));}
    void requestIcon(ServerPlayer player,long id){
        var session=sessions.get(player.getUUID());if(server!=player.getServer()||session==null)return;
        long window=ticks/20;if(session.imageWindow!=window){session.imageWindow=window;session.imageRequests=0;}
        if(session.imageRequests++>=4)return;
        boolean saved=session.log!=null&&session.log.get(id)!=null;
        var ship=VSGameUtilsKt.getShipObjectWorld(player.serverLevel()).getLoadedShips().getById(id);
        boolean local=ship!=null&&ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(player.serverLevel()))&&nearby(player,ship);
        if(!saved&&!local)return;var image=atlas.image(id);if(image!=null)StrategyNetwork.icon(player,id,image);
    }
    void save(ServerPlayer player,long expected){
        if(server!=player.getServer())return;var session=sessions.get(player.getUUID());
        if(session==null||session.log==null){tell(player,"Журнал спостережень ще недоступний");return;}
        if(ticks-session.lastSave<10)return;session.lastSave=ticks;
        Long aimed=target(player);if(aimed==null||aimed!=expected){tell(player,"Наведіть підзорну трубу на корабель");return;}
        var ship=VSGameUtilsKt.getShipObjectWorld(player.serverLevel()).getLoadedShips().getById(aimed);
        if(ship==null||!nearby(player,ship))return;
        var row=snapshot(player,ship,true);session.log.observe(row);session.dirty=true;
        store.save(player.getUUID(),session.log.snapshot());session.dirty=false;
        StrategyNetwork.send(player,JSON.toJson(session.log.snapshot()),aimed);tell(player,"Позицію корабля, швидкість і курс збережено на мапі");
    }
    private static void tell(ServerPlayer player,String message){player.displayClientMessage(net.minecraft.network.chat.Component.literal(message),true);}
    private ShipObservation snapshot(ServerPlayer player,Ship ship){return snapshot(player,ship,false);}
    private ShipObservation snapshot(ServerPlayer player,Ship ship,boolean saving){
        var info=details(player,ship.getId(),saving);
        var box=ship.getShipAABB();Vec3 center=box==null?new Vec3(ship.getTransform().getPositionInWorld().x(),ship.getTransform().getPositionInWorld().y(),ship.getTransform().getPositionInWorld().z()):
                ShipSight.worldPoint(ship,new Vec3(((double)box.minX()+box.maxX())/2,box.minY()+.5,((double)box.minZ()+box.maxZ())/2));
        var v=ship.getVelocity();double speed=Math.hypot(v.x(),v.z());double heading=speed>.05?Math.toDegrees(Math.atan2(v.x(),-v.z())):0;
        double yaw=-Math.toDegrees(Math.atan2(ship.getShipToWorld().m20(),ship.getShipToWorld().m22()));
        var icon=atlas.image(ship.getId());
        return new ShipObservation(ship.getId(),player.serverLevel().dimension().location().toString(),center.x,center.y,center.z,heading,speed,System.currentTimeMillis(),
                info.name(),info.country(),info.town(),info.mode(),saving?info.flag():"{}",yaw,icon==null?"":icon.key());
    }
    @SuppressWarnings("unchecked") private Details details(ServerPlayer player,long id,boolean saving){
        var session=sessions.get(player.getUUID());var cached=session.details.get(id);long age=id==session.focus?20:200;
        if(!saving&&cached!=null&&ticks-cached.at()<age)return cached;
        if(!saving&&session.infoBudget<=0)return cached==null?new Details("Корабель #"+id,"Без держави","Без міста","NONE","{}",ticks):cached;
        if(!saving)session.infoBudget--;Map<String,Object> info=Map.of();
        try{Object plugin=CurrencyNetwork.plugin();if(plugin!=null)info=(Map<String,Object>)plugin.getClass().getMethod("mapShipInfo",UUID.class,long.class).invoke(plugin,player.getUUID(),id);}
        catch(ReflectiveOperationException|LinkageError unavailable){/* Anonymous portraits work without the optional civic plugin. */}
        String flag=JSON.toJson(info.getOrDefault("flag",Map.of()));if(flag.length()>16384)flag="{}";
        var result=new Details(text(info.getOrDefault("name","Корабель #"+id)),text(info.getOrDefault("country","Без держави")),text(info.getOrDefault("town","Без міста")),text(info.getOrDefault("flagMode","NONE")),flag,ticks);
        session.details.put(id,result);while(session.details.size()>256)session.details.remove(session.details.keySet().iterator().next());return result;
    }
    private static String text(Object value){String s=Objects.toString(value,"");return s.substring(0,Math.min(256,s.length()));}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){var id=event.getEntity().getUUID();ShipViewDistance.forget(id);var session=sessions.remove(id);if(store!=null&&session!=null&&session.log!=null&&session.dirty)store.save(id,session.log.snapshot());}
    @SubscribeEvent public void stop(ServerStoppingEvent event){
        if(server!=event.getServer())return;sessions.forEach((id,s)->{if(s.log!=null&&s.dirty)store.save(id,s.log.snapshot());});
        try{store.close();}catch(java.io.IOException e){EmpireTownyMap.LOG.error("Ship history could not be completely saved",e);}
        try{atlas.close();}catch(java.io.IOException e){EmpireTownyMap.LOG.error("Ship images could not be completely saved",e);}
        sessions.clear();ShipViewDistance.clear();server=null;store=null;atlas=null;ticks=0;
    }
}
