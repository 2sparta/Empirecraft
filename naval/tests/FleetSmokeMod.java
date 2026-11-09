package com.empirecraft.fleettest;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import com.quintonc.vs_sails.protection.*;
import com.quintonc.vs_sails.registration.SailsItems;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

/** Never install outside naval/smoke.py's disposable server. Uses actual plugin classloaders and Towny. */
@Mod("empirecraft_fleet_smoke")
public final class FleetSmokeMod {
    MinecraftServer server;ServerLevel level;int ticks,checks;boolean restart;
    Object plugin,towny,universe,api,db,controller,fleet,markets,army;
    Class<?> townType,residentType,actorType;Object host,owner,third;
    ServerPlayer mayor,ownerMayor,otherMayor,admiral,captain,stranger;
    String market,offer,application;long longShip,auctionShip,builtShip;double lockedX,lockedZ;
    Properties saved=new Properties();
    public FleetSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void start(ServerStartedEvent e){server=e.getServer();level=server.overworld();restart=Files.exists(Path.of("fleet-save.properties"));}
    void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static Object field(Object target,String name)throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static Object call(Object target,String name,Object...args)throws Exception {
        for(Method method:target.getClass().getMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){
            boolean matches=true;for(int i=0;i<args.length;i++){Class<?> type=method.getParameterTypes()[i];if(args[i]!=null&&!type.isInstance(args[i])&&!type.isPrimitive())matches=false;}
            if(matches){method.setAccessible(true);return method.invoke(target,args);}
        }throw new NoSuchMethodException(name);
    }
    static UUID id(Object object)throws Exception{return (UUID)call(object,"getUUID");}
    static Object invoke(Object target,String name,Object...args)throws Exception {for(var method:target.getClass().getDeclaredMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){method.setAccessible(true);return method.invoke(target,args);}throw new NoSuchMethodException(name);}
    static String request(){return UUID.randomUUID().toString();}
    String town(Object town)throws Exception{return "t:"+id(town);}
    void sql(String statement,Object...args)throws Exception {db.getClass().getMethod("execute",String.class,Object[].class).invoke(db,statement,args);}
    @SuppressWarnings("unchecked") Map<String,Object> row(String statement,Object...args)throws Exception {return (Map<String,Object>)db.getClass().getMethod("one",String.class,Object[].class).invoke(db,statement,args);}
    Object actor(ServerPlayer p)throws Exception{return actorType.getConstructor(String.class,boolean.class).newInstance(p.getUUID().toString(),false);}
    long balance(Object town)throws Exception{return ((Number)row("SELECT balance FROM accounts WHERE owner=? AND code='NAV'","e:"+town(town)).get("balance")).longValue();}
    LoadedServerShip ship(long id){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);}
    void permission(ServerPlayer p)throws Exception {
        Object player=Class.forName("org.bukkit.Bukkit").getMethod("getPlayer",UUID.class).invoke(null,p.getUUID());check(player!=null,"native fake player is visible through actual Bukkit lookup");
        Object attachment=player.getClass().getMethod("addAttachment",Class.forName("org.bukkit.plugin.Plugin")).invoke(player,towny);
        call(attachment,"setPermission","vs_sails.ship.control",false);call(attachment,"setPermission","vs_sails.ship.bypass",false);call(attachment,"setPermission","empirecraft.feudal.use",true);
    }
    static io.netty.channel.embedded.EmbeddedChannel packetSink(){
        // EmbeddedChannel has no real event loop: the VS physics and server
        // threads would otherwise concurrently mutate its outbound linked list.
        // Headless fake players consume no packets; retain protocol attributes
        // while discarding writes before they reach that non-thread-safe buffer.
        return new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
            @Override public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise){
                io.netty.util.ReferenceCountUtil.release(message);promise.trySuccess();
            }
            @Override public void flush(io.netty.channel.ChannelHandlerContext context){}
        });
    }
    ServerPlayer player(String name,Object town,boolean isMayor)throws Exception {
        ServerPlayer p=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
        p.connection.connection.channel=packetSink();p.connection.connection.channel.attr(net.minecraft.network.Connection.ATTRIBUTE_PROTOCOL).set(net.minecraft.network.ConnectionProtocol.PLAY);
        p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setPos(2.5,80,2.5);level.addNewPlayer(p);
        for(Field f:net.minecraft.server.players.PlayerList.class.getDeclaredFields()){
            f.setAccessible(true);if(f.getType()==List.class)try{((List)f.get(server.getPlayerList())).add(p);break;}catch(UnsupportedOperationException ignored){}
        }
        for(Field f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(f.getType())&&f.getGenericType().getTypeName().contains("java.util.UUID")&&f.getGenericType().getTypeName().contains("ServerPlayer")){f.setAccessible(true);((Map)f.get(server.getPlayerList())).put(p.getUUID(),p);}
        Object resident=api.getClass().getMethod("getResident",UUID.class).invoke(api,p.getUUID());
        if(resident==null)resident=call(call(universe,"getDataSource"),"newResident",name,p.getUUID());
        if(town!=null){residentType.getMethod("setTown",townType).invoke(resident,town);if(isMayor)townType.getMethod("setMayor",residentType).invoke(town,resident);}
        permission(p);return p;
    }
    Object town(String name)throws Exception {Object t=api.getClass().getMethod("getTown",String.class).invoke(api,name);if(t==null){call(universe,"newTown",name);t=api.getClass().getMethod("getTown",String.class).invoke(api,name);}return t;}
    void setup()throws Exception {
        Object manager=Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);
        towny=call(manager,"getPlugin","Towny");plugin=call(manager,"getPlugin","EmpirecraftFeudal");check(towny!=null&&plugin!=null&&Boolean.TRUE.equals(call(plugin,"isEnabled")),"actual Towny and Feudal plugins enabled");
        ClassLoader loader=towny.getClass().getClassLoader();api=Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,loader).getMethod("getInstance").invoke(null);universe=Class.forName("com.palmergames.bukkit.towny.TownyUniverse",true,loader).getMethod("getInstance").invoke(null);
        townType=Class.forName("com.palmergames.bukkit.towny.object.Town",true,loader);residentType=Class.forName("com.palmergames.bukkit.towny.object.Resident",true,loader);
        host=town("FleetHost");owner=town("FleetOwner");third=town("FleetThird");mayor=player("FleetMayor",host,true);ownerMayor=player("FleetSeller",owner,true);otherMayor=player("FleetBidder",third,true);admiral=player("FleetAdmiral",host,false);captain=player("FleetCaptain",owner,false);stranger=player("FleetVisitor",third,false);
        db=field(plugin,"database");controller=field(plugin,"fleet");fleet=field(controller,"fleet");markets=field(fleet,"m");army=field(plugin,"armyController");actorType=Class.forName("ua.empirecraft.feudal.FeudalService$Actor",true,plugin.getClass().getClassLoader());
        call(field(plugin,"towny"),"sync");call(field(plugin,"politicalCommands"),"refresh");call(army,"refresh");
        for(int x=-1;x<=23;x++)for(int z=-1;z<=2;z++)level.setChunkForced(x,z,true);
        claims();
        if(restart){try(var in=Files.newInputStream(Path.of("fleet-save.properties"))){saved.load(in);}longShip=Long.parseLong(saved.getProperty("long"));auctionShip=Long.parseLong(saved.getProperty("auction"));builtShip=Long.parseLong(saved.getProperty("built"));offer=saved.getProperty("offer");market=saved.getProperty("market");}
        else{longShip=assemble(150,350,0,ownerMayor);auctionShip=assemble(90,100,8,ownerMayor);}
    }
    void claims()throws Exception {
        Object bukkitWorld=level.getClass().getMethod("getWorld").invoke(level);Object world=api.getClass().getMethod("getTownyWorld",String.class).invoke(api,call(bukkitWorld,"getName"));Class<?> worldType=world.getClass(),blockType=Class.forName("com.palmergames.bukkit.towny.object.TownBlock",true,towny.getClass().getClassLoader());
        for(int x=-1;x<=22;x++)for(int z=-1;z<=1;z++)claim(world,worldType,blockType,x,z);
        claim(world,worldType,blockType,62,0);claim(world,worldType,blockType,63,0);
    }
    void claim(Object world,Class<?> worldType,Class<?> blockType,int x,int z)throws Exception {
        Object block;try{block=worldType.getMethod("getTownBlock",int.class,int.class).invoke(world,x,z);}catch(InvocationTargetException absent){block=blockType.getConstructor(int.class,int.class,worldType).newInstance(x,z,world);universe.getClass().getMethod("addTownBlock",blockType).invoke(universe,block);}
        blockType.getMethod("setTown",townType).invoke(block,host);
    }
    long assemble(int x1,int x2,int z0,ServerPlayer founder)throws Exception {
        Set<BlockPos> blocks=new HashSet<>();for(int x=x1;x<=x2;x++)for(int z=z0;z<z0+3;z++){BlockPos p=new BlockPos(x,80,z);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);}
        var body=ShipAssembler.assembleToShip(level,blocks,1);body.setStatic(true);var b=body.getShipAABB();for(int x=b.minX()>>4;x<=(b.maxX()-1)>>4;x++)for(int z=b.minZ()>>4;z<=(b.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);
        ShipProtection.assembled(level,body,founder);return body.getId();
    }
    void market()throws Exception {
        call(field(plugin,"towny"),"sync");call(army,"refresh");
        // Synthetic approved market and funded test currency; the production
        // Towny claim adapter, public fleet API and transaction ledger stay real.
        level.setBlockAndUpdate(new BlockPos(2,80,2),Blocks.STONE.defaultBlockState());
        if(restart)return;
        String world=FleetNative.request("snapshot",Map.of("ship",longShip)).get("world").toString();
        market=call(markets,"create",actor(mayor),request(),town(host),world,"Морський базар","Native fleet fixture").toString();
        sql("UPDATE cm_markets SET state='OPEN',opened=TRUE WHERE id=?",market);sql("INSERT INTO cm_claims VALUES(?,?,0,0)",market,world);
        sql("INSERT INTO cm_departments(market,kind,building,x1,y1,z1,x2,y2,z2,cx,cy,cz,material,state,reviewer,reviewed) VALUES(?,'GOODS',?,0,78,0,15,84,15,2,80,2,'STONE','OPEN','fixture',?)",market,request(),System.currentTimeMillis());
        sql("UPDATE political_states SET stage='TRADE_REPUBLIC',government='REPUBLIC' WHERE estate=?",town(host));
        sql("INSERT INTO currencies(code,name,symbol,issuer,supply,backing) VALUES('NAV','Native fixture','N',?,300000,'GOLD')",town(host));
        for(Object town:List.of(host,owner,third))sql("INSERT INTO accounts VALUES(?,'NAV',100000)","e:"+town(town));
        sql("INSERT INTO cm_permits VALUES(?,'NAV',TRUE,TRUE,TRUE)",market);
        call(fleet,"port",actor(mayor),request(),market,true);call(army,"refresh");
    }
    void rights()throws Exception {
        check(ship(longShip)!=null,"assembled vessel is loaded before access checks");
        System.out.println("FLEET_NATIVE_DIAG snapshot="+FleetNative.request("snapshot",Map.of("ship",longShip))+" policy="+FleetNative.invoke("policy",Map.of("ship",longShip,"player",stranger.getUUID().toString()))+" port="+row("SELECT * FROM naval_ports WHERE market=?",market)+" market="+row("SELECT state,opened FROM cm_markets WHERE id=?",market));
        Object adapter=field(controller,"world"),hull=call(adapter,"hull",longShip);System.out.println("FLEET_NATIVE_DIAG territories="+call(adapter,"territories",hull)+" republic="+call(adapter,"republic",town(host))+" portOpen="+call(fleet,"portOpen",market)+" state="+row("SELECT * FROM political_states WHERE estate=?",town(host)));
        check(!ShipProtection.allows(stranger,level,ship(longShip)),"neutral vessel is protected throughout the enabled republic's waters");check(ShipProtection.allows(ownerMayor,level,ship(longShip)),"owner mayor controls own vessel in neutral protected harbor");
        check(!ShipProtection.allows(mayor,level,ship(longShip)),"host mayor cannot take a neutral visitor's vessel");
        call(fleet,"edit",actor(ownerMayor),request(),longShip,"captain",captain.getUUID().toString());call(fleet,"edit",actor(ownerMayor),request(),longShip,"crew-add",admiral.getUUID().toString());call(fleet,"admiral",actor(mayor),request(),town(host),admiral.getUUID().toString());reconcile();
        check(ShipProtection.allows(captain,level,ship(longShip))&&ShipProtection.allows(admiral,level,ship(longShip)),"assigned captain and foreign crew act in protected harbor");
        var b=ship(longShip).getShipAABB();var pos=new BlockPos(b.minX(),b.minY(),b.minZ());
        BlockPos helm=pos.above();
        level.setBlockAndUpdate(helm,com.quintonc.vs_sails.registration.SailsBlocks.HELM_BLOCK.get().defaultBlockState());
        var wheel=(com.quintonc.vs_sails.blocks.entity.BaseHelmBlockEntity)level.getBlockEntity(helm);
        wheel.setItem(0,new net.minecraft.world.item.ItemStack(com.quintonc.vs_sails.registration.SailsBlocks.SPRUCE_HELM_WHEEL.get()));
        for(ServerPlayer pilot:List.of(captain,admiral)){
            pilot.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
            level.getBlockState(helm).use(level,pilot,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(helm),net.minecraft.core.Direction.UP,helm,false));
            check(pilot.getVehicle()!=null,"assigned captain or crew can actually mount the helm without a global permission");pilot.stopRiding();
        }
        check(!wheel.sit(stranger),"an outsider cannot mount the same real helm");
        // Restore the original hull before measuring its asynchronous physics hold.
        level.setBlockAndUpdate(helm,Blocks.AIR.defaultBlockState());
        check(!ShipProtection.allows(stranger,level,ship(longShip)),"combat eligibility never grants an outsider ship maintenance or inventory access");
        townyEvent(captain,pos,false);townyEvent(stranger,pos,true);townyEvent(captain,new BlockPos(2,80,2),true);
        String war=request();sql("INSERT INTO army_wars VALUES(?,?,?,'ACTIVE',?,NULL,NULL)",war,town(owner),town(host),System.currentTimeMillis());call(army,"refresh");
        check(!ShipProtection.allows(stranger,level,ship(longShip)),"war does not grant enemy helm or inventory control");sql("DELETE FROM army_wars WHERE id=?",war);call(army,"refresh");
        townType.getMethod("addAlly",townType).invoke(host,owner);townType.getMethod("addAlly",townType).invoke(owner,host);call(army,"refresh");
    }
    @SuppressWarnings({"rawtypes","unchecked"}) void townyEvent(ServerPlayer p,BlockPos pos,boolean denied)throws Exception {
        ClassLoader loader=towny.getClass().getClassLoader();Object world=level.getClass().getMethod("getWorld").invoke(level),block=call(world,"getBlockAt",pos.getX(),pos.getY(),pos.getZ());
        Class<?> worldClass=Class.forName("org.bukkit.World"),locationClass=Class.forName("org.bukkit.Location"),materialClass=Class.forName("org.bukkit.Material"),playerClass=Class.forName("org.bukkit.entity.Player"),blockClass=Class.forName("org.bukkit.block.Block"),townBlockClass=Class.forName("com.palmergames.bukkit.towny.object.TownBlock",true,loader);
        Object location=locationClass.getConstructor(worldClass,double.class,double.class,double.class).newInstance(world,(double)pos.getX(),(double)pos.getY(),(double)pos.getZ());
        Object event=Class.forName("com.palmergames.bukkit.towny.event.actions.TownyBuildEvent",true,loader).getConstructor(playerClass,locationClass,materialClass,blockClass,townBlockClass,boolean.class).newInstance(bukkit(p),location,Enum.valueOf((Class)materialClass,"OAK_PLANKS"),block,null,true);
        Object manager=Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);call(manager,"callEvent",event);
        check(Boolean.valueOf(denied).equals(call(event,"isCancelled")),"real Towny action starts denied: ship crew may build, outsiders and ground stay denied ("+p.getGameProfile().getName()+", "+pos+")");
    }
    void reconcile()throws Exception {var method=controller.getClass().getDeclaredMethod("reconcile");method.setAccessible(true);method.invoke(controller);}
    Object bukkit(ServerPlayer p)throws Exception{return Class.forName("org.bukkit.Bukkit").getMethod("getPlayer",UUID.class).invoke(null,p.getUUID());}
    @SuppressWarnings("unchecked") Map<String,Object> ui(ServerPlayer p,String action,String form,List<String> values,String token)throws Exception {
        var sessions=(Map<UUID,?>)field(controller,"sessions");Object session=sessions.get(p.getUUID());String id=field(session,"id").toString();
        return (Map<String,Object>)plugin.getClass().getMethod("fleetUi",UUID.class,String.class,String.class,String.class,String.class,List.class,String.class,int.class).invoke(plugin,p.getUUID(),id,action,"manage",form,values,token,1);
    }
    void open(ServerPlayer p)throws Exception {Class.forName("org.bukkit.Bukkit").getMethod("dispatchCommand",Class.forName("org.bukkit.command.CommandSender"),String.class).invoke(null,bukkit(p),"fleet");}
    Map<String,Object> change(ServerPlayer p,String form,List<String> values)throws Exception {
        open(p);var prepare=ui(p,"prepare",form,values,"");check(!prepare.containsKey("error"),"prepare native UI "+form+": "+prepare);
        var confirm=(Map<?,?>)prepare.get("confirmation");var result=ui(p,"confirm","",List.of(),confirm.get("token").toString());check(!result.containsKey("error"),"confirm native UI "+form+": "+result);return result;
    }
    void sale()throws Exception {
        mayor.setPos(2.5,80,2.5);ownerMayor.setPos(3.5,80,2.5);ship(longShip).setStatic(false);
        change(ownerMayor,"sell",List.of(""+longShip,market,"100","NAV","24","Корабель із довгим носом"));
        var offerRow=row("SELECT * FROM naval_offers WHERE ship=? AND state='OPEN'",longShip);offer=offerRow.get("id").toString();
        check(ship(longShip).isStatic(),"listed ship gets an actual native static hold");var position=ship(longShip).getTransform().getPositionInWorld();lockedX=position.x();lockedZ=position.z();
        check(((Number)FleetNative.request("snapshot",Map.of("ship",longShip)).get("x")).doubleValue()>200,"test ship centre lies beyond the sale radius; its bow qualifies");
    }
    void purchase()throws Exception {
        check(Math.hypot(ship(longShip).getTransform().getPositionInWorld().x()-lockedX,ship(longShip).getTransform().getPositionInWorld().z()-lockedZ)<.02,"listed vessel remains in place across real physics ticks: old="+lockedX+","+lockedZ+" actual="+ship(longShip).getTransform().getPositionInWorld()+" static="+ship(longShip).isStatic());
        long buyer=balance(host),seller=balance(owner);change(mayor,"buy",List.of(offer));
        check(row("SELECT town FROM naval_ships WHERE id=?",longShip).get("town").equals(town(host)),"native UI purchase transfers canonical city ownership");check(balance(host)==buyer-9800&&balance(owner)==seller+9800,"town money, fees and ownership settle in the same transaction");
        check(ShipProtection.allows(admiral,level,ship(longShip)),"city admiral commands all newly purchased ships");check(!ShipProtection.allows(captain,level,ship(longShip)),"sale clears the previous captain's access");
        ShipRegistry.get(server).ships.get(longShip).town=id(owner);check(!ShipProtection.allows(ownerMayor,level,ship(longShip))&&ShipProtection.allows(mayor,level,ship(longShip)),"stale native ownership cannot override the committed database transfer");reconcile();
        change(mayor,"name",List.of(""+longShip,"Буревісник"));check(ship(longShip).getSlug().equals("Буревісник"),"mayor's Unicode ship name reaches the actual native ship");
    }
    @SuppressWarnings("unchecked") void choice()throws Exception {
        BlockPos source=new BlockPos(10,80,10);for(int x=10;x<13;x++)for(int z=10;z<13;z++)level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.OAK_PLANKS.defaultBlockState());
        mayor.setPos(10.5,81,10.5);mayor.setItemInHand(InteractionHand.MAIN_HAND,SailsItems.DEDICATION_BOTTLE.get().getDefaultInstance());
        var hit=new BlockHitResult(Vec3.atCenterOf(source),Direction.UP,source,false);var item=mayor.getMainHandItem();item.getItem().useOn(new UseOnContext(mayor,InteractionHand.MAIN_HAND,hit));
        check(mayor.getMainHandItem().getCount()==1&&!level.getBlockState(source).isAir(),"ordinary Sails bottle opens an application without consuming blocks or bottle");
        Object registration=field(plugin,"ships"),service=field(registration,"service");String world=FleetNative.request("snapshot",Map.of("ship",longShip)).get("world").toString();
        var anchorType=Class.forName("ua.empirecraft.feudal.ShipRegistrationService$Anchor",true,plugin.getClass().getClassLoader());var constructor=anchorType.getDeclaredConstructor(String.class,int.class,int.class,int.class);constructor.setAccessible(true);Object anchor=constructor.newInstance(world,10,80,10);
        application=invoke(service,"submit",actor(mayor),request(),"Заявлена Чайка",anchor).toString();
        Object admin=actorType.getConstructor(String.class,boolean.class).newInstance(ownerMayor.getUUID().toString(),true);invoke(service,"review",admin,request(),application,true,"Огляд Sails");
        // Sails retains its independent Towny DESTROY checks. The assembling
        // administrator also has the host mayor's real land permissions here.
        Object attachment=call(bukkit(mayor),"addAttachment",towny);call(attachment,"setPermission","empirecraft.feudal.ships.admin",true);
        mayor.getMainHandItem().getItem().useOn(new UseOnContext(mayor,InteractionHand.MAIN_HAND,hit));
        check(mayor.getMainHandItem().isEmpty()&&level.getBlockState(source).isAir(),"approved administrator Sails bottle consumes itself and assembles the real hull");
        builtShip=((Number)row("SELECT ship FROM naval_applications WHERE id=?",application).get("ship")).longValue();
        check(row("SELECT state FROM naval_applications WHERE id=?",application).get("state").equals("ASSEMBLED"),"actual Sails ship waits for administrator city/name menu");
    }
    void finishChoice()throws Exception {
        Object registration=field(plugin,"ships");invoke(registration,"tick");
        var prepared=ui(mayor,"prepare","ship-finalize",List.of(town(host),"Заявлена Чайка"),"");check(!prepared.containsKey("error"),"administrator previews Sails ship ownership and requested name: "+prepared);
        String token=((Map<?,?>)prepared.get("confirmation")).get("token").toString();var result=ui(mayor,"confirm","",List.of(),token);check(!result.containsKey("error"),"administrator confirms Sails ship ownership: "+result);
        check(row("SELECT town,name FROM naval_ships WHERE id=?",builtShip).get("town").equals(town(host)),"Sails ship belongs to the confirmed applicant city");
    }
    void direct()throws Exception {
        mayor.setPos(10.5,81,10.5);ownerMayor.setPos(11.5,81,10.5);long seller=balance(host),buyer=balance(owner);
        Class.forName("org.bukkit.Bukkit").getMethod("dispatchCommand",Class.forName("org.bukkit.command.CommandSender"),String.class).invoke(null,bukkit(mayor),"trade ship "+builtShip+" FleetOwner 10 NAV Передача через трейд");
        var prepared=ui(mayor,"view","",List.of(),"");check(prepared.containsKey("confirmation"),"actual /trade ship command opens exact confirmation while item trading remains market-only");
        var result=ui(mayor,"confirm","",List.of(),((Map<?,?>)prepared.get("confirmation")).get("token").toString());check(!result.containsKey("error"),"seller confirms native direct ship offer");
        String id=row("SELECT id FROM naval_offers WHERE ship=? AND kind='DIRECT' AND state='OPEN'",builtShip).get("id").toString();change(ownerMayor,"buy",List.of(id));
        check(balance(host)==seller+1000&&balance(owner)==buyer-1000&&row("SELECT town FROM naval_ships WHERE id=?",builtShip).get("town").equals(town(owner)),"actual /trade ship transfers money and selected ship together");
    }
    void observations()throws Exception {
        mayor.setPos(140,81,1.5);mayor.setYRot(-90);mayor.setYHeadRot(-90);mayor.setXRot(0);
        var bounds=ship(longShip).getShipAABB();var local=new BlockPos(bounds.minX(),bounds.minY(),bounds.minZ());var target=ship(longShip).getShipToWorld().transformPosition(new Vector3d(local.getX()+.5,local.getY()+.5,local.getZ()+.5));var point=new Vec3(target.x,target.y,target.z);var ray=org.valkyrienskies.mod.common.world.RaycastUtilsKt.clipIncludeShips(level,new net.minecraft.world.level.ClipContext(mayor.getEyePosition(),point,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mayor));
        System.out.println("FLEET_NATIVE_DIAG view player="+mayor.position()+" eye="+mayor.getEyePosition()+" look="+mayor.getViewVector(1)+" target="+point+" local="+local+" loaded="+level.hasChunkAt(local)+" state="+level.getBlockState(local)+" worldState="+level.getBlockState(new BlockPos(145,81,1))+" hit="+ray.getType()+" pos="+ray.getBlockPos()+" owner="+VSGameUtilsKt.getShipManagingPos(level,ray.getBlockPos())+" radius="+server.getPlayerList().getViewDistance());
        check(Boolean.TRUE.equals(FleetNative.request("visible",Map.of("ship",longShip,"player",mayor.getUUID().toString())).get("visible")),"real hull in front of observer is visible");
        for(int y=74;y<=90;y++)for(int z=-5;z<=6;z++)level.setBlockAndUpdate(new BlockPos(145,y,z),Blocks.STONE.defaultBlockState());
        check(Boolean.FALSE.equals(FleetNative.request("visible",Map.of("ship",longShip,"player",mayor.getUUID().toString())).get("visible")),"world wall prevents a fresh ship observation");
        for(int y=74;y<=90;y++)for(int z=-5;z<=6;z++)level.setBlockAndUpdate(new BlockPos(145,y,z),Blocks.AIR.defaultBlockState());mayor.setYRot(90);mayor.setYHeadRot(90);
        check(Boolean.FALSE.equals(FleetNative.request("visible",Map.of("ship",longShip,"player",mayor.getUUID().toString())).get("visible")),"a ship behind the observer is outside the field of view");mayor.setYRot(-90);mayor.setYHeadRot(-90);
    }
    void auction()throws Exception {
        ownerMayor.setPos(3.5,80,2.5);mayor.setPos(2.5,80,2.5);otherMayor.setPos(4.5,80,2.5);ship(auctionShip).setStatic(false);
        change(ownerMayor,"auction",List.of(""+auctionShip,market,"50","10","NAV","24","Аукціон судна"));offer=row("SELECT id FROM naval_offers WHERE ship=? AND state='OPEN'",auctionShip).get("id").toString();
        long first=balance(host),second=balance(third);change(mayor,"bid",List.of(offer,"50"));check(balance(host)==first-5000,"auction bid reserves city treasury funds");change(otherMayor,"bid",List.of(offer,"60"));check(balance(host)==first&&balance(third)==second-6000,"outbid city receives its full escrow refund");
        var body=ship(auctionShip);var p=body.getTransform().getPositionInWorld();VSGameUtilsKt.getShipObjectWorld(level).teleportShip(body,new ShipTeleportDataImpl(new Vector3d(1000,p.y(),p.z()),new Quaterniond(body.getTransform().getShipToWorldRotation()),new Vector3d(),new Vector3d(),body.getChunkClaimDimension(),1.0,body.getTransform().getPositionInShip()));
    }
    void save()throws Exception {
        var body=ship(auctionShip);body.setStatic(true);var p=body.getTransform().getPositionInWorld();VSGameUtilsKt.getShipObjectWorld(level).teleportShip(body,new ShipTeleportDataImpl(new Vector3d(100,p.y(),9),new Quaterniond(),new Vector3d(),new Vector3d(),body.getChunkClaimDimension(),1.0,body.getTransform().getPositionInShip()));
    }
    void persist()throws Exception {
        ship(auctionShip).setStatic(false);change(ownerMayor,"auction",List.of(""+auctionShip,market,"50","10","NAV","24","Збереження аукціону"));offer=row("SELECT id FROM naval_offers WHERE ship=? AND state='OPEN'",auctionShip).get("id").toString();change(mayor,"bid",List.of(offer,"50"));
        saved.setProperty("long",""+longShip);saved.setProperty("auction",""+auctionShip);saved.setProperty("built",""+builtShip);saved.setProperty("offer",offer);saved.setProperty("market",market);saved.setProperty("hostBalance",""+balance(host));
        Object source=call(universe,"getDataSource");for(Object town:List.of(host,owner,third))call(source,"saveTown",town);
        for(ServerPlayer player:List.of(mayor,ownerMayor,otherMayor,admiral,captain,stranger)){Object resident=api.getClass().getMethod("getResident",UUID.class).invoke(api,player.getUUID());call(source,"saveResident",resident);}
        Object world=api.getClass().getMethod("getTownyWorld",String.class).invoke(api,call(level.getClass().getMethod("getWorld").invoke(level),"getName"));for(Object block:(Collection<?>)call(world,"getTownBlocks"))call(source,"saveTownBlock",block);
        try(var out=Files.newOutputStream(Path.of("fleet-save.properties"))){saved.store(out,"Disposable fleet fixture");}
    }
    void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||server==null)return;try{
        ticks++;if(ticks==5)setup();if(ticks==30)market();
        if(restart){if(ticks==100){
            check(ship(auctionShip)!=null&&ship(auctionShip).isStatic(),"active auction restores its native hold after a real restart");check(row("SELECT state FROM naval_offers WHERE id=?",offer).get("state").equals("OPEN"),"saved listing remains open after restart");check(balance(host)==Long.parseLong(saved.getProperty("hostBalance")),"reserved city funds persist without another debit");
            sql("UPDATE naval_offers SET ends=? WHERE id=?",System.currentTimeMillis()-1,offer);call(fleet,"tick");reconcile();check(row("SELECT town FROM naval_ships WHERE id=?",auctionShip).get("town").equals(town(host)),"expired auction transfers the actual persisted ship to the winning town");
            check(!ship(auctionShip).isStatic(),"auction release restores the original dynamic state");System.out.println("FLEET_NATIVE_PASS phase=restart assertions="+checks);server.halt(false);server=null;}return;}
        if(ticks==45)rights();if(ticks==60)observations();if(ticks==80)sale();if(ticks==110)purchase();if(ticks==130)choice();if(ticks==140)finishChoice();if(ticks==150)direct();if(ticks==170)auction();
        if(ticks==200){check(row("SELECT state FROM naval_offers WHERE id=?",offer).get("state").equals("CANCELLED"),"moving beyond 200 blocks delists the ship automatically");check(balance(third)==100000,"out-of-range auction refunds the highest bidder");check(!ship(auctionShip).isStatic(),"automatic delisting releases only the market-owned hold");save();}
        if(ticks==230)persist();if(ticks==250){System.out.println("FLEET_NATIVE_PASS phase=ownership-crew-port-ui-map-auction-assembly assertions="+checks);server.halt(false);server=null;}
    }catch(Throwable failure){System.out.println("FLEET_NATIVE_FAIL tick="+ticks);failure.printStackTrace();server.halt(false);server=null;}}
}
