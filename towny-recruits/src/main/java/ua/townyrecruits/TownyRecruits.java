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
    public TownyRecruits(){MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void start(ServerStartedEvent e){
        forceSettings();Bridge.INSTANCE=new Bridge(e.getServer());LOG.info("TownyRecruits loaded. Waiting for Towny; /townyrecruits status");
    }
    static void forceSettings(){
        RecruitsServerConfig.RecruitsPayment.set(false);
        RecruitsServerConfig.RecruitCurrency.set("minecraft:gold_ingot");
        RecruitsServerConfig.MaxPlayersInFaction.set(0);
        RecruitsServerConfig.ShouldFactionEditingBeAllowed.set(false);
        RecruitsServerConfig.ShouldFactionManagingBeAllowed.set(false);
        RecruitsServerConfig.AllowClaiming.set(false);
        RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(true);
        // Towny is accessed only on the server thread, including targeting hooks.
        RecruitsServerConfig.UseAsyncTargetFinding.set(false);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END && Bridge.INSTANCE!=null)Bridge.INSTANCE.tick();}
    @SubscribeEvent public void stop(ServerStoppingEvent e){if(Bridge.INSTANCE!=null){Bridge.INSTANCE.close();Bridge.INSTANCE=null;}}
    @SubscribeEvent public void join(EntityJoinLevelEvent e){
        if(e.getLevel().m_5776_() || !(e.getEntity() instanceof AbstractRecruitEntity r))return;
        if(Bridge.INSTANCE!=null){Bridge.INSTANCE.loaded.add(r);Bridge.INSTANCE.updateUnit(r);}
        if(r instanceof IRangedRecruit)r.f_21345_.m_25352_(0,new GunGoal(r));
    }
    @SubscribeEvent public void leave(EntityLeaveLevelEvent e){if(Bridge.INSTANCE!=null && e.getEntity() instanceof AbstractRecruitEntity r){Bridge.INSTANCE.loaded.remove(r);if(r.m_146911_()==Entity.RemovalReason.KILLED || r.m_146911_()==Entity.RemovalReason.DISCARDED)Bridge.INSTANCE.data.remove(r.m_20148_());}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void death(LivingDeathEvent e){if(Bridge.INSTANCE!=null && e.getEntity() instanceof AbstractRecruitEntity r)Bridge.INSTANCE.data.remove(r.m_20148_());}
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
        e.getDispatcher().register(Commands.m_82127_("townyrecruits").requires(s->s.m_6761_(2))
            .then(Commands.m_82127_("status").executes(c->{Bridge b=Bridge.INSTANCE;c.getSource().m_288197_(()->Component.m_237113_(b==null?"TownyRecruits: starting":b.status()),false);return 1;}))
            .then(Commands.m_82127_("sync").executes(c->{if(Bridge.INSTANCE!=null){forceSettings();Bridge.INSTANCE.requestSync();}return 1;}))
            .then(Commands.m_82127_("census").executes(c->{if(Bridge.INSTANCE==null)return 0;Bridge.INSTANCE.requestCensus();c.getSource().m_288197_(()->Component.m_237113_("Повторний облік заплановано. Перевірте /townyrecruits status після завершення."),false);return 1;}))
            .then(Commands.m_82127_("info").then(Commands.m_82129_("nation",StringArgumentType.string()).executes(c->edit(c.getSource(),StringArgumentType.getString(c,"nation"),null,false))))
            .then(Commands.m_82127_("setlimit").then(Commands.m_82129_("nation",StringArgumentType.string()).then(Commands.m_82129_("amount",IntegerArgumentType.integer(0,1000000)).executes(c->edit(c.getSource(),StringArgumentType.getString(c,"nation"),IntegerArgumentType.getInteger(c,"amount"),false)))))
            .then(Commands.m_82127_("resetlimit").then(Commands.m_82129_("nation",StringArgumentType.string()).executes(c->edit(c.getSource(),StringArgumentType.getString(c,"nation"),null,true)))));
    }
    int edit(net.minecraft.commands.CommandSourceStack source,String name,Integer value,boolean reset){
        Bridge b=Bridge.INSTANCE;if(b==null||!b.ready){source.m_81352_(Component.m_237113_("Towny недоступний"));return 0;}
        Policy.Nation nation=b.snapshot.nations().values().stream().filter(n->n.name().equalsIgnoreCase(name)||n.id().toString().equalsIgnoreCase(name)).findFirst().orElse(null);
        if(nation==null){source.m_81352_(Component.m_237113_("Націю не знайдено: "+name));return 0;}
        if(reset)b.data.overrides.remove(nation.id());else if(value!=null)b.data.overrides.put(nation.id(),value);
        if(reset||value!=null){b.data.m_77762_();b.syncFactions();}
        String text=nation.name()+": "+b.count(nation.id())+" / "+b.limit(nation.id())+" units; towns="+nation.towns()+"; override="+b.data.overrides.get(nation.id());
        source.m_288197_(()->Component.m_237113_(text),false);return 1;
    }
}
