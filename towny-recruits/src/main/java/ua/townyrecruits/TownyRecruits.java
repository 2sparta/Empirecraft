package ua.townyrecruits;

import com.mojang.brigadier.arguments.*;
import com.talhanation.recruits.entities.*;
import com.talhanation.recruits.config.RecruitsServerConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.*;

@Mod("townyrecruits")
public final class TownyRecruits {
    public static final Logger LOG=LogUtils.getLogger();
    public TownyRecruits(){ua.townyrecruits.tactics.TacticalNetwork.init();ua.townyrecruits.map.ArmyMapNetwork.init();MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void start(ServerStartedEvent e){
        forceSettings();Bridge.INSTANCE=new Bridge(e.getServer());LOG.info("TownyRecruits loaded. Waiting for Towny; /townyrecruits status");
    }
    static void forceSettings(){
        try{
        RecruitsServerConfig.RecruitsPayment.set(false);
        RecruitsServerConfig.RecruitCurrency.set("minecraft:gold_ingot");
        RecruitsServerConfig.MaxPlayersInFaction.set(0);
        RecruitsServerConfig.ShouldFactionEditingBeAllowed.set(false);
        RecruitsServerConfig.ShouldFactionManagingBeAllowed.set(false);
        RecruitsServerConfig.AllowClaiming.set(false);
        RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(true);
        // Towny is accessed only on the server thread, including targeting hooks.
        RecruitsServerConfig.UseAsyncTargetFinding.set(false);
        }catch(NullPointerException unloaded){/* Recruits config spec not registered yet */}
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        if(e.phase==TickEvent.Phase.START)ua.townyrecruits.tactics.TacticalFire.tick();
        if(e.phase==TickEvent.Phase.END){if(Bridge.INSTANCE!=null){Bridge.INSTANCE.tick();Bridge.INSTANCE.military.morale.tick();ArmyMapService.tick(Bridge.INSTANCE);}ua.townyrecruits.tactics.TacticalRoutes.tick();}
    }
    @SubscribeEvent public void stop(ServerStoppingEvent e){ua.townyrecruits.tactics.TacticalNetwork.clear();RegimentExchange.clear();if(Bridge.INSTANCE!=null){Bridge.INSTANCE.close();Bridge.INSTANCE=null;}}
    @SubscribeEvent public void join(EntityJoinLevelEvent e){
        if(!(e.getEntity() instanceof AbstractRecruitEntity r))return;
        if(e.getLevel().m_5776_())return;
        ua.townyrecruits.tactics.TacticalMovement.install(r);
        ua.townyrecruits.tactics.TacticalCombat.install(r);
        if(Bridge.INSTANCE!=null)Bridge.INSTANCE.military.morale.install(r);
        ua.townyrecruits.tactics.RecruitBombs.enforce(r);
        if(Bridge.INSTANCE!=null){Bridge.INSTANCE.loaded.add(r);Bridge.INSTANCE.updateUnit(r);}
        r.f_21345_.m_25352_(0,new GunGoal(r));
        r.f_21345_.m_25352_(0,new ua.townyrecruits.tactics.ArcherGoal(r));
    }
    @SubscribeEvent public void leave(EntityLeaveLevelEvent e){if(e.getEntity() instanceof AbstractRecruitEntity r && !e.getLevel().m_5776_())ua.townyrecruits.tactics.TacticalOrders.leave(r);if(Bridge.INSTANCE!=null && e.getEntity() instanceof AbstractRecruitEntity r){Bridge.INSTANCE.loaded.remove(r);if(r.m_146911_()==Entity.RemovalReason.KILLED || r.m_146911_()==Entity.RemovalReason.DISCARDED){Bridge.INSTANCE.recruitment.retire(r.m_20148_());Bridge.INSTANCE.data.remove(r.m_20148_());}}}
    @SubscribeEvent public void recruitTick(LivingEvent.LivingTickEvent e){if(e.getEntity() instanceof AbstractRecruitEntity r && !r.m_20193_().m_5776_() && r.f_19797_%20==0)ua.townyrecruits.tactics.RecruitBombs.enforce(r);}
    @SubscribeEvent public void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){ua.townyrecruits.tactics.TacticalNetwork.logout(e.getEntity().m_20148_());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void death(LivingDeathEvent e){
        if(Bridge.INSTANCE!=null && e.getEntity() instanceof AbstractRecruitEntity r){
            Bridge.INSTANCE.recruitment.retire(r.m_20148_());
            var d=r.getPersistentData();long now=Bridge.INSTANCE.server.m_129783_().m_46467_();
            boolean battle=e.getSource().m_7639_() instanceof LivingEntity || d.m_128441_("TRBattleAt") && now>=d.m_128454_("TRBattleAt") && now-d.m_128454_("TRBattleAt")<=MoralePolicy.CALM;
            Bridge.INSTANCE.military.morale.casualty(r,battle);Bridge.INSTANCE.data.remove(r.m_20148_());Bridge.INSTANCE.military.forget(r);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void battleDamage(LivingDamageEvent e){
        if(Bridge.INSTANCE==null || !Bridge.INSTANCE.ready || e.getAmount()<=0 || !(e.getSource().m_7639_() instanceof LivingEntity attacker))return;
        if(Boolean.FALSE.equals(Bridge.combat(attacker,e.getEntity())))return;
        if(e.getEntity() instanceof AbstractRecruitEntity r)Bridge.INSTANCE.military.morale.combat(r);
        if(attacker instanceof AbstractRecruitEntity r)Bridge.INSTANCE.military.morale.combat(r);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void target(LivingChangeTargetEvent e){
        if(e.getNewTarget()!=null && Boolean.FALSE.equals(Bridge.combat(e.getEntity(),e.getNewTarget())))e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void impact(ProjectileImpactEvent e){
        if(e.getProjectile().m_19749_() instanceof LivingEntity owner && e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit && hit.m_82443_() instanceof LivingEntity target && Boolean.FALSE.equals(Bridge.combat(owner,target))){e.getProjectile().m_146870_();e.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void damage(LivingAttackEvent e){
        Entity source=e.getSource().m_7639_();
        if(source instanceof LivingEntity a && Boolean.FALSE.equals(Bridge.combat(a,e.getEntity())))e.setCanceled(true);
    }
    @SubscribeEvent public void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(Bridge.INSTANCE!=null)Bridge.INSTANCE.dirty=true;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void nativeCommand(CommandEvent e){
        var nodes=e.getParseResults().getContext().getNodes();
        if(!nodes.isEmpty() && java.util.Set.of("team","minecraft:team").contains(nodes.get(0).getNode().getName())){e.setCanceled(true);e.getParseResults().getContext().getSource().m_81352_(Component.m_237113_("Фракціями керує Towny."));return;}
        if(nodes.size()>2 && nodes.get(0).getNode().getName().equals("recruits") && nodes.get(1).getNode().getName().equals("admin") && java.util.Set.of("factionManager","diplomacyManager","claimManager","unitsManager").contains(nodes.get(2).getNode().getName())){
            e.setCanceled(true);e.getParseResults().getContext().getSource().m_81352_(Component.m_237113_("Керуйте націями через Towny, лімітами через /townyrecruits."));
        }
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent e){
        RecruitmentCommands.register(e.getDispatcher());
        e.getDispatcher().register(Commands.m_82127_("townyrecruits").requires(s->s.m_6761_(2))
            .then(Commands.m_82127_("status").executes(c->{Bridge b=Bridge.INSTANCE;c.getSource().m_288197_(()->Component.m_237113_(b==null?"TownyRecruits: starting":b.status()),false);return 1;}))
            .then(Commands.m_82127_("sync").executes(c->{if(Bridge.INSTANCE!=null){forceSettings();Bridge.INSTANCE.requestSync();}return 1;}))
            .then(Commands.m_82127_("census").executes(c->{if(Bridge.INSTANCE==null)return 0;Bridge.INSTANCE.requestCensus();c.getSource().m_288197_(()->Component.m_237113_("Повторний облік заплановано. Перевірте /townyrecruits status після завершення."),false);return 1;}))
            .then(Commands.m_82127_("info").then(Commands.m_82129_("nation",StringArgumentType.string()).suggests((context,builder)->RecruitmentCommands.nationSuggestions(context.getSource(),builder)).executes(c->edit(c.getSource(),StringArgumentType.getString(c,"nation"),null,false))))
            .then(Commands.m_82127_("setlimit").then(Commands.m_82129_("nation",StringArgumentType.string()).suggests((context,builder)->RecruitmentCommands.nationSuggestions(context.getSource(),builder)).then(Commands.m_82129_("amount",IntegerArgumentType.integer(0,1000000)).executes(c->edit(c.getSource(),StringArgumentType.getString(c,"nation"),IntegerArgumentType.getInteger(c,"amount"),false)))))
            .then(Commands.m_82127_("resetlimit").then(Commands.m_82129_("nation",StringArgumentType.string()).suggests((context,builder)->RecruitmentCommands.nationSuggestions(context.getSource(),builder)).executes(c->edit(c.getSource(),StringArgumentType.getString(c,"nation"),null,true)))));
    }
    int edit(net.minecraft.commands.CommandSourceStack source,String name,Integer value,boolean reset){
        Bridge b=Bridge.INSTANCE;if(b==null||!b.ready){source.m_81352_(Component.m_237113_("Towny недоступний"));return 0;}
        Policy.Nation nation;
        try{b.refreshNow();if(!b.ready)throw new IllegalStateException("Towny недоступний.");nation=RecruitmentCommands.nation(b,name);}
        catch(Exception ex){source.m_81352_(Component.m_237113_(Bridge.errorText(ex)));return 0;}
        if(reset||value!=null)b.recruitment.limits(nation.id(),reset?b.recruitment.config.total:value,reset?b.recruitment.config.perPlayer:b.recruitment.state.nations.get(nation.id()).perPlayer);
        String text=nation.name()+": військо "+b.count(nation.id())+"/"+TownyRecruitMenu.cap(b.limit(nation.id()))+"; міст="+nation.towns()+"; на гравця="+b.recruitment.state.nations.get(nation.id()).perPlayer;
        source.m_288197_(()->Component.m_237113_(text),false);return 1;
    }
}
