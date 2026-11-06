package ua.empirecraft.engagement;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import com.quintonc.vs_sails.protection.ShipProtection;

@Mod.EventBusSubscriber(modid=EngagementMod.ID)
public final class CombatEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void spawn(EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide)try{EngagementRuntime.inheritCompany(event.getEntity());ShotOrigins.joined(event.getEntity(),event.loadedFromDisk());}catch(RuntimeException error){EngagementRuntime.warn(error);if(ShotOrigins.dangerous(event.getEntity()))event.setCanceled(true);}
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){ShotOrigins.forget(event.getEntity().getUUID());Deck.forget(event.getEntity().getUUID());}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){if(event.phase==TickEvent.Phase.START&&event.level instanceof ServerLevel level)try{EngagementRuntime.tick(level);}catch(RuntimeException unavailable){EngagementRuntime.warn(unavailable);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void melee(AttackEntityEvent event){if(!event.getEntity().level().isClientSide&&!EngagementRuntime.attack(event.getEntity(),event.getTarget(),true))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hurt(LivingAttackEvent event){if(!event.getEntity().level().isClientSide&&!EngagementRuntime.hurt(event.getEntity(),event.getSource(),true))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damaged(LivingDamageEvent event){if(event.getAmount()>0&&!event.getEntity().level().isClientSide)EngagementRuntime.damaged(event.getEntity(),event.getSource());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void impact(ProjectileImpactEvent event){
        if(!(event.getProjectile().level() instanceof ServerLevel level))return;var projectile=event.getProjectile();
        try {
            var origin=ShotOrigins.find(projectile);var source=origin==null?(projectile.getOwner()==null?EngagementRuntime.environment():Combatants.party(projectile.getOwner())):origin.party();boolean allow=true;
            if(event.getRayTraceResult() instanceof EntityHitResult hit)allow=EngagementRuntime.decide(source,Combatants.party(hit.getEntity()),level,true).allow();
            else if(event.getRayTraceResult() instanceof BlockHitResult hit){var ship=ShipProtection.ship(level,hit.getBlockPos());if(ship!=null)allow=EngagementRuntime.decide(source,Combatants.ship(level,ship,null,null,null),level,true).allow();}
            if(!allow){event.setCanceled(true);projectile.discard();}
        }catch(RuntimeException unavailable){event.setCanceled(true);projectile.discard();EngagementRuntime.warn(unavailable);}
    }
    public static ShotOrigins.Origin explosionOrigin(Explosion explosion){
        var origin=ShotOrigins.find(explosion.getDirectSourceEntity());if(origin!=null)return origin;if(ShotOrigins.current()!=null)return ShotOrigins.current();
        var owner=explosion.getIndirectSourceEntity();return owner!=null&&owner.level() instanceof ServerLevel level&&Combatants.participant(owner)?ShotOrigins.of(Combatants.party(owner),level):null;
    }
    public static void explosion(ExplosionEvent.Detonate event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var origin=explosionOrigin(event.getExplosion());var source=origin==null?EngagementRuntime.environment():origin.party();Map<Long,Boolean> decisions=new HashMap<>();
        event.getAffectedBlocks().removeIf(pos->{var ship=ShipProtection.ship(level,pos);if(ship==null)return false;
            return !decisions.computeIfAbsent(ship.getId(),id->{try{return EngagementRuntime.decide(source,Combatants.ship(level,ship,null,null,null),level,true).allow();}catch(RuntimeException e){EngagementRuntime.warn(e);return false;}});});
        event.getAffectedEntities().removeIf(entity->{if(!Combatants.participant(entity))return false;try{return !EngagementRuntime.decide(source,Combatants.party(entity),level,true).allow();}catch(RuntimeException e){EngagementRuntime.warn(e);return true;}});
    }
    /** Called in place of Sails' old control-permission-based damage guard. Other listeners remain authoritative. */
    public static boolean breakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
        var ship=ShipProtection.ship(event.getPlayer().level(),event.getPos());if(ship==null)return false;
        Boolean ram=RamCombat.permitted((ServerLevel)event.getPlayer().level(),ship);if(ram!=null){if(!ram)event.setCanceled(true);return true;}
        if(Government.controls((ServerLevel)event.getPlayer().level(),event.getPlayer().getUUID(),ship.getId()))return false;
        boolean allow=EngagementRuntime.decide(Combatants.party(event.getPlayer()),Combatants.ship((ServerLevel)event.getPlayer().level(),ship,null,null,null),(ServerLevel)event.getPlayer().level(),true).allow();
        if(!allow)event.setCanceled(true);return true;
    }
    public static boolean block(ServerLevel level,BlockPos pos){
        Boolean ram=RamCombat.permitted(level,pos);if(ram!=null)return ram;
        var origin=ShotOrigins.current();if(origin==null)return true;var ship=ShipProtection.ship(level,pos);if(ship==null||Objects.equals(origin.ship(),ship.getId()))return true;
        try{return EngagementRuntime.decide(origin.party(),Combatants.ship(level,ship,null,null,null),level,true).allow();}catch(RuntimeException e){EngagementRuntime.warn(e);return false;}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){Government.clear();Deck.clear();ShotOrigins.clear();NavalFlags.clear();RamCombat.clear();EngagementRuntime.clear();}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&Combatants.active(p))EngagementRuntime.playerTag(p);}
}
