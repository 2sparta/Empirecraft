package ua.empirecraft.engagement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.quintonc.vs_sails.protection.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.saveddata.SavedData;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** An actual server item use binds the approved application to the physical ship it creates. */
public final class ShipAssemblyGate {
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final class Scope {
        final ServerLevel level;final UUID admin,applicant;final String permit,application,name;boolean captured;
        Scope(ServerLevel level,UUID admin,Map<String,Object> data){this.level=level;this.admin=admin;permit=data.get("permit").toString();application=data.get("application").toString();name=data.get("name").toString();applicant=UUID.fromString(data.get("applicant").toString());}
    }
    private ShipAssemblyGate(){}
    private static Map<String,Object> request(String action,Map<String,Object> data){try{var result=com.quintonc.vs_sails.protection.FleetNative.invoke(action,data);if(result==null)throw new IllegalStateException("Система заявок на кораблі ще не готова.");return result;}catch(ReflectiveOperationException error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();throw new IllegalStateException(Objects.toString(cause.getMessage(),"Система заявок на кораблі недоступна."),error);}}
    private static Map<String,Object> location(UseOnContext context){var level=(ServerLevel)context.getLevel();var pos=context.getClickedPos();return Map.of("player",context.getPlayer().getUUID().toString(),"world",Government.world(level),"x",pos.getX(),"y",pos.getY(),"z",pos.getZ());}
    public static boolean active(){return CURRENT.get()!=null;}
    public static void requestForm(UseOnContext context){try{request("ship-request-open",location(context));}catch(RuntimeException error){if(context.getPlayer()!=null)context.getPlayer().displayClientMessage(Component.literal(error.getMessage()),true);}}
    public static InteractionResult use(UseOnContext context,Operation<InteractionResult> original){
        if(context.getLevel().isClientSide||CURRENT.get()!=null)return original.call(context);
        if(!(context.getLevel() instanceof ServerLevel level)||!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.FAIL;
        try{
            boolean admin=Boolean.TRUE.equals(request("ship-tool-admin",Map.of("player",player.getUUID().toString())).get("admin"));
            if(!admin){requestForm(context);return InteractionResult.CONSUME;}
            // Ship Creator also creates a NEW body when clicked on an existing hull.
            // Never let that operation escape the application/approval boundary.
            if(VSGameUtilsKt.getShipObjectManagingPos(level,context.getClickedPos())!=null)throw new IllegalStateException("Цей блок уже належить фізичному кораблю.");
            var scope=new Scope(level,player.getUUID(),request("ship-begin",location(context)));CURRENT.set(scope);
            try{return original.call(context);}
            finally{
                CURRENT.remove();
                if(!scope.captured)request("ship-abort",Map.of("player",scope.admin.toString(),"permit",scope.permit));
                else flush(level.getServer());
            }
        }catch(RuntimeException denied){player.displayClientMessage(Component.literal(Objects.toString(denied.getMessage(),"Збирання корабля недоступне.")),true);return InteractionResult.FAIL;}
    }
    public static void capture(ServerShip ship){
        var scope=CURRENT.get();if(scope==null||scope.captured||ship==null)return;scope.captured=true;
        var registry=ShipRegistry.get(scope.level.getServer());var vessel=new ShipAccess.Vessel(null,scope.applicant);vessel.managed=true;vessel.name=scope.name;registry.ships.put(ship.getId(),vessel);((SavedData)registry).setDirty();
        var links=Links.get(scope.level.getServer());links.values.put(ship.getId(),new Link(scope.level.dimension().location().toString(),scope.permit,ship.isStatic()));links.setDirty();
        ship.setStatic(true); // Keep the hull in place until the administrator finishes its city/name menu.
    }
    public static void flush(MinecraftServer server){
        var links=Links.get(server);for(var entry:List.copyOf(links.values.entrySet())){
            var link=entry.getValue();var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(link.dimension)));
            if(level==null)continue;var ship=VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(entry.getKey());if(ship==null)continue;
            try{var result=request("ship-complete",Map.of("permit",link.permit,"ship",entry.getKey()));
                if(Boolean.TRUE.equals(result.get("assigned"))){ship.setStatic(link.wasStatic);links.values.remove(entry.getKey());links.setDirty();}
            }catch(RuntimeException unavailable){/* The durable link is retried after startup or a temporary backend failure. */}
        }
    }
    private record Link(String dimension,String permit,boolean wasStatic){}
    public static final class Links extends SavedData {
        final Map<Long,Link> values=new HashMap<>();
        static Links get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(Links::load,Links::new,"empirecraft_ship_applications");}
        static Links load(CompoundTag tag){var data=new Links();var list=tag.getList("links",10);for(int i=0;i<list.size();i++){var row=list.getCompound(i);data.values.put(row.getLong("ship"),new Link(row.getString("dimension"),row.getString("permit"),row.getBoolean("static")));}return data;}
        @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();values.forEach((ship,link)->{var row=new CompoundTag();row.putLong("ship",ship);row.putString("dimension",link.dimension);row.putString("permit",link.permit);row.putBoolean("static",link.wasStatic);list.add(row);});tag.put("links",list);return tag;}
    }
}
