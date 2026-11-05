package com.quintonc.vs_sails.protection;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** JDK-only DTO boundary between the native mod and the authoritative Bukkit fleet ledger. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vs_sails")
public final class FleetNative {
    private record Body(ServerLevel level,ServerShip ship) {}
    private record Assembly(UseOnContext context,ItemStack item,net.minecraft.world.level.block.state.BlockState block,String token,int expires) {}
    private static final Map<UUID,Assembly> ASSEMBLIES=new HashMap<>();
    private static final Map<UUID,String> APPROVED=new HashMap<>();
    private static final Map<UUID,double[]> VIEWS=new HashMap<>();
    private FleetNative(){}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent e){ASSEMBLIES.clear();APPROVED.clear();VIEWS.clear();}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){UUID id=e.getEntity().getUUID();ASSEMBLIES.remove(id);APPROVED.remove(id);VIEWS.remove(id);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void expire(net.minecraftforge.event.TickEvent.ServerTickEvent e){
        var server=ServerLifecycleHooks.getCurrentServer();if(e.phase==net.minecraftforge.event.TickEvent.Phase.END && server!=null && server.getTickCount()%20==0)ASSEMBLIES.values().removeIf(a->a.expires<server.getTickCount());
    }
    private static MinecraftServer server(){var s=ServerLifecycleHooks.getCurrentServer();if(s==null || !s.isSameThread())throw new IllegalStateException("Fleet needs the server thread");return s;}
    public static Object plugin()throws ReflectiveOperationException {
        try{var b=Class.forName("org.bukkit.Bukkit");Object manager=b.getMethod("getPluginManager").invoke(null);
            Object p=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"EmpirecraftFeudal");
            if(p==null || !Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p)))return null;
            p.getClass().getMethod("fleetRequest",String.class,Map.class);return p;
        }catch(ClassNotFoundException|NoSuchMethodException absent){return null;}
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> invoke(String action,Map<String,Object> data)throws ReflectiveOperationException {
        Object plugin=plugin();return plugin==null?null:(Map<String,Object>)plugin.getClass().getMethod("fleetRequest",String.class,Map.class).invoke(plugin,action,data);
    }
    public static Map<String,Object> policy(ServerLevel level,long id,UUID actor)throws ReflectiveOperationException {
        var data=new LinkedHashMap<String,Object>();data.put("ship",id);if(actor!=null)data.put("player",actor.toString());return invoke("policy",data);
    }
    private static Body body(long id){
        for(var level:server().getAllLevels()){
            var ship=VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(id);
            if(ship!=null && ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(level)))return new Body(level,ship);
        }return null;
    }
    private static String world(ServerLevel level)throws ReflectiveOperationException {
        Object world=level.getClass().getMethod("getWorld").invoke(level);return world.getClass().getMethod("getUID").invoke(world).toString();
    }
    private static Map<String,Object> snapshot(Body body)throws ReflectiveOperationException {
        var ship=body.ship;var bounds=ship.getShipAABB();if(bounds==null)return Map.of();var data=new LinkedHashMap<String,Object>();
        List<List<Double>> polygon=new ArrayList<>();
        for(int x:new int[]{bounds.minX(),bounds.maxX()})for(int y:new int[]{bounds.minY(),bounds.maxY()})for(int z:new int[]{bounds.minZ(),bounds.maxZ()}){
            var p=ship.getShipToWorld().transformPosition(new Vector3d(x,y,z));if(!p.isFinite())return Map.of();polygon.add(List.of(p.x,p.z));
        }
        var registry=ShipRegistry.get(server());var owner=registry.ships.get(ship.getId());var position=ship.getTransform().getPositionInWorld();
        data.put("id",ship.getId());data.put("world",world(body.level));data.put("dimension",body.level.dimension().location().toString());data.put("footprint",polygon);
        data.put("x",position.x());data.put("y",position.y());data.put("z",position.z());data.put("town",owner==null||owner.town==null?"":"t:"+owner.town);
        data.put("founder",owner==null||owner.founder==null?"":owner.founder.toString());data.put("name",owner!=null&&!owner.name.isBlank()?owner.name:ship.getSlug());
        data.put("information","Розмір ≈ "+(bounds.maxX()-bounds.minX())+" × "+(bounds.maxY()-bounds.minY())+" × "+(bounds.maxZ()-bounds.minZ())+" блоків; маса ≈ "+Math.round(ship.getInertiaData().getMass())+" кг");
        data.put("loaded",VSGameUtilsKt.getShipObjectWorld(body.level).getLoadedShips().getById(ship.getId())!=null);data.put("frozen",ship.isStatic());
        data.put("captain",owner==null||owner.captain==null?"":owner.captain.toString());data.put("crew",owner==null?List.of():owner.grants.players.stream().map(UUID::toString).toList());
        return data;
    }
    public static boolean beforeAssembly(UseOnContext context){
        if(context.getPlayer()==null || APPROVED.containsKey(context.getPlayer().getUUID()))return false;
        try{if(plugin()==null)return false;UUID player=context.getPlayer().getUUID();String token=UUID.randomUUID().toString();
            ASSEMBLIES.put(player,new Assembly(context,context.getItemInHand().copy(),context.getLevel().getBlockState(context.getClickedPos()),token,server().getTickCount()+400));
            var result=invoke("assembly",Map.of("player",player.toString(),"token",token));
            if(result==null || !Boolean.TRUE.equals(result.get("ok")))throw new IllegalStateException("Town selection unavailable");return true;
        }catch(Exception failed){context.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal("Вибір міста недоступний. Спробуйте ще раз."),true);return true;}
    }
    public static String assemblyTown(UUID player){return APPROVED.get(player);}
    public static void registered(long id,UUID founder)throws ReflectiveOperationException {
        String town=APPROVED.getOrDefault(founder,"");invoke("registered",Map.of("ship",id,"player",founder.toString(),"town",town));
    }
    private static void resume(UUID player,String token,String town)throws Exception {
        Assembly a=ASSEMBLIES.remove(player);ServerPlayer actor=server().getPlayerList().getPlayer(player);
        if(a==null || actor==null || actor.isSpectator() || !actor.mayBuild() || !a.token.equals(token) || a.expires<server().getTickCount() || actor.level()!=a.context.getLevel()
                || !ItemStack.matches(a.item,actor.getItemInHand(a.context.getHand())) || actor.distanceToSqr(Vec3.atCenterOf(a.context.getClickedPos()))>64
                || !actor.level().hasChunkAt(a.context.getClickedPos()) || actor.level().getBlockState(a.context.getClickedPos())!=a.block
                || actor.getCooldowns().isOnCooldown(a.item.getItem()))throw new IllegalStateException("Запит створення корабля завершено.");
        var allowed=invoke("can-build",Map.of("player",player.toString(),"town",town));
        if(allowed==null || !Boolean.TRUE.equals(allowed.get("ok")))throw new IllegalStateException("Це місто недоступне.");
        var fresh=new UseOnContext(actor,a.context.getHand(),new BlockHitResult(a.context.getClickLocation(),a.context.getClickedFace(),a.context.getClickedPos(),a.context.isInside()));
        APPROVED.put(player,town);try{a.item.getItem().useOn(fresh);}finally{APPROVED.remove(player);}
    }
    private static void project(Body body,Map<String,Object> data){
        var registry=ShipRegistry.get(server());var v=registry.ships.computeIfAbsent(body.ship.getId(),id->new ShipAccess.Vessel(null,null));
        String town=data.getOrDefault("town","").toString(),captain=data.getOrDefault("captain","").toString(),lock=data.getOrDefault("lock","").toString();
        v.town=town.isEmpty()?null:UUID.fromString(town.replace("t:",""));v.captain=captain.isEmpty()?null:UUID.fromString(captain);v.managed=true;v.offeredTo=null;
        v.name=data.getOrDefault("name",body.ship.getSlug()).toString();v.revision=((Number)data.getOrDefault("revision",0)).longValue();
        v.grants.towns.clear();v.grants.players.clear();for(Object person:(List<?>)data.getOrDefault("crew",List.of()))v.grants.players.add(UUID.fromString(person.toString()));
        if(v.captain!=null)v.grants.players.add(v.captain);
        if(!lock.isEmpty()){
            if(v.marketLock.isEmpty())v.marketWasStatic=body.ship.isStatic();v.marketLock=lock;body.ship.setStatic(true);
        }else if(!v.marketLock.isEmpty()){
            boolean held=v.marketWasStatic;
            if(body.ship instanceof org.valkyrienskies.core.api.ships.LoadedServerShip loaded){
                var control=loaded.getAttachment(com.quintonc.vs_sails.ship.SailsShipControl.class);if(control!=null)held|=control.sailSafetyHeld;
            }
            v.marketLock="";if(!held)body.ship.setStatic(false);
        }
        if(!Objects.equals(body.ship.getSlug(),v.name))body.ship.setSlug(v.name);registry.setDirty();
    }
    private static boolean visible(UUID observer,Body body){
        ServerPlayer player=server().getPlayerList().getPlayer(observer);if(player==null || player.serverLevel()!=body.level)return false;
        var b=body.ship.getWorldAABB();if(b==null)return false;Vec3 eye=player.getEyePosition(),look=player.getViewVector(1);
        double radius=Math.min(192,server().getPlayerList().getViewDistance()*16.0);double[] view=VIEWS.getOrDefault(observer,new double[]{110,80});
        Map<Vec3,Boolean> points=new LinkedHashMap<>();var localBounds=body.ship.getShipAABB();if(localBounds==null)return false;
        for(int x:new int[]{localBounds.minX(),(localBounds.minX()+localBounds.maxX()-1)/2,localBounds.maxX()-1})
            for(int y:new int[]{localBounds.minY(),(localBounds.minY()+localBounds.maxY()-1)/2,localBounds.maxY()-1})
                for(int z:new int[]{localBounds.minZ(),(localBounds.minZ()+localBounds.maxZ()-1)/2,localBounds.maxZ()-1}){
                    BlockPos local=new BlockPos(x,y,z);if(!body.level.hasChunkAt(local))continue;var state=body.level.getBlockState(local);
                    boolean solid=!state.isAir() && !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock);
                    var p=body.ship.getShipToWorld().transformPosition(new Vector3d(x+.5,y+.5,z+.5));points.put(new Vec3(p.x,p.y,p.z),solid);
                }
        Vec3 right=new Vec3(-look.z,0,look.x).normalize(),up=right.cross(look).normalize();
        for(var sample:points.entrySet()){Vec3 point=sample.getKey(),delta=point.subtract(eye);double distance=delta.length();if(distance>radius || distance<.05)continue;
            double forward=delta.dot(look);if(forward<=0 || Math.abs(Math.toDegrees(Math.atan2(delta.dot(right),forward)))>view[0]*.5
                    || Math.abs(Math.toDegrees(Math.atan2(delta.dot(up),forward)))>view[1]*.5)continue;
            boolean loaded=true;for(int step=0;step<=distance;step+=8)if(!body.level.hasChunkAt(BlockPos.containing(eye.add(delta.scale(step/distance))))){loaded=false;break;}if(!loaded)continue;
            var hit=org.valkyrienskies.mod.common.world.RaycastUtilsKt.clipIncludeShips(body.level,new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            // A ray towards an empty corner may still hit the actual hull en route.
            // A miss proves visibility only when the endpoint is a verified hull block.
            if(hit.getType()==HitResult.Type.MISS){if(sample.getValue())return true;continue;}
            var owner=VSGameUtilsKt.getShipManagingPos(body.level,hit.getBlockPos());if(owner!=null && owner.getId()==body.ship.getId())return true;
        }return false;
    }
    public static Map<String,Object> request(String action,Map<String,Object> data)throws Exception {
        server();
        if(action.equals("at")){
            String world=data.get("world").toString();BlockPos pos=new BlockPos(((Number)data.get("x")).intValue(),((Number)data.get("y")).intValue(),((Number)data.get("z")).intValue());
            for(var level:server().getAllLevels())if(world(level).equals(world)){
                var direct=VSGameUtilsKt.getShipManagingPos(level,pos);if(direct!=null)return Map.of("ship",direct.getId());
                if(!level.hasChunkAt(pos) || !level.getBlockState(pos).getCollisionShape(level,pos).isEmpty())return Map.of();
                for(var ship:VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()){
                    if(!ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(level)))continue;
                    var p=ship.getWorldToShip().transformPosition(new Vector3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));var local=BlockPos.containing(p.x,p.y,p.z);
                    if(level.hasChunkAt(local) && !level.getBlockState(local).isAir())return Map.of("ship",ship.getId());
                }return Map.of();
            }return Map.of();
        }
        if(action.equals("list")){
            List<Map<String,Object>> ships=new ArrayList<>();Set<Long> seen=new HashSet<>();
            for(var level:server().getAllLevels())for(var ship:VSGameUtilsKt.getShipObjectWorld(level).getAllShips())if(seen.add(ship.getId())){Body b=body(ship.getId());if(b!=null)ships.add(snapshot(b));}
            return Map.of("ships",ships);
        }
        if(action.equals("assembly-resume")){resume(UUID.fromString(data.get("player").toString()),data.get("token").toString(),data.get("town").toString());return Map.of("ok",true);}
        if(action.equals("view")){double h=((Number)data.get("horizontal")).doubleValue(),v=((Number)data.get("vertical")).doubleValue();if(Double.isFinite(h)&&Double.isFinite(v))VIEWS.put(UUID.fromString(data.get("player").toString()),new double[]{Math.max(30,Math.min(160,h)),Math.max(30,Math.min(120,v))});return Map.of("ok",true);}
        Body body=body(((Number)data.get("ship")).longValue());if(body==null)return Map.of();
        if(action.equals("snapshot"))return snapshot(body);
        if(action.equals("project")){project(body,data);return Map.of("ok",true);}
        if(action.equals("visible"))return Map.of("visible",visible(UUID.fromString(data.get("player").toString()),body));
        throw new IllegalArgumentException("Unknown native fleet action");
    }
}
