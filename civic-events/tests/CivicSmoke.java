package ua.empirecraft.civictest;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Actual Starcatcher, Mohist, Towny, Bukkit item journal and production SQL, in a disposable world. */
@Mod("empirecraft_civic_smoke")
public final class CivicSmoke {
    MinecraftServer server;ServerLevel level;int ticks,checks;Object plugin,towny,db,markets,fishing,api,universe,town;ServerPlayer mayor,angler;String event,market,world;Properties saved=new Properties();
    public CivicSmoke(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent e){server=e.getServer();level=server.overworld();}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static String req(){return UUID.randomUUID().toString();}
    static Object field(Object target,String name)throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static Object call(Object target,String name,Object...args)throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())for(Method method:type.getDeclaredMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){boolean matches=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!method.getParameterTypes()[i].isInstance(args[i])&&!method.getParameterTypes()[i].isPrimitive())matches=false;if(matches){method.setAccessible(true);return method.invoke(target,args);}}throw new NoSuchMethodException(target.getClass()+"#"+name);}
    Class<?> feudal(String name)throws Exception{return Class.forName("ua.empirecraft.feudal."+name,true,plugin.getClass().getClassLoader());}
    void sql(String statement,Object...args)throws Exception {call(db,"execute",statement,args);}
    Map<String,Object> row(String statement,Object...args)throws Exception{return (Map<String,Object>)call(db,"one",statement,args);}
    Object actor(ServerPlayer p)throws Exception{return feudal("FeudalService$Actor").getConstructor(String.class,boolean.class).newInstance(p.getUUID().toString(),false);}
    Object bukkit(ServerPlayer p)throws Exception{return Class.forName("org.bukkit.Bukkit").getMethod("getPlayer",UUID.class).invoke(null,p.getUUID());}
    Object place(double x,double y,double z)throws Exception{return feudal("CityMarketService$Place").getConstructor(String.class,double.class,double.class,double.class).newInstance(world,x,y,z);}
    Object goods()throws Exception{return Enum.valueOf((Class)feudal("CityMarketService$Department"),"GOODS");}
    long balance(ServerPlayer p)throws Exception {var row=row("SELECT balance FROM accounts WHERE owner=? AND code='FSH'","p:"+p.getUUID());return row==null?0:((Number)row.get("balance")).longValue();}
    long score()throws Exception{return ((Number)row("SELECT score FROM cm_fish_scores WHERE event=? AND person=?",event,angler.getUUID().toString()).get("score")).longValue();}
    ServerPlayer player(String name,boolean leader)throws Exception {
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
        p.connection.connection.channel=new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){@Override public void write(io.netty.channel.ChannelHandlerContext c,Object m,io.netty.channel.ChannelPromise promise){io.netty.util.ReferenceCountUtil.release(m);promise.trySuccess();}});
        p.connection.connection.channel.attr(net.minecraft.network.Connection.ATTRIBUTE_PROTOCOL).set(net.minecraft.network.ConnectionProtocol.PLAY);p.setNoGravity(true);p.setPos(2.5,64,2.5);level.addNewPlayer(p);
        for(Field f:net.minecraft.server.players.PlayerList.class.getDeclaredFields()){f.setAccessible(true);if(f.getType()==List.class)try{((List)f.get(server.getPlayerList())).add(p);break;}catch(UnsupportedOperationException ignored){}}
        for(Field f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(f.getType())&&f.getGenericType().getTypeName().contains("java.util.UUID")&&f.getGenericType().getTypeName().contains("ServerPlayer")){f.setAccessible(true);((Map)f.get(server.getPlayerList())).put(p.getUUID(),p);}
        Object resident=call(api,"getResident",p.getUUID());if(resident==null)resident=call(call(universe,"getDataSource"),"newResident",name,p.getUUID());call(resident,"setTown",town);if(leader)call(town,"setMayor",resident);
        var attachment=call(bukkit(p),"addAttachment",towny);call(attachment,"setPermission","empirecraft.feudal.use",true);return p;
    }
    void setup()throws Exception {
        var manager=Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);plugin=call(manager,"getPlugin","EmpirecraftFeudal");towny=call(manager,"getPlugin","Towny");check(plugin!=null&&Boolean.TRUE.equals(call(plugin,"isEnabled")),"actual Feudal plugin is enabled");
        var loader=towny.getClass().getClassLoader();api=Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,loader).getMethod("getInstance").invoke(null);universe=Class.forName("com.palmergames.bukkit.towny.TownyUniverse",true,loader).getMethod("getInstance").invoke(null);
        town=call(api,"getTown","FishingTown");if(town==null){call(universe,"newTown","FishingTown");town=call(api,"getTown","FishingTown");}
        mayor=player("CivicMayor",true);angler=player("CivicAngler",false);call(field(plugin,"towny"),"sync");call(field(plugin,"politicalCommands"),"refresh");
        Object bukkitWorld=call(level,"getWorld");world=call(bukkitWorld,"getUID").toString();Object townWorld=call(api,"getTownyWorld",call(bukkitWorld,"getName"));Class<?> blockType=Class.forName("com.palmergames.bukkit.towny.object.TownBlock",true,loader);
        Object block;try{block=call(townWorld,"getTownBlock",0,0);}catch(InvocationTargetException absent){block=blockType.getConstructor(int.class,int.class,townWorld.getClass()).newInstance(0,0,townWorld);call(universe,"addTownBlock",block);}call(block,"setTown",town);
        level.getChunk(0,0);level.setBlock(new BlockPos(2,64,2),Blocks.STONE.defaultBlockState(),2);level.setBlock(new BlockPos(3,63,3),Blocks.STONE.defaultBlockState(),2);level.setBlock(new BlockPos(3,64,3),Blocks.OAK_SIGN.defaultBlockState(),2);
        db=field(plugin,"database");markets=field(field(plugin,"cityMarketCommands"),"m");fishing=field(markets,"fishing");check(Boolean.TRUE.equals(field(fishing,"nativeReady")),"actual civic addon handshake completed");
    }
    Object fish()throws Exception {return fish("cod");}
    Object fish(String name)throws Exception {
        ResourceKey key=(ResourceKey)Class.forName("com.wdiscute.starcatcher.Starcatcher").getField("FISH_REGISTRY_KEY").get(null);Registry registry=level.registryAccess().registryOrThrow(key);
        for(Object value:registry){Object info=call(value,"catchInfo");if(call(info,"fishEntryType").toString().equals("FISH")&&registry.getKey(value).toString().contains(name))return value;}throw new AssertionError("No actual cod fish entry");
    }
    Entity bobber(Object fish,double x)throws Exception {
        EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("starcatcher","fishing_bob"));check(type!=null,"actual Starcatcher bobber is registered");
        Entity entity=type.create(level);((Projectile)entity).setOwner(angler);entity.setPos(x,64,5);entity.getClass().getField("fpToFish").set(entity,fish);
        Object skin=Class.forName("com.wdiscute.starcatcher.registry.tackleskin.BaseTackleSkin").getConstructor().newInstance();entity.getClass().getField("tackleSkin").set(entity,skin);
        Object key=Class.forName("com.wdiscute.starcatcher.fish.FishApi").getMethod("getKey",net.minecraft.world.level.Level.class,fish.getClass()).invoke(null,level,fish);entity.getClass().getField("rlToAwardUponFishingComplete").set(entity,key);
        level.addFreshEntity(entity);var a=Class.forName("com.wdiscute.starcatcher.registry.SCDataAttachments");Object attachment=a.getMethod("get",Entity.class,java.util.function.Supplier.class).invoke(null,angler,a.getField("FISHING_BOB").get(null));call(attachment,"setUuid",angler,entity.getUUID());return entity;
    }
    void complete(boolean success)throws Exception {Class.forName("com.wdiscute.starcatcher.fish.FishApi").getMethod("spawnFishFromPlayerFishing",ServerPlayer.class,boolean.class,int.class,boolean.class,boolean.class,int.class).invoke(null,angler,success,100,false,false,1);}
    void dimensions()throws Exception {
        ServerLevel europe=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new ResourceLocation("minecraft","world_europe")));
        check(europe!=null,"actual custom world_europe dimension is loaded");Object fp=fish();Object restriction=((List<?>)call(fp,"restrictions")).stream().filter(o->o.getClass().getSimpleName().equals("DimensionRestriction")).findFirst().orElseThrow();
        Object context=Enum.valueOf((Class)Class.forName("com.wdiscute.starcatcher.registry.fishrestrictions.AbstractFishRestriction$Context"),"FISHING");
        check(((Number)call(restriction,"adjustChance",0,europe,fp,angler,ItemStack.EMPTY,context)).intValue()==0,"actual Starcatcher cod dimension restriction accepts world_europe");
        check(((Number)call(restriction,"adjustChance",0,level,fp,angler,ItemStack.EMPTY,context)).intValue()==0,"original Overworld fishing remains valid");
        var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);if(nether!=null)check(((Number)call(restriction,"adjustChance",0,nether,fp,angler,ItemStack.EMPTY,context)).intValue()<0,"canonical dimension groups still reject Overworld fish in the Nether");
    }
    void first()throws Exception {
        String city="t:"+call(town,"getUUID");market=call(markets,"create",actor(mayor),req(),city,world,"Fishing market","Native civic integration").toString();
        sql("UPDATE cm_markets SET state='OPEN',opened=TRUE WHERE id=?",market);sql("INSERT INTO cm_claims VALUES(?,?,0,0)",market,world);sql("INSERT INTO cm_departments(market,kind,building,x1,y1,z1,x2,y2,z2,cx,cy,cz,material,state) VALUES(?,'GOODS',?,0,60,0,15,80,15,2,64,2,'STONE','OPEN')",market,req());
        sql("INSERT INTO currencies(code,name,symbol,issuer,supply,backing) VALUES('FSH','Fishing fixture','F',?,110000,'GOLD')",city);sql("INSERT INTO accounts VALUES(?,'FSH',100000)","e:"+city);sql("INSERT INTO accounts VALUES(?,'FSH',10000)","p:"+angler.getUUID());sql("INSERT INTO cm_permits VALUES(?,'FSH',TRUE,TRUE,TRUE)",market);
        Object notices=field(markets,"notices");call(notices,"bind",actor(mayor),req(),market,place(3,64,3));call(notices,"configure",actor(mayor),req(),market,100L,"FSH",true);long version=((Number)call(notices,"settings",market).getClass().getMethod("get",Object.class).invoke(call(notices,"settings",market),"version")).longValue();
        String notice=call(notices,"submit",actor(angler),req(),market,place(2.5,64,2.5),"Fishing rod","An actual fishing rod",7,version).toString();check(balance(angler)==9900,"pending notice reserves actual wallet money");check(((List)call(notices,"publicEntries",market,place(2.5,64,2.5),1)).isEmpty(),"pending text is not public");call(notices,"review",actor(mayor),req(),notice,true,"");check(((List)call(notices,"publicEntries",market,place(2.5,64,2.5),1)).size()==1,"mayor approval publishes on physical sign");
        Object screens=field(plugin,"marketScreens");call(screens,"openSection",bukkit(angler),"notice");var opens=(List<Map<String,Object>>)call(plugin,"marketPoll");String session=opens.get(0).get("session").toString();var response=(Map)call(plugin,"marketUi",angler.getUUID(),session,"view","goods","",List.of(),"",1);check(response.get("section").equals("notice")&&!response.containsKey("error"),"unchanged native Bridge opens notice section from its initial goods request");check(((List<Map>)response.get("forms")).stream().anyMatch(f->f.get("id").equals("board-post")),"native screen exposes real posting forms");
        Object rule=Enum.valueOf((Class)feudal("MarketFishing$Rule"),"COUNT");event=call(fishing,"create",actor(mayor),req(),market,place(2.5,64,2.5),"Native fishing","Genuine catches",System.currentTimeMillis()+600000,10,rule).toString();call(fishing,"entryFee",actor(mayor),req(),event,250L,"FSH");call(fishing,"money",actor(mayor),req(),event,1,"FSH",1000L,true);
        mayor.getInventory().setItem(0,new ItemStack(Items.DIAMOND,2));Object commands=field(plugin,"cityMarketCommands");String spec=call(commands,"prizeSpec",bukkit(mayor)).toString();Object change=call(commands,"take",bukkit(mayor),spec,2L);String op=call(fishing,"items",actor(mayor),req(),event,1,spec,2L,place(2.5,64,2.5),change).toString();call(commands,"apply",op);check(mayor.getInventory().getItem(0).isEmpty(),"real Bukkit inventory journal removed prize diamonds");
        call(fishing,"announce",actor(mayor),req(),event);call(fishing,"join",actor(angler),req(),event);call(fishing,"join",actor(angler),req(),event);check(balance(angler)==9650,"tournament entry charges only once");
        for(int i=0;i<60;i++)sql("INSERT INTO cm_fairs(id,market,name,starts,ends,state,kind) VALUES(?,?,'Other city fair',?,?,'ANNOUNCED','FAIR')",req(),"other-market",System.currentTimeMillis(),System.currentTimeMillis()+600000);
        call(screens,"openSection",bukkit(mayor),"notice");var mayorOpen=(List<Map<String,Object>>)call(plugin,"marketPoll");String mayorSession=mayorOpen.get(0).get("session").toString();
        var cityBoard=(Map)call(plugin,"marketUi",mayor.getUUID(),mayorSession,"view","goods","",List.of(),"",1);check(((List<Map>)cityBoard.get("rows")).stream().anyMatch(r->event.equals(r.get("id"))),"sixty earlier foreign fairs cannot hide the host city's tournament row");
        ((Map)field(screens,"throttles")).remove(mayor.getUUID());var rules=(Map)call(plugin,"marketUi",mayor.getUUID(),mayorSession,"civicRead","notice","EVENT",List.of(event),"",1);check("FISHING".equals(rules.get("readKind"))&&Boolean.TRUE.equals(rules.get("readCanJoin")),"board tournament opens its reader and permits a new entrant");check(((List<String>)rules.get("messages")).stream().anyMatch(t->t.contains("2.50 FSH")),"native tournament rules expose the exact entrance fee");
        Object contact=Class.forName("ua.empirecraft.townymap.contact.ContactMail").getMethod("data").invoke(null);
        var mailbox=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("contact","white_mailbox"));var half=net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;
        level.setBlock(new BlockPos(8,64,8),mailbox.defaultBlockState().setValue(half,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER),2);level.setBlock(new BlockPos(8,65,8),mailbox.defaultBlockState().setValue(half,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
        call(contact,"resetMailboxContents",angler.getUUID());call(contact,"setMailboxData",angler.getUUID(),level.dimension(),new BlockPos(8,65,8));
        Object mail=field(field(markets,"fairs"),"mail");check(field(mail,"gateway")!=null,"actual Contact invitation gateway is installed");String sample=call(mail,"preview",actor(mayor),event,place(2.5,64,2.5)).toString();check(sample!=null,"announced tournament produces an actual Contact invitation sample");
        sql("UPDATE cm_fairs SET starts=?,ends=? WHERE id=?",System.currentTimeMillis()-1000,System.currentTimeMillis()+600000,event);Object fp=fish();bobber(fp,5);complete(false);check(score()==0,"failed real Starcatcher minigame does not score");
        bobber(fp,40);complete(true);check(score()==0,"actual catch outside host Towny land does not score");bobber(fish("cloudfin"),5);complete(true);check(score()==1,"genuine weightless cloudfin counts without inventing weight");Entity bobber=bobber(fp,5);complete(true);check(score()==2,"actual completed Starcatcher catch reaches SQL through the production Mixin");
        Class.forName("com.wdiscute.starcatcher.tournament.TournamentHandler").getMethod("addScore",net.minecraft.world.entity.player.Player.class,fp.getClass(),boolean.class,float.class).invoke(null,angler,fp,false,50f);check(score()==2,"replaying scoring after bobber consumption adds nothing");
        check(ForgeRegistries.ITEMS.getValue(new ResourceLocation("starcatcher","chorus_minnow"))==null||ForgeRegistries.ITEMS.getValue(new ResourceLocation("starcatcher","chorus_minnow"))==Items.AIR,"new End fish stays excluded in upgraded native registry");
        sql("UPDATE cm_fairs SET ends=? WHERE id=?",System.currentTimeMillis()-1,event);call(markets,"tick");call(markets,"tick");check(balance(angler)==10900,"winner receives reserved prize and entry pool once");
        ((Map)field(screens,"throttles")).remove(angler.getUUID());var finished=(Map)call(plugin,"marketUi",angler.getUUID(),session,"civicRead","fishing","FISHING",List.of(event),"",1);check(Boolean.FALSE.equals(finished.get("readCanJoin"))&&!finished.containsKey("error"),"finished tournament remains readable and disables entry");check(((Number)row("SELECT SUM(quantity) AS n FROM cm_assets WHERE owner=? AND category='FISH_PRIZE'","p:"+angler.getUUID()).get("n")).longValue()==2,"item prize exists in persistent claimable assets");
        Object source=call(universe,"getDataSource");call(source,"saveTown",town);for(Object resident:(Collection<?>)call(api,"getResidents"))call(source,"saveResident",resident);Object tw=call(api,"getTownyWorld",call(call(level,"getWorld"),"getName"));for(Object block:(Collection<?>)call(tw,"getTownBlocks"))call(source,"saveTownBlock",block);
        saved.setProperty("event",event);saved.setProperty("market",market);try(var out=Files.newOutputStream(Path.of("civic.properties"))){saved.store(out,"Actual persistent event");}
    }
    void second()throws Exception {
        try(var in=Files.newInputStream(Path.of("civic.properties"))){saved.load(in);}event=saved.getProperty("event");market=saved.getProperty("market");check(score()==2,"actual server restart restores fishing score");check(balance(angler)==10900,"actual server restart preserves payout without duplication");call(markets,"tick");check(balance(angler)==10900,"settlement tick after restart is idempotent");check(((Number)row("SELECT SUM(quantity) AS n FROM cm_assets WHERE owner=? AND category='FISH_PRIZE'","p:"+angler.getUUID()).get("n")).longValue()==2,"restart preserves prize diamonds");check(((List)call(field(markets,"notices"),"publicEntries",market,place(2.5,64,2.5),1)).size()==1,"physical notice and approval survive server restart");
    }
    void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||server==null||++ticks!=140)return;try{setup();int phase=Files.exists(Path.of("civic.properties"))?2:1;if(phase==1){dimensions();first();}else second();System.out.println("CIVIC_NATIVE_PASS phase="+phase+" assertions="+checks);}catch(Throwable error){System.out.println("CIVIC_NATIVE_FAIL");error.printStackTrace();}finally{server.halt(false);server=null;}}
}
