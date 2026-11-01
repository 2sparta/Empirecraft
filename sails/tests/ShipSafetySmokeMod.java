package com.empirecraft.sails.test;

import com.mojang.authlib.GameProfile;
import com.quintonc.vs_sails.protection.*;
import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import java.lang.reflect.*;
import java.util.*;

/** Destructive fixture, exclusively for a disposable Mohist world with real Towny/Supplementaries. */
@Mod("empirecraft_ship_safety_smoketest")
public final class ShipSafetySmokeMod {
    MinecraftServer server;ServerLevel level;int ticks,checks;
    final Map<String,Long> ships=new LinkedHashMap<>();
    final Map<String,Double> frozenY=new HashMap<>();
    Object townyPlugin,api,universe,home,ally;Class<?> townType,residentType;ServerPlayer captain,visitor,untrained;
    Object captainPerms,visitorPerms;
    public ShipSafetySmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    void started(ServerStartedEvent event){server=event.getServer();level=server.overworld();}
    static Object call(Object object,String method) throws Exception {return object.getClass().getMethod(method).invoke(object);}
    static UUID id(Object object)throws Exception{return (UUID)call(object,"getUUID");}
    LoadedServerShip ship(String name){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(ships.get(name));}
    void assemble(String name,int x,boolean hull,boolean externalStatic) {
        Set<BlockPos> blocks=new HashSet<>();
        for(int a=0;a<(name.equals("single")?1:4);a++)for(int z=0;z<(name.equals("single")?1:4);z++) {
            BlockPos p=new BlockPos(x+a,110,z);level.setBlockAndUpdate(p,SailsBlocks.SAIL_BLOCK.get().defaultBlockState());blocks.add(p);
        }
        if(hull){BlockPos p=new BlockPos(x,109,0);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);}
        if(name.equals("remnant")) {
            BlockPos p=new BlockPos(x+1,109,0);level.setBlockAndUpdate(p,BuiltInRegistries.BLOCK.get(new ResourceLocation("supplementaries:cannon")).defaultBlockState());blocks.add(p);
        }
        var body=ShipAssembler.assembleToShip(level,blocks,1);body.setStatic(externalStatic);ships.put(name,body.getId());
    }
    void build(){
        for(int x=-2;x<14;x++)for(int z=-2;z<=2;z++)level.setChunkForced(x,z,true);
        assemble("cloth",0,false,false);assemble("single",32,false,false);assemble("remnant",64,true,false);assemble("external",96,false,true);assemble("legacy",128,false,false);
    }
    void loaded(){for(String name:ships.keySet()) {
        var body=ship(name);check(body!=null,"loaded "+name);var b=body.getShipAABB();
        for(int x=b.minX()>>4;x<=(b.maxX()-1)>>4;x++)for(int z=b.minZ()>>4;z<=(b.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);
        SailsShipControl.getOrCreate(body,level).onServerTick();
    }}
    void legacyMass() {
        var body=ship("legacy");var bounds=body.getShipAABB();var nativeWorld=VSGameUtilsKt.getShipObjectWorld(level);
        var air=org.valkyrienskies.mod.common.BlockStateInfo.INSTANCE.get(Blocks.AIR.defaultBlockState()).getSecond();
        List<BlockPos> blocks=new ArrayList<>();
        for(int x=bounds.minX();x<bounds.maxX();x++)for(int y=bounds.minY();y<bounds.maxY();y++)for(int z=bounds.minZ();z<bounds.maxZ();z++) {
            var p=new BlockPos(x,y,z);if(!level.getBlockState(p).isAir())blocks.add(p);
        }
        double mass=body.getInertiaData().getMass();String dim=body.getChunkClaimDimension();
        for(var p:blocks)nativeWorld.onSetBlock(p.getX(),p.getY(),p.getZ(),dim,org.valkyrienskies.mod.common.BlockStateInfo.INSTANCE.get(level.getBlockState(p)).getSecond(),air,0,0);
        var origin=blocks.get(0);nativeWorld.onSetBlock(origin.getX(),origin.getY(),origin.getZ(),dim,air,air,mass,0);
        for(var p:blocks)nativeWorld.onSetBlock(p.getX(),p.getY(),p.getZ(),dim,air,org.valkyrienskies.mod.common.BlockStateInfo.INSTANCE.get(level.getBlockState(p)).getSecond(),0,.01);
        check(body.getInertiaData().getMass()<1,"legacy 0.01 kg/cloth inertia reproduced through real VS");
        SailsShipControl.markDirty(level,origin);SailsShipControl.getOrCreate(body,level).onServerTick();
        check(body.getInertiaData().getMass()>=1599,"legacy sail inertia repaired through real VS");
    }
    void frozen(){for(String name:List.of("cloth","single","external","legacy")) {
        var body=ship(name);var control=SailsShipControl.getOrCreate(body,level);
        check(control.sailSafetyHeld && body.isStatic(),"cloth-only body frozen: "+name);
        check(body.getInertiaData().getMass()>=99,"safe cloth inertia: "+name);
        check(!control.motionSafetyTripped,"normal cloth hold does not trip emergency: "+name);
        frozenY.put(name,body.getTransform().getPositionInWorld().y());
    }}
    void editHull(String name,boolean add) {
        var body=ship(name);var b=body.getShipAABB();
        if(add)level.setBlockAndUpdate(new BlockPos(b.minX(),b.minY()-1,b.minZ()),Blocks.OAK_PLANKS.defaultBlockState());
        else for(int x=b.minX();x<b.maxX();x++)for(int y=b.minY();y<b.maxY();y++)for(int z=b.minZ();z<b.maxZ();z++) {
            BlockPos p=new BlockPos(x,y,z);if(!(level.getBlockState(p).getBlock() instanceof com.quintonc.vs_sails.blocks.SailBlock))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
        }
    }
    ServerPlayer player(String name) throws Exception {
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
        player.setPos(0,120,0);level.addNewPlayer(player);
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(field.getType()==List.class) {
            field.setAccessible(true);Object list=field.get(server.getPlayerList());try {((List)list).add(player);break;}catch(UnsupportedOperationException ignored){}
        }
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())
                && field.getGenericType().getTypeName().contains("java.util.UUID") && field.getGenericType().getTypeName().contains("ServerPlayer")) {
            field.setAccessible(true);((Map)field.get(server.getPlayerList())).put(player.getUUID(),player);
        }
        return player;
    }
    Object resident(ServerPlayer player,Object town)throws Exception {
        Object datasource=call(universe,"getDataSource");
        Object r=datasource.getClass().getMethod("newResident",String.class,UUID.class).invoke(datasource,player.getScoreboardName(),player.getUUID());
        r.getClass().getMethod("setTown",townType).invoke(r,town);return r;
    }
    Object permissions(ServerPlayer player,boolean control) throws Exception {
        Object bukkitPlayer=Class.forName("org.bukkit.Bukkit").getMethod("getPlayer",UUID.class).invoke(null,player.getUUID());
        check(bukkitPlayer!=null,"fake actor is visible through real Bukkit player lookup");
        Class<?> permissible=Class.forName("org.bukkit.permissions.Permissible");
        Object attachment=permissible.getMethod("addAttachment",Class.forName("org.bukkit.plugin.Plugin")).invoke(bukkitPlayer,townyPlugin);
        permission(attachment,"vs_sails.ship.control",control);permission(attachment,"vs_sails.ship.bypass",false);return attachment;
    }
    void permission(Object attachment,String node,boolean value)throws Exception {attachment.getClass().getMethod("setPermission",String.class,boolean.class).invoke(attachment,node,value);}
    void claims(Object town) throws Exception {
        Object world=api.getClass().getMethod("getTownyWorld",String.class).invoke(api,call(level.getClass().getMethod("getWorld").invoke(level),"getName"));
        Class<?> worldType=world.getClass(),blockType=Class.forName("com.palmergames.bukkit.towny.object.TownBlock",true,townyPlugin.getClass().getClassLoader());
        var b=ship("remnant").getWorldAABB();int size=16;
        for(int x=(int)Math.floor(b.minX()/size);x<=(int)Math.floor(b.maxX()/size);x++)for(int z=(int)Math.floor(b.minZ()/size);z<=(int)Math.floor(b.maxZ()/size);z++) {
            Object block;
            try {block=worldType.getMethod("getTownBlock",int.class,int.class).invoke(world,x,z);}
            catch(InvocationTargetException absent){block=blockType.getConstructor(int.class,int.class,worldType).newInstance(x,z,world);universe.getClass().getMethod("addTownBlock",blockType).invoke(universe,block);}
            blockType.getMethod("setTown",townType).invoke(block,town);
        }
    }
    void towny() throws Exception {
        Class<?> bukkit=Class.forName("org.bukkit.Bukkit"),managerType=Class.forName("org.bukkit.plugin.PluginManager");Object manager=bukkit.getMethod("getPluginManager").invoke(null);
        townyPlugin=managerType.getMethod("getPlugin",String.class).invoke(manager,"Towny");check(townyPlugin!=null,"real Towny loaded");
        ClassLoader loader=townyPlugin.getClass().getClassLoader();
        universe=Class.forName("com.palmergames.bukkit.towny.TownyUniverse",true,loader).getMethod("getInstance").invoke(null);
        api=Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,loader).getMethod("getInstance").invoke(null);
        townType=Class.forName("com.palmergames.bukkit.towny.object.Town",true,loader);residentType=Class.forName("com.palmergames.bukkit.towny.object.Resident",true,loader);
        universe.getClass().getMethod("newTown",String.class).invoke(universe,"HomeHarbour");universe.getClass().getMethod("newTown",String.class).invoke(universe,"AllyHarbour");
        home=api.getClass().getMethod("getTown",String.class).invoke(api,"HomeHarbour");ally=api.getClass().getMethod("getTown",String.class).invoke(api,"AllyHarbour");
        captain=player("ShipCaptain");visitor=player("AllyCaptain");untrained=player("AllyDeckhand");
        Object resident=resident(captain,home);resident(visitor,ally);resident(untrained,ally);townType.getMethod("setMayor",residentType).invoke(home,resident);
        captainPerms=permissions(captain,true);visitorPerms=permissions(visitor,true);permissions(untrained,false);
        var body=ship("remnant");ShipProtection.assembled(level,body,captain);var data=ShipRegistry.get(server);var vessel=data.ships.get(body.getId());
        check(vessel.town.equals(id(home)),"assembly registers actor's actual town");claims(home);
        check(ShipProtection.allows(captain,level,body),"own authorised captain");check(!ShipProtection.allows(visitor,level,body),"foreign captain rejected");
        permission(captainPerms,"vs_sails.ship.control",false);check(!ShipProtection.allows(captain,level,body),"own untrained resident rejected");permission(captainPerms,"vs_sails.ship.control",true);
        claims(ally);check(ShipProtection.allows(visitor,level,body),"unallied foreign territory has no additional ship protection");
        townType.getMethod("addAlly",townType).invoke(home,ally);townType.getMethod("addAlly",townType).invoke(ally,home);
        check(!ShipProtection.allows(visitor,level,body),"ally land does not grant ship access");
        ProtectionConfig.get().protectAlliedTowns=false;check(ShipProtection.allows(visitor,level,body),"allied territory protection can be configured off");ProtectionConfig.get().protectAlliedTowns=true;
        vessel.grants.towns.add(id(ally));check(ShipProtection.allows(visitor,level,body),"trusted town captain allowed");check(!ShipProtection.allows(untrained,level,body),"trusted town's ordinary member denied");
        vessel.grants.towns.clear();vessel.grants.players.add(visitor.getUUID());check(ShipProtection.allows(visitor,level,body),"explicit player allowed");
        vessel.grants.players.clear();check(!ShipProtection.allows(visitor,level,body),"player revocation immediate");
        var fleet=data.fleets.computeIfAbsent(id(home),k->new ShipAccess.Grants());fleet.towns.add(id(ally));check(ShipProtection.allows(visitor,level,body),"fleet town grant applied");fleet.towns.clear();
        var center=body.getTransform().getPositionInShip();var pos=BlockPos.containing(center.x(),center.y(),center.z());
        check(MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,pos,level.getBlockState(pos),visitor)),"Forge foreign block break cancelled");
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        check(MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(visitor,InteractionHand.MAIN_HAND,pos,hit)),"Forge foreign block interaction cancelled");
        cannon(body,vessel);
        var loaded=ShipRegistry.load(data.save(new net.minecraft.nbt.CompoundTag()));check(loaded.ships.get(body.getId()).town.equals(id(home)),"real NBT registry roundtrip");
        vessel.offeredTo=id(ally);vessel.accept(id(ally));check(!ShipProtection.allows(captain,level,body),"sale removes old town access");check(ShipProtection.allows(visitor,level,body),"sale authorises new town's captain");
        data.setDirty();
    }
    void cannon(LoadedServerShip body,ShipAccess.Vessel vessel) throws Exception {
        var b=body.getShipAABB();net.minecraft.world.level.block.entity.BlockEntity tile=null;
        for(int x=b.minX();x<b.maxX();x++)for(int y=b.minY();y<b.maxY();y++)for(int z=b.minZ();z<b.maxZ();z++) {
            var candidate=level.getBlockEntity(new BlockPos(x,y,z));if(candidate!=null && candidate.getClass().getName().endsWith("CannonBlockTile"))tile=candidate;
        }
        check(tile!=null,"real Supplementaries cannon tile");BlockPos pos=tile.getBlockPos();
        visitor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        tile.getClass().getMethod("setPlayerWhoMayEdit",UUID.class).invoke(tile,visitor.getUUID());
        Class<?> packet=Class.forName("net.mehvahdjukaar.supplementaries.common.network.ServerBoundSyncCannonPacket");
        Class<?> context=Class.forName("net.mehvahdjukaar.moonlight.api.platform.network.ChannelHandler$Context");
        Object ctx=Proxy.newProxyInstance(context.getClassLoader(),new Class[]{context},(p,m,a)->m.getName().equals("getSender")?visitor:null);
        Object message=packet.getConstructor(float.class,float.class,byte.class,boolean.class,BlockPos.class,boolean.class).newInstance(35f,5f,(byte)1,false,pos,false);
        float before=((Number)tile.getClass().getMethod("getYaw").invoke(tile)).floatValue();packet.getMethod("handle",context).invoke(message,ctx);
        check(((Number)tile.getClass().getMethod("getYaw").invoke(tile)).floatValue()==before,"forged/stale cannon control packet rejected");
        vessel.grants.players.add(visitor.getUUID());packet.getMethod("handle",context).invoke(message,ctx);
        check(((Number)tile.getClass().getMethod("getYaw").invoke(tile)).floatValue()!=before,"authorised cannon control packet accepted");
        ProtectionEvents.open(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(visitor,visitor.inventoryMenu));
        vessel.grants.players.clear();packet.getMethod("handle",context).invoke(message,ctx);
        check(!ProtectionEvents.allowsOpen(visitor),"stale inventory session denied after revocation");
        tile.getClass().getMethod("setAttributes",float.class,float.class,byte.class,boolean.class,net.minecraft.world.entity.player.Player.class).invoke(tile,70f,5f,(byte)1,false,visitor);
        check(((Number)tile.getClass().getMethod("getYaw").invoke(tile)).floatValue()!=70,"direct cannon mutation also denied");
        ((CannonShipGuard)tile).vs_sails$validateEditor();
        check(tile.getClass().getMethod("getPlayerWhoMayEdit").invoke(tile)==null,"revocation releases the stale cannon editor lock");
    }
    void tick(TickEvent.ServerTickEvent event) {
        if(server==null || event.phase!=TickEvent.Phase.END)return;
        try {
            ticks++;
            if(ticks==10)build();if(ticks==30){loaded();legacyMass();}if(ticks==60)frozen();
            if(ticks==70)towny();
            if(ticks==90)editHull("remnant",false);
            if(ticks==120) {
                check(ship("remnant").isStatic() && SailsShipControl.getOrCreate(ship("remnant"),level).sailSafetyHeld,"stripped hull becomes safe cloth hold");
                for(String name:List.of("cloth","single","external"))check(Math.abs(ship(name).getTransform().getPositionInWorld().y()-frozenY.get(name))<.01,"cloth remains motionless: "+name);
                editHull("cloth",true);editHull("external",true);
            }
            if(ticks==140) {
                check(!SailsShipControl.getOrCreate(ship("cloth"),level).sailSafetyHeld && !ship("cloth").isStatic(),"adding hull releases owned cloth hold");
                check(ship("external").isStatic(),"adding hull preserves external administrator freeze");
                var body=ship("cloth");
                VSGameUtilsKt.getShipObjectWorld(level).teleportShip(body,new org.valkyrienskies.core.impl.game.ShipTeleportDataImpl(
                        body.getTransform().getPositionInWorld(),body.getTransform().getShipToWorldRotation(),new org.joml.Vector3d(200,0,0),new org.joml.Vector3d(),body.getChunkClaimDimension(),1.0,body.getTransform().getPositionInShip()));
            }
            if(ticks==160) {
                check(SailsShipControl.getOrCreate(ship("cloth"),level).motionSafetyTripped && ship("cloth").isStatic(),"real excessive velocity trips emergency hold");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new Vec3(12,120,12)),"vs_sails ship recover "+ship("cloth").getId());
            }
            if(ticks==180) {
                check(!SailsShipControl.getOrCreate(ship("cloth"),level).motionSafetyTripped && !ship("cloth").isStatic(),"administrator recover command clears fault after zero-velocity teleport");
                Object manager=Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);
                Class.forName("org.bukkit.plugin.PluginManager").getMethod("disablePlugin",Class.forName("org.bukkit.plugin.Plugin")).invoke(manager,townyPlugin);
                check(!ShipProtection.allows(visitor,level,ship("remnant")),"Towny unavailable denies access");
                System.out.println("SHIP_SAFETY_SMOKE_PASS assertions="+checks);server.halt(false);server=null;
            }
        }catch(Throwable failed){failed.printStackTrace();System.out.println("SHIP_SAFETY_SMOKE_FAIL "+failed);server.halt(false);server=null;}
    }
}
