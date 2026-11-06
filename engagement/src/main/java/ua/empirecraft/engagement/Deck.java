package ua.empirecraft.engagement;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Physical support, not a crew roster or a loose hull AABB, associates people with a vessel. */
public final class Deck {
    public record Contact(LoadedServerShip ship,Vec3 local,long tick){}
    private record Checked(long tick,LoadedServerShip ship,boolean ground){}
    private static final Map<UUID,Checked> checked=new HashMap<>();
    private static final Map<UUID,Contact> last=new HashMap<>();
    private Deck(){}
    public static LoadedServerShip support(Entity entity){
        if(!(entity.level() instanceof ServerLevel level))return null;long now=level.getServer().overworld().getGameTime();var known=checked.get(entity.getUUID());if(known!=null&&known.tick==now)return known.ship;
        Entity feet=entity.getRootVehicle();LoadedServerShip aboard=null;boolean ground=false;
        if(feet!=entity){var managed=VSGameUtilsKt.getLoadedShipManagingPos(level,feet.getX(),feet.getY(),feet.getZ());if(managed!=null)aboard=managed;}
        if(aboard==null){
            double width=Math.min(.5,feet.getBbWidth()*.3);double[][] samples={{0,0},{width,width},{width,-width},{-width,width},{-width,-width}};
            for(var offset:samples){Vec3 p=feet.position().add(offset[0],.2,offset[1]);var hit=CombatRaycast.first(level,feet,p,p.add(0,-.85,0),true,state->false);
                if(hit!=null&&hit.state()!=null&&hit.normal().y>.35){if(hit.ship() instanceof LoadedServerShip loaded){aboard=loaded;break;}ground=true;}
            }
        }
        checked.put(entity.getUUID(),new Checked(now,aboard,ground));if(ground&&aboard==null)last.remove(entity.getUUID());
        return aboard;
    }
    public static Contact previous(Entity entity){return last.get(entity.getUUID());}
    public static void remember(Entity entity,LoadedServerShip ship){
        long now=((ServerLevel)entity.level()).getServer().overworld().getGameTime();last.put(entity.getUUID(),new Contact(ship,CombatRaycast.localPoint(ship,entity.position()),now));
    }
    public static LoadedServerShip source(Entity entity){
        var current=support(entity);if(current!=null&&Combatants.crew(entity,current))return current;
        var previous=last.get(entity.getUUID());if(previous==null||!(entity.level() instanceof ServerLevel level))return null;
        long age=level.getServer().overworld().getGameTime()-previous.tick;
        if(age<0||age>(current==null?10:200)||!Combatants.crew(entity,previous.ship))return null;
        var bounds=previous.ship.getWorldAABB();if(bounds==null)return null;
        Vec3 point=entity.position();double dx=Math.max(Math.max(bounds.minX()-point.x,0),point.x-bounds.maxX()),dy=Math.max(Math.max(bounds.minY()-point.y,0),point.y-bounds.maxY()),dz=Math.max(Math.max(bounds.minZ()-point.z,0),point.z-bounds.maxZ());
        return dx*dx+dy*dy+dz*dz<=(current==null?4:1024)?previous.ship:null;
    }
    public static void rejectBoarding(Entity entity,Contact contact){
        if(contact==null||!(entity.level() instanceof ServerLevel level))return;Vec3 p=CombatRaycast.worldPoint(contact.ship,contact.local);
        if(!Double.isFinite(p.lengthSqr())||entity.position().distanceToSqr(p)>64*64||!level.hasChunkAt(net.minecraft.core.BlockPos.containing(p)))return;
        entity.stopRiding();if(entity instanceof net.minecraft.server.level.ServerPlayer player)player.teleportTo(level,p.x,p.y,p.z,player.getYRot(),player.getXRot());else entity.teleportTo(p.x,p.y,p.z);
    }
    public static void forget(UUID entity){checked.remove(entity);last.remove(entity);}
    public static void clear(){checked.clear();last.clear();}
}
