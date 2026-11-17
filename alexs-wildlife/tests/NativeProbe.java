package ua.empirecraft.wildlifetest;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.wildlife.*;

@Mod("empirecraft_wildlife_test")
public final class NativeProbe {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public NativeProbe(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    static Entity create(String id,ServerLevel l){var key=new ResourceLocation(id);check(ForgeRegistries.ENTITY_TYPES.containsKey(key),"Registered "+id);return ForgeRegistries.ENTITY_TYPES.getValue(key).m_20615_(l);}
    static void at(Entity e,double lon,double lat){e.m_7678_(WildlifeMod.policy.earth.x(lon),4,WildlifeMod.policy.earth.z(lat),0,0);}
    void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.m_129783_();var p=WildlifeMod.policy;
        try{
            // Exercise the real Forge event gates with every removed registered entity.
            for(String id:p.removed){var entity=create(id,level);entity.m_7678_(0,4,0,0,0);check(!level.m_7967_(entity),"Excluded entity cannot enter a level: "+id);}
            for(String id:p.animals().keySet())check(create(id,level)!=null,"Kept species constructor "+id);
            for(String id:List.of("naturalist:bear","naturalist:catfish","spawn:seal"))check(create(id,level)!=null,"Existing preferred species "+id);
            var oldAnt=create("spawn:ant",level);var disk=new EntityJoinLevelEvent(oldAnt,level,true);MinecraftForge.EVENT_BUS.post(disk);check(disk.isCanceled(),"Saved Spawn ant removed");
            var kangaroo=(Mob)create("alexsmobs:kangaroo",level);at(kangaroo,135,-25);
            var checkAustralia=new MobSpawnEvent.PositionCheck(kangaroo,level,MobSpawnType.NATURAL,null);MinecraftForge.EVENT_BUS.post(checkAustralia);check(checkAustralia.getResult()!=Event.Result.DENY,"Kangaroo Australia event");
            at(kangaroo,32,48);var checkUkraine=new MobSpawnEvent.PositionCheck(kangaroo,level,MobSpawnType.NATURAL,null);MinecraftForge.EVENT_BUS.post(checkUkraine);check(checkUkraine.getResult()==Event.Result.DENY,"Kangaroo Ukraine event");
            check(!level.m_7967_(kangaroo),"Direct custom spawn cannot bypass geographic check");
            // A deliberately handled animal may be transported; geography governs natural creation.
            var captive=create("alexsmobs:kangaroo",level);at(captive,32,48);captive.getPersistentData().m_128379_("EmpirecraftWildlifeCaptive",true);check(level.m_7967_(captive),"Captive transport allowed");captive.m_146870_();
            AABB area=new AABB(-20,70,-20,20,120,20);
            for(String id:p.json.getAsJsonObject("whale_loot").keySet()) {
                for(var item:level.m_45976_(ItemEntity.class,area))item.m_146870_();
                var whale=(Animal)create(id,level);whale.m_7678_(0,100,0,0,0);whale.getPersistentData().m_128379_("EmpirecraftWildlifeCaptive",true);check(level.m_7967_(whale),"Add real whale "+id);
                whale.m_6667_(level.m_269111_().m_269264_());
                var totals=new HashMap<String,Integer>();
                for(var drop:level.m_45976_(ItemEntity.class,area)){
                    var stack=drop.m_32055_();check(stack.m_41613_()>0&&stack.m_41613_()<=stack.m_41741_(),"Legal whale stack");totals.merge(ForgeRegistries.ITEMS.getKey(stack.m_41720_()).toString(),stack.m_41613_(),Integer::sum);
                }
                for(var loot:p.json.getAsJsonObject("whale_loot").getAsJsonObject(id).entrySet()){
                    int total=totals.getOrDefault(loot.getKey(),0);var range=loot.getValue().getAsJsonArray();check(total>=range.get(0).getAsInt()&&total<=range.get(1).getAsInt(),id+" "+loot.getKey()+"="+total);
                }
                whale.m_146870_();
            }
            for(var item:level.m_45976_(ItemEntity.class,area))item.m_146870_();
            level.m_46469_().m_46170_(GameRules.f_46135_).m_46246_(false,server);
            var whale=(Animal)create("alexsmobs:cachalot_whale",level);whale.m_7678_(0,100,0,0,0);whale.getPersistentData().m_128379_("EmpirecraftWildlifeCaptive",true);level.m_7967_(whale);whale.m_6667_(level.m_269111_().m_269264_());
            check(level.m_45976_(ItemEntity.class,area).isEmpty(),"doMobLoot false produces no bulk loot");
            level.m_46469_().m_46170_(GameRules.f_46135_).m_46246_(true,server);
            var baby=(Animal)create("alexsmobs:cachalot_whale",level);baby.m_146762_(-24000);baby.m_7678_(0,100,0,0,0);baby.getPersistentData().m_128379_("EmpirecraftWildlifeCaptive",true);level.m_7967_(baby);baby.m_6667_(level.m_269111_().m_269264_());
            int amount=level.m_45976_(ItemEntity.class,area).stream().mapToInt(i->i.m_32055_().m_41613_()).sum();check(amount<128,"No adult yield from baby whales");
            String result="WILDLIFE_NATIVE_PASS checks="+checks+" removed="+p.removed.size()+" kept="+p.animals().size();Files.writeString(Path.of("wildlife-pass.txt"),result);System.out.println(result);
        }catch(Throwable failure){System.out.println("WILDLIFE_NATIVE_FAIL "+failure);failure.printStackTrace();}
        finally{server.m_7570_(false);}
    }
}
