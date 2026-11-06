package ua.townyrecruits;

import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.townyrecruits.map.*;
import ua.townyrecruits.map.ArmyMapData.*;
import ua.townyrecruits.tactics.*;

/** Allies share loaded positions; other armies are shown only while vanilla sends their entities to this player. */
@Mod.EventBusSubscriber(modid="townyrecruits")
public final class ArmyMapService {
    private static final Map<UUID,Set<UUID>> TRACKED=new HashMap<>();
    private static long sequence;
    private ArmyMapService() {}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof AbstractRecruitEntity r)TRACKED.computeIfAbsent(p.m_20148_(),id->new HashSet<>()).add(r.m_20148_());}
    @SubscribeEvent public static void stoppedTracking(PlayerEvent.StopTracking e){var ids=TRACKED.get(e.getEntity().m_20148_());if(ids!=null)ids.remove(e.getTarget().m_20148_());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){TRACKED.remove(e.getEntity().m_20148_());}
    @SubscribeEvent public static void stopped(ServerStoppingEvent e){TRACKED.clear();sequence=0;}
    static Relation relation(Bridge bridge,UUID viewer,AbstractRecruitEntity r){int relation=Policy.relation(bridge.snapshot,viewer,bridge.nation(r));return relation<0?Relation.ENEMY:relation>0?Relation.ALLY:Relation.NEUTRAL;}
    public static void tick(Bridge bridge) {
        if(bridge.ticks%20!=0)return;long current=++sequence;
        List<AbstractRecruitEntity> loaded=List.copyOf(bridge.loaded);
        for(ServerPlayer player:bridge.server.m_6846_().m_11314_()) {
            List<Unit> units=new ArrayList<>();Map<UUID,Style> styles=new LinkedHashMap<>();UUID viewer=bridge.snapshot.residents().get(player.m_20148_());
            if(bridge.ready&&bridge.censusReady()) {
                Set<UUID> tracked=TRACKED.getOrDefault(player.m_20148_(),Set.of());
                var visible=loaded.stream().filter(r->r.m_6084_()&&!r.m_213877_()&&ArmyMapData.visible(relation(bridge,viewer,r),r.m_20193_()==player.m_20193_(),tracked.contains(r.m_20148_()),r.m_20145_()))
                    .sorted(Comparator.comparingInt((AbstractRecruitEntity r)->relation(bridge,viewer,r)==Relation.ALLY?0:1).thenComparingDouble(player::m_20280_)).limit(ArmyMapData.MAX_UNITS).toList();
                for(var r:visible) {
                    var pose=ArmyMapCoordinates.world(r.m_20193_(),TacticalActor.position(r),r.m_146908_());if(pose==null)continue;
                    var company=bridge.military.data.companies.get(r.getGroup());var regiment=company==null?null:bridge.military.data.regiments.get(company.regiment());
                    UUID reg=regiment==null?null:regiment.id();
                    if(reg!=null&&!styles.containsKey(reg)) {
                        if(styles.size()<ArmyMapData.MAX_STYLES)styles.put(reg,new Style(reg,ArmyMapData.text(regiment.name()),RegimentalBanners.design(bridge.military.data,reg)));
                        else reg=null;
                    }
                    UUID nation=bridge.nation(r);var n=nation==null?null:bridge.snapshot.nations().get(nation);
                    var group=RecruitEvents.recruitsGroupsManager.getGroup(r.getGroup());String commander=r.getOwnerUUID()==null?"":bridge.military.commanderName(r.getOwnerUUID());
                    boolean mounted=TacticalActor.mounted(r),ranged=TacticalEquipment.ranged(r);
                    Kind kind=mounted?(ranged?Kind.MOUNTED_RANGED:Kind.CAVALRY):ranged?Kind.RANGED:TacticalEquipment.pike(r)?Kind.PIKE:Kind.INFANTRY;
                    units.add(new Unit(r.m_20148_(),pose.position(),pose.yaw(),relation(bridge,viewer,r),kind,reg,
                        ArmyMapData.text(n==null?"":n.name()),ArmyMapData.text(group==null?"":group.getName()),ArmyMapData.text(commander),
                        RegimentalBanners.carrying(bridge.military.data,reg,r),MoraleSystem.routing(r)));
                }
            }
            ArmyMapNetwork.send(player,new Snapshot(player.m_20193_().m_46472_().m_135782_(),current,List.copyOf(styles.values()),List.copyOf(units)));
        }
    }
}
