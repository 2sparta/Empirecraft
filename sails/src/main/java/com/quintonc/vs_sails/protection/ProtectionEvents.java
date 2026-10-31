package com.quintonc.vs_sails.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="vs_sails")
public final class ProtectionEvents {
    private record OpenShip(long id,ServerLevel level) {}
    private static final Map<UUID,OpenShip> openShips=new HashMap<>();
    public static void track(Player player,BlockPos pos) {
        var ship=ShipProtection.ship(player.level(),pos);
        if(ship!=null && player.level() instanceof ServerLevel level)openShips.put(player.getUUID(),new OpenShip(ship.getId(),level));
    }
    public static boolean allowsOpen(ServerPlayer p) {
        var opened=openShips.get(p.getUUID());if(opened==null)return true;
        var ship=org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(opened.level).getLoadedShips().getById(opened.id);
        return p.serverLevel()==opened.level && ship!=null && ShipProtection.allows(p,opened.level,ship);
    }
    private static boolean deny(Player player,BlockPos pos) {
        if(ShipProtection.allows(player,player.level(),pos))return false;
        ShipProtection.denied(player);return true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.RightClickBlock e) {
        if(e.getLevel().isClientSide)return;
        if(deny(e.getEntity(),e.getPos())) {e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);return;}
        track(e.getEntity(),e.getPos());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void left(PlayerInteractEvent.LeftClickBlock e) {if(!e.getLevel().isClientSide && deny(e.getEntity(),e.getPos()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void breaking(BlockEvent.BreakEvent e) {if(deny(e.getPlayer(),e.getPos()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void place(BlockEvent.EntityPlaceEvent e) {
        if(!(e.getEntity() instanceof Player p))return;
        if(deny(p,e.getPos()) || e instanceof BlockEvent.EntityMultiPlaceEvent multi
                && multi.getReplacedBlockSnapshots().stream().anyMatch(s->deny(p,s.getPos())))e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void entity(PlayerInteractEvent.EntityInteract e) {entityCheck(e,e.getTarget());}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void specific(PlayerInteractEvent.EntityInteractSpecific e) {entityCheck(e,e.getTarget());}
    private static void entityCheck(PlayerInteractEvent e,net.minecraft.world.entity.Entity target) {
        if(e.getLevel() instanceof ServerLevel level && !ShipProtection.allows(e.getEntity(),level,ShipProtection.ship(target))) {
            e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);ShipProtection.denied(e.getEntity());
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void mount(EntityMountEvent e) {
        if(e.isMounting() && e.getEntityMounting() instanceof Player p && p.level() instanceof ServerLevel level
                && !ShipProtection.allows(p,level,ShipProtection.ship(e.getEntityBeingMounted()))) {e.setCanceled(true);ShipProtection.denied(p);}
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.START || !(e.player instanceof ServerPlayer p))return;
        if(p.getVehicle()!=null && !ShipProtection.allows(p,p.serverLevel(),ShipProtection.ship(p.getVehicle())))p.stopRiding();
        var opened=openShips.get(p.getUUID());
        if(opened!=null && p.containerMenu!=p.inventoryMenu) {
            var ship=org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(opened.level).getLoadedShips().getById(opened.id);
            if(p.serverLevel()!=opened.level || ship==null || !ShipProtection.allows(p,opened.level,ship))p.closeContainer();
        }
    }
    @SubscribeEvent public static void close(PlayerContainerEvent.Close e) {openShips.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {openShips.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {openShips.clear();}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void explosion(ExplosionEvent.Detonate e) {
        if(!(e.getLevel() instanceof ServerLevel level) || !ProtectionConfig.get().protectExplosions)return;
        Map<Long,Boolean> decisions=new HashMap<>();
        e.getAffectedBlocks().removeIf(pos->{var ship=ShipProtection.ship(level,pos);
            return ship!=null && !decisions.computeIfAbsent(ship.getId(),id->ShipProtection.allows(null,level,ship));});
        e.getAffectedEntities().removeIf(entity->{var ship=ShipProtection.ship(entity);
            return ship!=null && !decisions.computeIfAbsent(ship.getId(),id->ShipProtection.allows(null,level,ship));});
    }
}
