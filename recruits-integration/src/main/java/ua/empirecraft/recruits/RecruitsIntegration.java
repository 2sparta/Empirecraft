package ua.empirecraft.recruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.FactionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.effect.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod("empirecraft_recruits")
public final class RecruitsIntegration {
    static final Map<UUID,AbstractRecruitEntity> LOADED=new ConcurrentHashMap<>();
    int ticks;
    public RecruitsIntegration(){MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void join(EntityJoinLevelEvent e){if(e.getLevel().m_5776_())return;if(e.getEntity() instanceof AbstractRecruitEntity r){LOADED.put(r.m_20148_(),r);r.getPersistentData().m_128356_("ArmyFoodAt",System.currentTimeMillis());}}
    @SubscribeEvent public void leave(EntityLeaveLevelEvent e){if(e.getLevel().m_5776_())return;if(e.getEntity() instanceof AbstractRecruitEntity r){LOADED.remove(r.m_20148_());ArmyHooks.call(r.m_146911_()==Entity.RemovalReason.DISCARDED?"retire":"lost",Map.of("unit",r.m_20148_().toString()));}}
    @SubscribeEvent public void death(LivingDeathEvent e){if(e.getEntity() instanceof AbstractRecruitEntity r&&ArmyHooks.managed(r)){ArmyHooks.call("dead",Map.of("unit",r.m_20148_().toString()));LOADED.remove(r.m_20148_());}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void target(LivingChangeTargetEvent e){if(e.getEntity() instanceof AbstractRecruitEntity r&&ArmyHooks.managed(r)&&e.getNewTarget()!=null&&!ArmyHooks.allows(r,e.getNewTarget())){e.setNewTarget(null);}}
    static Entity source(net.minecraft.world.damagesource.DamageSource damage){Entity source=damage.m_7639_();if(source instanceof Projectile p&&p.m_19749_()!=null)source=p.m_19749_();return source;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void attack(LivingAttackEvent e){if(!ArmyHooks.damageAllowed(e.getSource(),e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void hurt(LivingHurtEvent e){if(!ArmyHooks.damageAllowed(e.getSource(),e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e){Entity source=source(e.getSource());if(!ArmyHooks.damageAllowed(e.getSource(),e.getEntity())){e.setCanceled(true);return;}if(e.getAmount()>0){var data=source==null?ArmyHooks.projectileData(e.getSource().m_7640_(),e.getEntity()):ArmyHooks.pair(source,e.getEntity());if(data!=null)ArmyHooks.call("incident",data);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void projectile(ProjectileImpactEvent e){if(e.getRayTraceResult() instanceof EntityHitResult hit&&hit.m_82443_() instanceof LivingEntity target){if(!ArmyHooks.projectileAllowed(e.getProjectile(),target))e.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);}}
    @SubscribeEvent public void stop(ServerStoppingEvent e){LOADED.clear();ArmyHooks.units.clear();ArmyHooks.detach();}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;ticks++;
        for(AbstractRecruitEntity r:LOADED.values())if(r.m_6084_()&&ArmyHooks.managed(r)&&r.m_5448_()!=null&&!ArmyHooks.allows(r,r.m_5448_())){r.m_6710_(null);r.m_6703_(null);r.m_6598_(null);r.m_21561_(false);r.m_21573_().m_26573_();}
        if(ticks%20!=0)return;
        try{FactionSync.tick();}catch(RuntimeException failure){System.getLogger("EmpirecraftRecruits").log(System.Logger.Level.WARNING,"Синхронізація фракцій: "+failure);}
        for(AbstractRecruitEntity r:LOADED.values())if(r.m_6084_()&&ArmyHooks.managed(r)){
            try{maintain(r);}catch(RuntimeException ex){System.getLogger("EmpirecraftRecruits").log(System.Logger.Level.WARNING,"Не вдалося оновити рекрута "+r.m_20148_()+": "+ex);}
        }
    }
    static void maintain(AbstractRecruitEntity r){
        if(!r.getIsOwned()&&!ArmyHooks.garrison(r)){ArmyHooks.call("retire",Map.of("unit",r.m_20148_().toString()));r.getPersistentData().m_128379_(ArmyHooks.MANAGED,false);return;}
        var state=ArmyHooks.observe(r);if(!state.isEmpty()){
            Object desired=state.get("owner");if(desired!=null&&!Objects.equals(desired.toString(),r.getOwnerUUID()==null?null:r.getOwnerUUID().toString()))r.setOwnerUUID(Optional.of(UUID.fromString(desired.toString())));
            Object faction=state.get("faction");if(faction!=null)FactionSync.unit(r,faction.toString());
            if(Boolean.FALSE.equals(state.get("active"))){r.m_6710_(null);r.setAggroState(3);}
        }
        if(state.isEmpty()){r.getPersistentData().m_128356_("ArmyFoodAt",System.currentTimeMillis());return;}
        long now=System.currentTimeMillis();float beforeHunger=r.getHunger();var tag=r.getPersistentData();long previous=tag.m_128454_("ArmyFoodAt");tag.m_128356_("ArmyFoodAt",now);
        double rate=((Number)ArmyHooks.config.getOrDefault("hungerPerMinute",2.)).doubleValue();r.setHunger(RationMath.drain(r.getHunger(),previous,now,rate));
        boolean feedingPaused=false;
        if(r.getHunger()<=50){long sequence=tag.m_128454_(ArmyHooks.FOOD_SEQ)+1;var response=ArmyHooks.call("feed",Map.of("unit",r.m_20148_().toString(),"seq",sequence));if(response.isEmpty()||Boolean.TRUE.equals(response.get("paused"))){r.setHunger(beforeHunger);feedingPaused=true;}Object amount=response.get("amount");if(amount instanceof Number n&&n.intValue()>0){r.setHunger(RationMath.restore(r.getHunger(),n.intValue()));tag.m_128356_(ArmyHooks.FOOD_SEQ,sequence);}}
        int interval=((Number)ArmyHooks.config.getOrDefault("starveEverySeconds",10)).intValue();
        if(!feedingPaused&&RationMath.starvation(r.getHunger(),tag.m_128454_("ArmyStarveAt"),now,interval)){tag.m_128356_("ArmyStarveAt",now);r.m_6469_(r.m_269291_().m_269064_(),1f);}
        if(ArmyHooks.garrison(r))r.resetPaymentTimer();
        if(Boolean.TRUE.equals(state.get("unpaid"))){var slow=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft","slowness"));if(slow!=null)r.m_7292_(new MobEffectInstance(slow,40,0,false,false));}
        if(now-tag.m_128454_("ArmyMoraleAt")>=60000){r.updateMorale();tag.m_128356_("ArmyMoraleAt",now);}
    }
}
