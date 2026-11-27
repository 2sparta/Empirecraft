package ua.townyrecruits.test;
import ua.townyrecruits.*;
import net.minecraft.server.MinecraftServer;

import com.mojang.authlib.GameProfile;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.entities.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.bukkit.*;

/** Test-only Forge mod. Uses actual Towny objects, FakePlayer and live Recruits entities. */
@Mod("townyrecruits_test")
public final class RecruitmentSmokeMod {
    final java.util.concurrent.atomic.AtomicInteger confirmationAccepts=new java.util.concurrent.atomic.AtomicInteger();
    int ticks,checks;boolean done;Bridge b;ServerLevel level;MinecraftServer server;Object access,api;ServerPlayer player;Object town,nation,other;
    static final UUID PLAYER=UUID.fromString("deadcafe-0000-4000-8000-000000000001");
    void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);System.out.println("RECRUIT_CHECK "+label);}
    public RecruitmentSmokeMod(){long now=System.currentTimeMillis();System.setProperty("empirecraft.calendar.wallclock.v1","1|"+now+"|0|false|"+now);MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.register(this);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public void dismissed(com.talhanation.recruits.RecruitEvent.Dismissed event){if(Boolean.getBoolean("recruitment.trace")){var bridge=Bridge.INSTANCE;var unit=event.getRecruit();System.out.println("RECRUIT_DISMISS_DIAGNOSTIC uuid="+unit.m_20148_()+" owner="+unit.getOwnerUUID()+" record="+(bridge==null?null:bridge.data.units.get(unit.m_20148_()))+" snapshotOwner="+(bridge==null?null:bridge.snapshot.residents().get(unit.getOwnerUUID())));try{if(bridge!=null)System.out.println("RECRUIT_HOMES_DIAGNOSTIC cities="+field(bridge.recruitment,"cities")+" home="+bridge.recruitment.state.homes.get(unit.m_20148_()));}catch(Exception ignored){}new Exception("RECRUIT_NATIVE_DISMISS_TRACE").printStackTrace();}}
    void started(net.minecraftforge.event.server.ServerStartedEvent event){var world=event.getServer().m_129783_();var stone=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).m_49966_();for(int cx:new int[]{8,24})for(int x=cx-3;x<=cx+3;x++)for(int z=5;z<=11;z++)world.m_7731_(new net.minecraft.core.BlockPos(x,64,z),stone,3);}
    boolean cancelSpawn;
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void joining(net.minecraftforge.event.entity.EntityJoinLevelEvent event){if(cancelSpawn&&event.getEntity() instanceof AbstractRecruitEntity){cancelSpawn=false;event.setCanceled(true);}}
    static Object call(Object target,String method,Object...args)throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        List<Method> methods=new ArrayList<>(List.of(type.getMethods()));for(Class<?> cursor=type;cursor!=null;cursor=cursor.getSuperclass())methods.addAll(List.of(cursor.getDeclaredMethods()));
        for(Method m:methods)if(m.getName().equals(method)&&m.getParameterCount()==args.length){boolean match=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!box(m.getParameterTypes()[i]).isInstance(args[i]))match=false;if(match){m.setAccessible(true);return m.invoke(target instanceof Class<?>?null:target,args);}}
        throw new NoSuchMethodException(type+"."+method+Arrays.toString(args));
    }
    static Class<?> box(Class<?> c){return c==boolean.class?Boolean.class:c==int.class?Integer.class:c==long.class?Long.class:c==double.class?Double.class:c;}
    static Object field(Object obj,String name)throws Exception {for(Class<?> c=obj.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(obj);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static void field(Object obj,String name,Object value)throws Exception {var f=obj.getClass().getDeclaredField(name);f.setAccessible(true);f.set(obj,value);}
    UUID id(Object obj)throws Exception{return (UUID)call(obj,"getUUID");}
    Set<AbstractRecruitEntity> loaded()throws Exception{return (Set<AbstractRecruitEntity>)field(b,"loaded");}
    static void command(Object source,String root,String text)throws Exception {call(RecruitmentCommands.class,"run",source,root,text);}
    Class<?> townyClass(String name)throws Exception{return Class.forName("com.palmergames.bukkit.towny."+name,true,field(access,"plugin").getClass().getClassLoader());}
    void fake()throws Exception {
        player=FakePlayerFactory.get(level,new GameProfile(PLAYER,"TRTester"));player.m_7678_(8,65,8,0,0);
        for(Class<?> c=server.m_6846_().getClass();c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(List.class.isAssignableFrom(f.getType())&&f.getGenericType().getTypeName().contains("ServerPlayer")){f.setAccessible(true);try{((List)f.get(server.m_6846_())).add(player);}catch(UnsupportedOperationException ignored){}}
        for(Field f:server.m_6846_().getClass().getSuperclass().getDeclaredFields())if(Map.class.isAssignableFrom(f.getType())){f.setAccessible(true);Object obj=f.get(server.m_6846_());if(obj instanceof Map map){String name=f.getName();if(name.equals("f_11244_")||name.equals("playersByUUID"))map.put(PLAYER,player);}}
        // Locate the actual mapped player index by verifying the public lookup.
        if(server.m_6846_().m_11259_(PLAYER)==null)for(Class<?> c=server.m_6846_().getClass();c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(Map.class.isAssignableFrom(f.getType())){f.setAccessible(true);var map=(Map)f.get(server.m_6846_());if(map!=null&&f.getGenericType().getTypeName().contains("java.util.UUID")&&f.getGenericType().getTypeName().contains("ServerPlayer"))map.put(PLAYER,player);}
        check(server.m_6846_().m_11259_(PLAYER)==player,"real FakePlayer in native player index");
    }
    Object createTown(Object ds,Object api,String name,String leader,UUID id,int chunk)throws Exception {
        call(call(townyClass("TownyUniverse"),"getInstance"),"newTown",name);Object t=call(api,"getTown",name);call(t,"setUUID",UUID.nameUUIDFromBytes(name.getBytes()));call(call(townyClass("TownyUniverse"),"getInstance"),"registerTownUUID",t);Object r=call(ds,"newResident",leader,id);call(r,"setTown",t);call(t,"setMayor",r);
        Object tw=call(api,"getTownyWorld",Bukkit.getWorlds().get(0).getName());Object block=townyClass("object.TownBlock").getConstructor(int.class,int.class,townyClass("object.TownyWorld")).newInstance(chunk,0,tw);call(block,"setTown",t);call(t,"setHomeBlock",block);call(t,"setSpawn",new Location(Bukkit.getWorlds().get(0),chunk*16+8,65,8));call(ds,"saveResident",r);call(ds,"saveTownBlock",block);call(ds,"saveTown",t);return t;
    }
    void initialize()throws Exception {
        server=(MinecraftServer)field(b,"server");access=field(b,"towny");api=field(access,"api");level=server.m_129783_();level.m_8602_(0,0,true);level.m_8602_(1,0,true);fake();
        Object universe=call(townyClass("TownyUniverse"),"getInstance"),ds=call(universe,"getDataSource");
        org.bukkit.plugin.Plugin feudal=Bukkit.getPluginManager().getPlugin("EmpirecraftFeudal");
        if(feudal!=null){Bukkit.getScheduler().cancelTasks(feudal);Object bridge=field(feudal,"towny");Object kingdoms=field(bridge,"kingdoms");field(kingdoms,"applying",true);if(kingdoms instanceof org.bukkit.event.Listener listener)org.bukkit.event.HandlerList.unregisterAll(listener);}

        if(Boolean.getBoolean("recruitment.resume")){town=call(api,"getTown","HireTown");nation=call(town,"getNationOrNull");other=call(api,"getTown","OtherTown");return;}
        town=createTown(ds,api,"HireTown","TRTester",PLAYER,0);call(ds,"newNation","HireNation",UUID.nameUUIDFromBytes("HireNation".getBytes()));nation=call(api,"getNation","HireNation");call(town,"setNation",nation);call(nation,"setCapital",town);
        for(int i=1;i<100;i++){var r=call(ds,"newResident","TRMember"+i,UUID.nameUUIDFromBytes(("member"+i).getBytes()));call(r,"setTown",town);call(ds,"saveResident",r);}call(ds,"saveTown",town);call(ds,"saveNation",nation);
        UUID oldKing=UUID.fromString("deadcafe-0000-4000-8000-000000000002");other=createTown(ds,api,"OtherTown","LegacyKing",oldKing,1);call(ds,"newNation","LegacyNation",UUID.nameUUIDFromBytes("LegacyNation".getBytes()));Object old=call(api,"getNation","LegacyNation");call(other,"setNation",old);call(old,"setCapital",other);call(ds,"saveNation",old);
        String legacy="tn_"+id(old).toString().replace("-","").substring(0,8);FactionEvents.recruitsFactionManager.addTeam(legacy,"LegacyNation",oldKing,"LegacyKing",new CompoundTag(),(byte)0,net.minecraft.ChatFormatting.WHITE);var faction=FactionEvents.recruitsFactionManager.getFactionByStringID(legacy);faction.setMaxNPCs(77);faction.setMaxNPCsPerPlayer(12);
        field(b,"forceSync",true);call(b,"refreshNow");
        if(feudal!=null){Bukkit.getScheduler().cancelTasks(feudal);Object bridge=field(feudal,"towny"),service=field(feudal,"service");call(service,"sync",call(bridge,"snapshot"));}
    }
    void gold(int n){player.m_150109_().m_6836_(0,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:gold_ingot")),n));}
    int gold(){return player.m_150109_().m_8020_(0).m_41613_();}
    org.bukkit.entity.Player bp()throws Exception{return (org.bukkit.entity.Player)call(player,"getBukkitEntity");}
    void verify()throws Exception {
        UUID n=id(nation),t=id(town);var r=b.recruitment;var f=FactionEvents.recruitsFactionManager.getFactionByStringID(Policy.team(n));
        check(f!=null&&f.getPlayers()==100,"100 actual Towny residents synchronized to one faction");
        check(f.getMaxPlayers()==0,"native faction player limit disabled");
        Object old=call(api,"getNation","LegacyNation");UUID oldId=id(old);
        check(Policy.team(oldId).startsWith("tn_")&&b.limit(oldId)==77&&r.state.nations.get(oldId).perPlayer==12,"legacy faction ID and 77/12 overrides preserved");
        if(Boolean.getBoolean("recruitment.resume")){
            check(b.limit(n)==3&&r.state.nations.get(n).perPlayer==2,"limits persisted over native server restart");
            check(r.state.balance(t).compareTo(new BigDecimal("8.5"))==0,"decimal development persisted over native restart");
            check(r.state.nations.get(n).shieldman,"shield unlock persisted over restart");
            check(b.count(n)==2,"unloaded recruits counted after restart");
            bp().addAttachment((org.bukkit.plugin.Plugin)field(access,"plugin"),"towny.command.nation.delete",true);
            bp().addAttachment((org.bukkit.plugin.Plugin)field(access,"plugin"),"towny.command.nation",true);
            check(Bukkit.dispatchCommand(bp(),"towny:nation delete"),"native nation delete command dispatched");
            Class<?> handler=townyClass("confirmations.ConfirmationHandler");
            check(Boolean.TRUE.equals(call(handler,"hasConfirmation",bp())),"real nation deletion creates a pending confirmation");
            Class<?> compat=Class.forName("ua.townyrecruits.TownyCommandCompat");
            check(((Number)call(compat,"confirm",player.m_20203_(),true)).intValue()==1,"Towny confirmation accepted through compatibility command");
            return;
        }
        check(b.limit(n)==15&&r.state.nations.get(n).perPlayer==15,"new nation receives 15/15");check(((Number)call(RecruitmentCommands.class,"run",server.m_129893_(),"trbridge","status")).intValue()==1,"legacy detailed faction and diplomacy diagnostics remain available");
        Object resident=call(api,"getResident","TRMember99");call(resident,"removeTown");field(b,"forceSync",true);call(b,"refreshNow");check(f.getPlayers()==99,"offline resident leaving is removed from the faction");call(resident,"setTown",town);field(b,"forceSync",true);call(b,"refreshNow");check(f.getPlayers()==100,"offline resident joining is restored without invitation");call(resident,"save");call(town,"save");
        check(((Number)call(RecruitmentCommands.class,"run",server.m_129893_(),"tarmyadmin","dev info Львів")).intValue()==0,"Ukrainian command argument returns a normal not-found result without hanging");check(((Number)call(RecruitmentCommands.class,"run",server.m_129893_(),"tarmyadmin","dev add HireTown 10")).intValue()==0,"removed development command is rejected with the migration message");
        command(server.m_129893_(),"trbridge","limit HireNation 3 2");check(b.limit(n)==3&&r.state.nations.get(n).perPlayer==2,"legacy console limit command updates single ledger");
        gold(20);var denied=bp().addAttachment((org.bukkit.plugin.Plugin)field(access,"plugin"),"townyrecruits.hire",false);r.purchase(bp(),"recruit");check(gold()==20&&b.count(n)==0,"explicit permission denial prevents hiring without charging");bp().removeAttachment(denied);r.purchase(bp(),"shieldman");check(b.count(n)==0&&gold()==20,"locked shieldman consumes no gold or development");
        r.state.development(t,"set",BigDecimal.ZERO);r.purchase(bp(),"recruit");check(gold()==20&&b.count(n)==0,"insufficient development consumes nothing");r.state.development(t,"set",BigDecimal.TEN);
        gold(0);r.purchase(bp(),"recruit");check(b.count(n)==0&&r.state.balance(t).compareTo(BigDecimal.TEN)==0,"insufficient gold consumes nothing");gold(20);
        cancelSpawn=true;r.purchase(bp(),"recruit");check(b.count(n)==0&&gold()==20&&r.state.balance(t).compareTo(BigDecimal.TEN)==0,"cancelled real entity spawn refunds gold, development and cap slot");
        cancelSpawn=false;r.purchase(bp(),"recruit");check(b.count(n)==1&&gold()==15,"real recruit purchase costs five gold (count="+b.count(n)+" gold="+gold()+")");check(r.state.balance(t).compareTo(new BigDecimal("9.5"))==0,"purchase consumes exactly 0.5 development");
        var unit=loaded().stream().filter(AbstractRecruitEntity::getIsOwned).findFirst().orElseThrow();check(unit instanceof RecruitEntity,"spawned entity is real Villager Recruits RecruitEntity");check(Math.abs(unit.m_20185_()-8)<=6.5&&Math.abs(unit.m_20186_()-65)<=2&&Math.abs(unit.m_20189_()-8)<=6.5,"recruit spawns near native town spawn");
        var nativeBox=((org.bukkit.entity.Entity)unit.getClass().getMethod("getBukkitEntity").invoke(unit)).getBoundingBox();
        check(!nativeBox.overlaps(bp().getBoundingBox()),"new recruit does not overlap buyer");
        check(unit.m_5647_()!=null&&unit.m_5647_().m_5758_().equals(Policy.team(n)),"purchased recruit belongs to nation faction");check(t.equals(call(r,"home",unit)),"town of origin is persisted");
        check(!unit.canEatItemStack(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:bread")))),"native inventory food bypass is blocked");
        check(!unit.hire(player,null,false),"native hiring cannot bypass development and GUI");
        command(server.m_129893_(),"tarmyadmin","unlock shieldman HireNation");r.purchase(bp(),"shieldman");
        check(b.count(n)==2&&gold()==5,"real shieldman purchase costs ten gold");check(loaded().stream().anyMatch(e->e.getIsOwned()&&e.getClass().getSimpleName().contains("Shield")),"shieldman is the native Recruits type");
        check(r.state.balance(t).compareTo(new BigDecimal("8.5"))==0,"shield purchase consumes exactly one development");
        org.bukkit.plugin.Plugin feudal=Bukkit.getPluginManager().getPlugin("EmpirecraftFeudal");
        if(feudal!=null){
            Object controller=field(feudal,"armyController"),army=field(controller,"army"),db=field(army,"db"),provisions=field(controller,"provisions");
            String home="t:"+t,foreign="t:"+id(other);call(provisions,"settleArmy",home);call(provisions,"settleArmy",foreign);
            Class<?> inventoryBridge=Class.forName("ua.empirecraft.feudal.GoldInventoryBridge",true,feudal.getClass().getClassLoader());
            String encoded=(String)call(inventoryBridge,"encode",(Object)new org.bukkit.inventory.ItemStack[]{new org.bukkit.inventory.ItemStack(org.bukkit.Material.BREAD)});
            for(String city:List.of(home,foreign))call(db,"execute","MERGE INTO provision_stock KEY(town,slot) VALUES(?,0,?,20,5,64,1)",new Object[]{city,encoded});
            unit.setHunger(40);call(r,"feed",unit);check(unit.getHunger()==50,"native Feudal provision feed increases recruit hunger by ten");
            Map receipt=(Map)call(army,"unit",unit.m_20148_().toString());check(((Number)receipt.get("feed_total")).longValue()==10&&receipt.get("home").equals(home),"native food receipt records the fixed town and paid nutrition");
            Map foreignStock=(Map)call(db,"one","SELECT quantity FROM provision_stock WHERE town=? AND slot=0",new Object[]{foreign});check(((Number)foreignStock.get("quantity")).intValue()==20,"foreign town stock remains untouched");
            call(db,"execute","UPDATE army_units SET home=? WHERE id=?",new Object[]{foreign,unit.m_20148_().toString()});call(r,"feed",unit);receipt=(Map)call(army,"unit",unit.m_20148_().toString());check(receipt.get("home").equals(home),"external supply reassignment cannot change the town of origin");
        }
        if(feudal!=null){call(feudal,"townMenuOpen",bp());boolean entry=false;for(var item:bp().getOpenInventory().getTopInventory().getContents())if(item!=null&&item.hasItemMeta()&&item.getItemMeta().getDisplayName().equals("Рекрути"))entry=true;check(entry,"recruitment entry appears in the actual Feudal Towny menu");bp().closeInventory();}
        Object screen=townyClass("object.statusscreens.StatusScreen").getConstructor(org.bukkit.command.CommandSender.class).newInstance(Bukkit.getConsoleSender());call(screen,"addComponentOf","native-town-line","Місто: HireTown");
        Object status=townyClass("event.statusscreen.TownStatusScreenEvent").getConstructor(townyClass("object.statusscreens.StatusScreen"),townyClass("object.Town")).newInstance(screen,town);Bukkit.getPluginManager().callEvent((org.bukkit.event.Event)status);
        Collection keys=(Collection)call(screen,"getComponentKeys");check(keys.containsAll(List.of("native-town-line","townyrecruits-development","townyrecruits-army")),"native Towny status retains original lines and adds development and army");
        call(field(r,"menu"),"open",bp());check(bp().getOpenInventory().getTopInventory().getSize()==27&&bp().getOpenInventory().getTopInventory().getItem(4)!=null,"real recruitment GUI opens with current balances");bp().closeInventory();
        r.limits(n,2,1);r.purchase(bp(),"recruit");check(b.count(n)==2&&gold()==5,"lowered cap preserves existing army and blocks new hires without charging");r.limits(n,3,2);
        Set<UUID> prior=new HashSet<>();loaded().forEach(e->prior.add(e.m_20148_()));r.purchase(bp(),"recruit");check(b.count(n)==3,"third recruit occupies final national slot");var third=loaded().stream().filter(e->!prior.contains(e.m_20148_())&&e.getIsOwned()).findFirst().orElseThrow();third.m_6074_();check(b.count(n)==2,"real recruit death immediately releases its slot");call(b,"updateUnit",third);check(b.count(n)==2,"dying entity cannot be counted again by AI updates");r.state.development(t,"add",new BigDecimal("0.5"));
        feedback();
    }
    void feedback()throws Exception {
        UUID n=id(nation),t=id(town);var r=b.recruitment;
        String display=call(nation,"getFormattedName").toString();
        command(server.m_129893_(),"trbridge","limit "+com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(display)+" 3 2");
        check(b.limit(n)==3,"actual formatted Towny nation name resolves");
        Object menu=field(r,"menu");r.limits(n,2,2);call(menu,"open",bp());
        var inventory=bp().getOpenInventory().getTopInventory();
        check(inventory.getItem(11).getType()==Material.BARRIER,"full cap renders disabled recruit button");
        check(!inventory.getItem(11).getItemMeta().getPersistentDataContainer().has(new NamespacedKey("townyrecruits","recruitment"),org.bukkit.persistence.PersistentDataType.STRING),"disabled button has no purchase action");
        bp().closeInventory();r.limits(n,3,2);
        org.bukkit.plugin.Plugin feudal=Bukkit.getPluginManager().getPlugin("EmpirecraftFeudal");
        if(feudal!=null){
            Object provisions=field(feudal,"provisions");
            call(provisions,"onCommand",bp(),null,"provision",new String[0]);
            var stock=bp().getOpenInventory().getTopInventory();
            check(stock.getHolder()!=null&&stock.getHolder().getClass().getName().endsWith("ProvisionController$Chest"),"native provision screen opens");
            Class<?> view=Class.forName("ua.townyrecruits.RecruitProvisionView");
            call(view,"refresh",r,stock);
            check(stock.getItem(47)!=null&&stock.getItem(47).getItemMeta().getDisplayName().contains("Загальна поживність"),"nutrition total is explicitly labelled");
            check(stock.getItem(48)!=null&&stock.getItem(48).getItemMeta().getDisplayName().contains("Залишок поживності"),"partial food credit remains visible");
            check(stock.getItem(50)!=null&&stock.getItem(50).getItemMeta().getDisplayName().contains("Постачання"),"army supply needs are shown");
            bp().closeInventory();
        }
        // No damage bypass: invoke the same owner-authorized operation used by the confirmation GUI.
        var owned=loaded().stream().filter(e->e.getIsOwned()&&PLAYER.equals(e.getOwnerUUID())).findFirst().orElseThrow();
        UUID dismissed=owned.m_20148_();int before=b.count(n),money=gold();BigDecimal development=r.state.balance(t);
        r.dismiss(bp(),dismissed);
        check(!owned.getIsOwned()&&b.count(n)==before-1,"owner dismissal neutralizes recruit and releases one slot");
        check(gold()==money&&r.state.balance(t).compareTo(development)==0,"dismissal never refunds purchase resources");
        boolean duplicate=false;try{r.dismiss(bp(),dismissed);}catch(Exception expected){duplicate=true;}
        check(duplicate&&b.count(n)==before-1,"repeated dismissal cannot release another slot");
        var reopened=new RecruitmentState((java.nio.file.Path)field(r.state,"file"));
        check(reopened.dismissed.contains(dismissed),"dismissal is durably saved before restart");
        // The earlier paid recruit died without a refund, leaving this fixture
        // with no gold. Fund this separate purchase rather than bypassing its cost.
        gold(money+5);
        r.purchase(bp(),"recruit");check(b.count(n)==before&&gold()==money,"vacated slot permits one new paid recruit");
        // Restore fixture's established restart expectations after testing another paid purchase.
        r.state.development(t,"set",development);gold(money);
        Class<?> confirmation=townyClass("confirmations.Confirmation"),handler=townyClass("confirmations.ConfirmationHandler");
        Class<?> compat=Class.forName("ua.townyrecruits.TownyCommandCompat");
        Object builder=call(confirmation,"runOnAccept",(Runnable)()->confirmationAccepts.incrementAndGet());
        call(builder,"sendTo",bp());
        check(Boolean.TRUE.equals(call(handler,"hasConfirmation",bp())),"native Towny confirmation is present");
        check(((Number)call(compat,"confirm",player.m_20203_(),true)).intValue()==1,"explicit trconfirm accepts own native request");
        check(!Boolean.TRUE.equals(call(handler,"hasConfirmation",bp())),"accepted confirmation removed immediately");
        check(((Number)call(compat,"confirm",player.m_20203_(),true)).intValue()==0,"replayed confirmation has no effect");
        Object cancelled=call(confirmation,"runOnAccept",(Runnable)()->confirmationAccepts.addAndGet(100));
        call(cancelled,"sendTo",bp());
        check(((Number)call(compat,"confirm",player.m_20203_(),false)).intValue()==1,"trcancel revokes own native request");
        check(!Boolean.TRUE.equals(call(handler,"hasConfirmation",bp())),"cancelled request removed");
    }

    void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||done||Bridge.INSTANCE==null)return;ticks++;b=Bridge.INSTANCE;
        if(ticks==80)try{initialize();}catch(Throwable ex){fail(ex);}
        if(ticks==160&&!done)try{call(b,"refreshNow");verify();}catch(Throwable ex){fail(ex);}
        if(ticks==164&&!done)try{
            if(Boolean.getBoolean("recruitment.resume")){
                call(b,"refreshNow");
                check(call(api,"getNation","HireNation")==null,"confirmed native command actually deletes nation");
                check(FactionEvents.recruitsFactionManager.getFactionByStringID(Policy.team(id(nation)))==null,"confirmed nation deletion removes managed faction");
            }else check(confirmationAccepts.get()==1,"native scheduled confirmation executes exactly once; cancelled action never executes");
            System.out.println("RECRUIT_SMOKE_PASS phase="+(Boolean.getBoolean("recruitment.resume")?"restart":"initial")+" checks="+checks);
            done=true;server.m_7570_(false);
        }catch(Throwable ex){fail(ex);}
    }
    void fail(Throwable ex){done=true;System.out.println("RECRUIT_SMOKE_FAIL checks="+checks);ex.printStackTrace();if(server!=null)server.m_7570_(false);}
}
