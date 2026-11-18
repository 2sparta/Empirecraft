package ua.empirecraft.townymap;

import com.google.gson.Gson;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Optical reconnaissance is authenticated and ray-cast by the server, including moving VS hulls. */
final class SpyglassService {
    private static final Gson JSON=new Gson();
    private static final class Session {
        ShipObservation.Log log; long retry,focus=-1;boolean dirty,sent;int cursor;
    }
    private MinecraftServer server;
    private ShipObservationStore store;
    private long ticks;
    private final Map<UUID,Session> sessions=new HashMap<>();
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var current=ServerLifecycleHooks.getCurrentServer();if(current==null)return;
        if(server==null){server=current;store=new ShipObservationStore(current.getWorldPath(LevelResource.ROOT).resolve("empirecraft/ship-observations"),
                error->EmpireTownyMap.LOG.error("Could not save ship observations; retaining data for retry",error));}
        if(server!=current || ++ticks%10!=0)return;
        for(var player:server.getPlayerList().getPlayers()) {
            var session=sessions.computeIfAbsent(player.getUUID(),id->new Session());
            if(session.log==null && ticks>=session.retry)try{session.log=store.load(player.getUUID());}
            catch(java.io.IOException error){session.retry=ticks+1200;EmpireTownyMap.LOG.warn("Ship history unavailable; preserving its file",error);}
            if(session.log==null)continue;
            boolean changed=false;long focus=-1;
            try {
                boolean scoping=player.isUsingItem() && player.getUseItem().is(Items.SPYGLASS);
                if(scoping) {
                    Long ship=target(player);
                    if(ship!=null){focus=ship;changed|=observe(player,session,ship);}
                }
                // Once inspected, a ship can update while actually visible; never dead-reckon an unseen vessel.
                if(ticks%20==0 && !scoping) {
                    var known=session.log.snapshot();int count=Math.min(16,known.size());
                    for(int i=0;i<count;i++){var old=known.get((session.cursor+i)%known.size());
                        if(old.ship()!=focus)changed|=observe(player,session,old.ship());}
                    if(!known.isEmpty())session.cursor=(session.cursor+count)%known.size();
                }
            } catch(Exception | LinkageError unavailable) {
                if(ticks%1200==0)EmpireTownyMap.LOG.warn("Optical ship inspection waiting for native fleet APIs",unavailable);
            }
            session.dirty|=changed;
            if(changed || focus!=session.focus || !session.sent || ticks%200==0) {
                StrategyNetwork.send(player,JSON.toJson(session.log.snapshot()),focus);session.sent=true;session.focus=focus;
            }
            if(ticks%600==0 && session.dirty){store.save(player.getUUID(),session.log.snapshot());session.dirty=false;}
        }
        if(ticks%600==0)store.retry();
    }
    private static Long target(ServerPlayer player) {
        var level=player.serverLevel();var eye=player.getEyePosition();
        double range=Math.min(256,player.getServer().getPlayerList().getViewDistance()*16.0);
        var look=player.getViewVector(1);var end=eye.add(look.scale(range));
        for(int distance=0;distance<range;distance+=8)if(!level.hasChunkAt(BlockPos.containing(eye.add(look.scale(distance))))) {
            end=eye.add(look.scale(Math.max(0,distance-8)));break;
        }
        var hit=org.valkyrienskies.mod.common.world.RaycastUtilsKt.clipIncludeShips(level,
                new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()==HitResult.Type.MISS)return null;
        var ship=VSGameUtilsKt.getLoadedShipManagingPos(level,hit.getBlockPos());
        return ship==null?null:ship.getId();
    }
    @SuppressWarnings("unchecked") private boolean observe(ServerPlayer player,Session session,long id)throws Exception {
        var level=player.serverLevel();var ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);
        if(ship==null || !ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(level)))return false;
        Object plugin=CurrencyNetwork.plugin();if(plugin==null)return false;
        var info=(Map<String,Object>)plugin.getClass().getMethod("mapShipInfo",UUID.class,long.class).invoke(plugin,player.getUUID(),id);
        if(info.isEmpty())return false;
        var p=ship.getTransform().getPositionInWorld();var velocity=ship.getVelocity();
        double speed=Math.hypot(velocity.x(),velocity.z());var previous=session.log.get(id);
        double heading=speed>.05?Math.toDegrees(Math.atan2(velocity.x(),-velocity.z())):previous==null?0:previous.heading();
        return session.log.observe(new ShipObservation(id,level.dimension().location().toString(),p.x(),p.y(),p.z(),heading,speed,
                System.currentTimeMillis(),Objects.toString(info.get("name"),"Корабель"),Objects.toString(info.get("country"),"Без держави"),
                Objects.toString(info.get("town"),"Без міста"),Objects.toString(info.get("flagMode"),"NONE"),JSON.toJson(info.getOrDefault("flag",Map.of()))));
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var id=event.getEntity().getUUID();var session=sessions.remove(id);
        if(store!=null && session!=null && session.log!=null && session.dirty)store.save(id,session.log.snapshot());
    }
    @SubscribeEvent public void stop(ServerStoppingEvent event) {
        if(server!=event.getServer())return;
        sessions.forEach((id,session)->{if(session.log!=null&&session.dirty)store.save(id,session.log.snapshot());});
        try{store.close();}catch(java.io.IOException error){EmpireTownyMap.LOG.error("Ship histories could not be completely saved",error);}
        sessions.clear();server=null;store=null;ticks=0;
    }
}
