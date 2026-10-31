package smoke;

import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.mojang.authlib.GameProfile;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.RecruitEvents;
import ua.empirecraft.feudal.*;
import ua.empirecraft.recruits.*;
import java.util.*;
import java.lang.reflect.Field;

/** TEST ONLY: creates two towns and NPCs in a disposable world, then exits the server. */
public final class ArmySmokePlugin extends JavaPlugin {
    static final UUID A=UUID.nameUUIDFromBytes("ArmySmokeA".getBytes()),B=UUID.nameUUIDFromBytes("ArmySmokeB".getBytes());
    FeudalPlugin feudal;FeudalService s;ArmyController controller;ServerLevel level;Town ta,tb;AbstractRecruitEntity a,b;String cityA,cityB;int checks;long contract;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->step(this::prepare),100);}
    interface Action{void run()throws Exception;}
    void step(Action action){try{action.run();}catch(Throwable e){System.out.println("ARMY_SMOKE_FAIL "+e);e.printStackTrace();Bukkit.shutdown();}}
    void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;System.out.println("ARMY_SMOKE_CHECK "+reason);}
    Town town(String name,UUID owner)throws Exception{
        var universe=TownyUniverse.getInstance();var resident=universe.getDataSource().newResident(name,owner);Town t=new Town(name,UUID.randomUUID());resident.setTown(t);t.setMayor(resident);universe.registerTown(t);if(!universe.hasTown(t.getUUID()))universe.registerTownUUID(t);return t;
    }
    void prepare()throws Exception{
        feudal=(FeudalPlugin)Bukkit.getPluginManager().getPlugin("EmpirecraftFeudal");check(feudal!=null&&feudal.isEnabled(),"Feudal enabled");check(Bukkit.getPluginManager().isPluginEnabled("TownyRecruitsBridge"),"Bridge enabled");
        Field sf=FeudalPlugin.class.getDeclaredField("service");sf.setAccessible(true);s=(FeudalService)sf.get(feudal);Field cf=FeudalPlugin.class.getDeclaredField("armyController");cf.setAccessible(true);controller=(ArmyController)cf.get(feudal);
        level=ServerLifecycleHooks.getCurrentServer().m_129783_();level.m_8602_(0,0,true);long now=System.currentTimeMillis();System.setProperty(ProvisionClock.PROPERTY,"1|"+now+"|0|false|"+now);
        ta=town("ArmySmokeA",A);tb=town("ArmySmokeB",B);cityA="t:"+ta.getUUID();cityB="t:"+tb.getUUID();
        Bukkit.getWorlds().forEach(w->{w.setPVP(true);if(TownyAPI.getInstance().isTownyWorld(w))TownyAPI.getInstance().getTownyWorld(w).setPVP(true);});
        Field tf=FeudalPlugin.class.getDeclaredField("towny");tf.setAccessible(true);((TownyBridge)tf.get(feudal)).sync();Field pf=FeudalPlugin.class.getDeclaredField("politics");pf.setAccessible(true);((PoliticsService)pf.get(feudal)).syncDomains();controller.refresh();
        FakePlayerFactory.get(level,new GameProfile(A,"ArmySmokeA"));FakePlayerFactory.get(level,new GameProfile(B,"ArmySmokeB"));
        a=spawn(A,.5);b=spawn(B,4.5);
        var stack=new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft","bread"))),8);a.getInventory().m_6836_(0,stack);
        a.setHunger(20);b.setHunger(100);
        // Normal city initialization, with fixture-only stock to isolate the ration mechanism.
        feudal.armyRequest("observe",new HashMap<>(Map.of("unit",a.m_20148_().toString(),"owner",A.toString(),"hunger",20.)));
        feudal.armyRequest("observe",new HashMap<>(Map.of("unit",b.m_20148_().toString(),"owner",B.toString(),"hunger",100.)));
        for(String city:List.of(cityA,cityB))s.db.execute("MERGE INTO provision_towns KEY(town) VALUES(?,?,0,0,0,0,0,0,0,?,0,FALSE)",city,now,now);
        s.db.execute("UPDATE provision_towns SET credit=? WHERE town=?",new java.math.BigDecimal(ProvisionClock.UNIT.multiply(java.math.BigInteger.valueOf(20))),cityA);
        Bukkit.getScheduler().runTaskLater(this,()->step(this::fed),100);
    }
    AbstractRecruitEntity spawn(UUID owner,double x){
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits","recruit"));check(type!=null,"Recruits entity registered");Entity entity=type.m_20615_(level);check(entity instanceof AbstractRecruitEntity,"Real recruit created");var r=(AbstractRecruitEntity)entity;r.setOwnerUUID(Optional.of(owner));r.setIsOwned(true);r.m_6034_(x,Bukkit.getWorlds().get(0).getHighestBlockYAt((int)x,0)+1,.5);r.setFollowState(2);r.setAggroState(3);level.m_7967_(r);return r;
    }
    void fed()throws Exception{
        check(a.getHunger()>60,"City provision refills recruit hunger");check(a.getInventory().m_8020_(0).m_41613_()==8,"Personal food remains untouched");
        var row=s.db.one("SELECT * FROM army_units WHERE id=?",a.m_20148_().toString());check(Database.number(row,"feed_total")==10,"Exactly ten nutrition debited");
        long sequence=a.getPersistentData().m_128454_(ArmyHooks.FOOD_SEQ);check(sequence==1,"Food sequence persisted in entity NBT");
        feudal.armyRequest("feed",Map.of("unit",a.m_20148_().toString(),"seq",sequence));check(Database.number(s.db.one("SELECT * FROM army_units WHERE id=?",a.m_20148_().toString()),"feed_total")==10,"Receipt replay does not debit twice");
        a.setHunger(20);Bukkit.getScheduler().runTaskLater(this,()->step(this::fedAgain),60);
    }
    void fedAgain()throws Exception{
        check(a.getPersistentData().m_128454_(ArmyHooks.FOOD_SEQ)==2,"Second meal persists a later receipt");
        var replay=feudal.armyRequest("feed",Map.of("unit",a.m_20148_().toString(),"seq",1L));check(((Number)replay.get("amount")).intValue()==10,"Old receipt replays after several meals");check(Database.number(s.db.one("SELECT * FROM army_units WHERE id=?",a.m_20148_().toString()),"feed_total")==20,"Old receipt replay preserves total consumption");
        var aa=new FeudalService.Actor(A.toString(),false);var bb=new FeudalService.Actor(B.toString(),false);
        contract=s.contractBuilder.create(aa,cityB,cityA);s.contractBuilder.agree(aa,contract,1);s.contractBuilder.agree(bb,contract,1);s.contractBuilder.sign(aa,contract,1);s.contractBuilder.sign(bb,contract,1);controller.refresh();
        check(!RecruitEvents.canAttack(a,b),"Vassal and liege recruits cannot attack");float health=b.m_21223_();b.m_6469_(a.m_269291_().m_269333_(a),1);check(b.m_21223_()==health,"Actual allied damage is blocked");a.m_6710_(b);check(a.m_5448_()==null,"Native target selection refuses ally");
        s.contractBuilder.exit(aa,contract,false);s.contractBuilder.exit(bb,contract,false);controller.refresh();check(!ArmyHooks.allows(a,b),"Neutral recruits do not attack first");
        Field af=ArmyController.class.getDeclaredField("army");af.setAccessible(true);ArmyService army=(ArmyService)af.get(controller);String na=s.estate(cityA).nation(),nb=s.estate(cityB).nation();army.propose(aa,na==null?cityA:na,"WAR",nb==null?cityB:nb);controller.refresh();check(ArmyHooks.allows(a,b),"Formal war permits enemy combat in PvP territory");
        // Source ownership survives removal/unloading of the shooter.
        var arrow=new net.minecraft.world.entity.projectile.Arrow(level,a);arrow.m_5602_(a);check(arrow.getPersistentData().m_128403_("ArmyShooter"),"Projectile stores durable shooter provenance");
        s.db.execute("UPDATE provision_towns SET credit=0 WHERE town=?",cityA);s.db.execute("UPDATE provision_stock SET quantity=0 WHERE town=?",cityA);a.setHunger(0);a.m_21153_(2);a.getPersistentData().m_128356_("ArmyStarveAt",0);
        System.clearProperty(ProvisionClock.PROPERTY);Bukkit.getScheduler().runTaskLater(this,()->step(this::calendarPaused),60);
    }
    void calendarPaused(){
        check(a.m_6084_()&&a.m_21223_()==2,"Unavailable calendar does not cause starvation damage");long now=System.currentTimeMillis();System.setProperty(ProvisionClock.PROPERTY,"1|"+now+"|0|false|"+now);
        Bukkit.getScheduler().runTaskLater(this,()->step(this::starved),500);
    }
    void starved(){check(!a.m_6084_(),"Recruit dies without city provision despite personal food");check(!Boolean.TRUE.equals(safeAlive(a.m_20148_().toString())),"Roster records death");System.out.println("ARMY_SMOKE_PASS "+checks+" real Mohist/Towny/Recruits checks");Bukkit.shutdown();}
    Object safeAlive(String id){try{return s.db.one("SELECT alive FROM army_units WHERE id=?",id).get("alive");}catch(Exception e){throw new RuntimeException(e);}}
}
