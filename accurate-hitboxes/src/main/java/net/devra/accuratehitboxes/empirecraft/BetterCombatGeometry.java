package net.devra.accuratehitboxes.empirecraft;

import java.util.*;
import net.bettercombat.client.collision.OrientedBoundingBox;
import net.bettercombat.BetterCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Client attack geometry; original Better Combat damage, relations and combos stay in charge. */
public final class BetterCombatGeometry {
    private record Contact(ModelBox weapon,List<ModelBox> parts) {}
    private static final Map<Entity,Contact> CONTACTS=new IdentityHashMap<>();
    private static Player attacker;
    private BetterCombatGeometry(){}
    public static void begin(Player player){clear();attacker=player;}
    public static void clear(){CONTACTS.clear();attacker=null;}
    public static Boolean collides(OrientedBoundingBox weapon,Entity entity){
        var boxes=WhaleCandidates.clientParts(entity);if(boxes.isEmpty())return null;
        var shape=new ModelBox(v(weapon.center),v(weapon.axisX).mul(weapon.extent.x),
                v(weapon.axisY).mul(weapon.extent.y),v(weapon.axisZ).mul(weapon.extent.z));
        List<ModelBox> parts=new ArrayList<>();
        for(var box:boxes)try{
            var part=WhaleCandidates.model(box);if(shape.intersects(part))parts.add(part);
        }catch(IllegalArgumentException invalid){/* Zero-scale/non-finite render parts cannot be struck. */}
        CONTACTS.put(entity,new Contact(shape,parts));return !parts.isEmpty();
    }
    public static Boolean radial(Entity entity,Vec3 origin,Vec3 direction,double range,double angle){
        var contact=CONTACTS.get(entity);if(contact==null)return null;
        if(attacker==null||!Double.isFinite(range)||range<0||!Double.isFinite(angle))return false;
        Vector3d start=v(origin),forward=v(direction).normalize();
        double cosine=Math.cos(Math.toRadians(Math.max(0,Math.min(360,angle))*.5));
        for(var part:contact.parts())for(var point:part.contacts(contact.weapon(),start)){
            Vector3d delta=point.sub(start,new Vector3d());double distance=delta.length();
            if(distance>range+1e-6)continue;
            if(angle!=0 && distance>1e-9 && delta.dot(forward)/distance<cosine-1e-6)continue;
            if(BetterCombat.config.allow_attacking_thru_walls || WhaleCandidates.clear(entity.level(),attacker,origin,new Vec3(point.x,point.y,point.z)))return true;
        }
        return false;
    }
    private static Vector3d v(Vec3 p){return new Vector3d(p.x,p.y,p.z);}
}
