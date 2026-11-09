package com.quintonc.vs_sails.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import java.util.*;

public final class ShipProtection {
    private ShipProtection() {}
    private static long nextWarning;
    public static LoadedServerShip ship(Level level,BlockPos pos) {
        return level instanceof ServerLevel server?VSGameUtilsKt.getShipObjectManagingPos(server,pos):null;
    }
    public static LoadedServerShip ship(Entity entity) {
        var found=ship(entity.level(),entity.blockPosition());if(found!=null)return found;
        if(!(entity.level() instanceof ServerLevel level))return null;
        // Entities on a deck can have world coordinates, while helm seats have shipyard coordinates.
        var point=entity.position();
        for(var body:VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if(!body.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(level)))continue;
            var bounds=body.getShipAABB();if(bounds==null)continue;
            var local=body.getTransform().getWorldToShip().transformPosition(new org.joml.Vector3d(point.x,point.y,point.z));
            if(local.x>=bounds.minX() && local.x<=bounds.maxX() && local.y>=bounds.minY()-.5 && local.y<=bounds.maxY()+2
                    && local.z>=bounds.minZ() && local.z<=bounds.maxZ())return body;
        }
        return null;
    }
    public static boolean allows(Player actor,Level level,BlockPos pos) {
        return !(level instanceof ServerLevel server) || allows(actor,server,ship(level,pos));
    }
    public static boolean allows(Player actor,ServerLevel level,LoadedServerShip ship) {
        if(ship==null || !ShipTowny.installed())return true;
        if(!level.getServer().isSameThread())return false;
        try {
            if(!ProtectionConfig.get().enabled)return true;
            var towny=new ShipTowny();var registry=ShipRegistry.get(level.getServer());var vessel=registry.ships.get(ship.getId());
            UUID owner=vessel==null?null:vessel.town;
            if(actor!=null && towny.permission(actor.getUUID(),ProtectionConfig.get().bypassPermission))return true;
            var policy=FleetNative.policy(level,ship.getId(),actor==null?null:actor.getUUID());
            // The canonical ledger already checks captain, crew and mayor/admiral delegation.
            // A second global permission vetoed those explicit grants for ordinary residents.
            if(policy!=null)return Boolean.TRUE.equals(policy.get("allow"));
            if(vessel!=null && vessel.managed)return false;
            if(!towny.protectedWaters(level,ship,owner))return true;
            return actor!=null && ShipAccess.allows(vessel,registry.fleets.get(owner),actor.getUUID(),
                    towny.playerTown(actor.getUUID()),towny.permission(actor.getUUID(),ProtectionConfig.get().controlPermission));
        }catch(ReflectiveOperationException | RuntimeException | LinkageError failed) {
            long now=System.currentTimeMillis();if(now>=nextWarning){nextWarning=now+30000;
                com.quintonc.vs_sails.ship.SailsShipControl.LOGGER.warn("Ship access denied while Towny is unavailable",failed);}
            return false;
        }
    }
    public static void denied(Player player) {player.displayClientMessage(Component.translatable("vs_sails.protection.denied"),true);}
    /** Called immediately after assembly, before the bottle is consumed. No pilot can acquire ownership. */
    public static void assembled(ServerLevel level,ServerShip ship,Player actor) {
        if(!ShipTowny.installed())return;
        try {
            String selected=FleetNative.assemblyTown(actor.getUUID());
            UUID town=selected==null?new ShipTowny().playerTown(actor.getUUID()):UUID.fromString(selected.replace("t:",""));var registry=ShipRegistry.get(level.getServer());
            var vessel=new ShipAccess.Vessel(town,actor.getUUID());vessel.managed=selected!=null;
            registry.ships.put(ship.getId(),vessel);registry.setDirty();FleetNative.registered(ship.getId(),actor.getUUID());
        }catch(ReflectiveOperationException | RuntimeException | LinkageError failed) {
            // Leave the ship unregistered and therefore quarantined in claims; never guess its owner.
            com.quintonc.vs_sails.ship.SailsShipControl.LOGGER.error("Ship {} needs explicit owner assignment",ship.getId(),failed);
        }
    }
    public static void inherit(ServerLevel level,long oldId,long newId) {
        var registry=ShipRegistry.get(level.getServer());var old=registry.ships.get(oldId);if(old==null)return;
        var copy=new ShipAccess.Vessel(old.town,old.founder);copy.grants.towns.addAll(old.grants.towns);copy.grants.players.addAll(old.grants.players);
        copy.name=old.name;copy.captain=old.captain;copy.managed=old.managed;
        registry.ships.put(newId,copy);registry.setDirty();
    }
}
