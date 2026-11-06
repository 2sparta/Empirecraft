package com.empirecraft.engagementtest;

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

/** Shared native fixture helpers from the fleet regression. */
class FleetFixtureBase {
    MinecraftServer server;ServerLevel level;int ticks,checks;boolean restart;
    Object plugin,towny,universe,api,db,controller,fleet,markets,army;
    Class<?> townType,residentType,actorType;Object host,owner,third;
    ServerPlayer mayor,ownerMayor,otherMayor,admiral,captain,stranger;
    String market,offer;long longShip,auctionShip,builtShip;double lockedX,lockedZ;
    Properties saved=new Properties();

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
    static String request(){return UUID.randomUUID().toString();}
    String town(Object town)throws Exception{return "t:"+id(town);}
    Object town(String name)throws Exception {Object t=api.getClass().getMethod("getTown",String.class).invoke(api,name);if(t==null){call(universe,"newTown",name);t=api.getClass().getMethod("getTown",String.class).invoke(api,name);}return t;}
    void sql(String statement,Object...args)throws Exception {db.getClass().getMethod("execute",String.class,Object[].class).invoke(db,statement,args);}
    @SuppressWarnings("unchecked") Map<String,Object> row(String statement,Object...args)throws Exception {return (Map<String,Object>)db.getClass().getMethod("one",String.class,Object[].class).invoke(db,statement,args);}
    Object actor(ServerPlayer p)throws Exception{return actorType.getConstructor(String.class,boolean.class).newInstance(p.getUUID().toString(),false);}
    void permission(ServerPlayer p)throws Exception {
        Object player=Class.forName("org.bukkit.Bukkit").getMethod("getPlayer",UUID.class).invoke(null,p.getUUID());check(player!=null,"native fake player is visible through actual Bukkit lookup");
        Object attachment=player.getClass().getMethod("addAttachment",Class.forName("org.bukkit.plugin.Plugin")).invoke(player,towny);
        call(attachment,"setPermission","vs_sails.ship.control",true);call(attachment,"setPermission","vs_sails.ship.bypass",false);call(attachment,"setPermission","empirecraft.feudal.use",true);
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
        else{longShip=assemble(150,350,0,ownerMayor);auctionShip=assemble(90,100,8,mayor);}
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
    Object bukkit(ServerPlayer p)throws Exception{return Class.forName("org.bukkit.Bukkit").getMethod("getPlayer",UUID.class).invoke(null,p.getUUID());}
    LoadedServerShip ship(long id){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);}
}
