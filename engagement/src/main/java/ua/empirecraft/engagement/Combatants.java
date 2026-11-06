package ua.empirecraft.engagement;

import java.util.*;
import java.lang.reflect.Method;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import static ua.empirecraft.engagement.CombatPolicy.*;

public final class Combatants {
    public record Unit(UUID commander,UUID group){}
    public record Party(Side side,String key,String town,UUID person,UUID group,Long ship,Entity entity){}
    private record Access(Method owned,Method owner,Method group){}
    private static final Map<Class<?>,Optional<Access>> access=new HashMap<>();
    private Combatants(){}
    public static Unit unit(Entity entity){
        var type=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());if(type==null||!type.getNamespace().equals("recruits"))return null;
        try {
            var methods=access.computeIfAbsent(entity.getClass(),c->{try{return Optional.of(new Access(c.getMethod("getIsOwned"),c.getMethod("getOwnerUUID"),c.getMethod("getGroup")));}catch(ReflectiveOperationException no){return Optional.empty();}});
            if(methods.isEmpty()||!Boolean.TRUE.equals(methods.get().owned.invoke(entity)))return null;
            UUID owner=(UUID)methods.get().owner.invoke(entity),group=(UUID)methods.get().group.invoke(entity);return owner==null?null:new Unit(owner,group);
        }catch(ReflectiveOperationException|RuntimeException e){throw new IllegalStateException("Recruit identity unavailable",e);}
    }
    public static boolean participant(Entity e){return e instanceof ServerPlayer||unit(e)!=null;}
    public static String personKey(UUID id){return "P:"+id;}
    public static String entityKey(UUID id){return "E:"+id;}
    public static String groupKey(UUID id){return "C:"+id;}
    public static String shipKey(long id){return "S:"+id;}
    public static boolean active(Entity e){
        if(!(e.level() instanceof ServerLevel level))return false;var ledger=CombatLedger.get(level.getServer());long now=level.getServer().overworld().getGameTime();var u=unit(e);
        return ledger.active(e instanceof ServerPlayer?personKey(e.getUUID()):entityKey(e.getUUID()),now)||u!=null&&u.group()!=null&&ledger.active(groupKey(u.group()),now);
    }
    public static boolean crew(Entity e,LoadedServerShip ship){
        if(!(e.level() instanceof ServerLevel level))return false;var unit=unit(e);UUID owner=e instanceof ServerPlayer?e.getUUID():unit==null?null:unit.commander();
        return owner!=null&&Government.crew(level,owner,ship.getId());
    }
    public static Party party(Entity e){
        if(!(e.level() instanceof ServerLevel level))return new Party(new Side(Kind.ENVIRONMENT,Flag.NONE,Water.WILDERNESS,false,true),"","",null,null,null,e);
        if(!participant(e)){
            for(Entity rider:e.getIndirectPassengers())if(participant(rider)){var party=party(rider);return new Party(party.side(),party.key(),party.town(),party.person(),party.group(),party.ship(),e);}
            return new Party(new Side(Kind.ENVIRONMENT,Flag.NONE,Water.WILDERNESS,false,true),"","",null,null,null,e);
        }
        var unit=unit(e);UUID owner=e instanceof ServerPlayer?e.getUUID():unit.commander();
        var aboard=Deck.source(e);if(aboard!=null&&crew(e,aboard))return ship(level,aboard,e,owner,unit==null?null:unit.group());
        try {
            String city=Government.town(level,owner);var water=city.isBlank()?Water.WILDERNESS:Government.point(level,city,e.getX(),e.getZ());
            return new Party(new Side(unit==null?Kind.PERSON:Kind.RECRUIT,Flag.NONE,water,active(e),true),unit!=null&&unit.group()!=null?groupKey(unit.group()):personKey(owner),city,owner,unit==null?null:unit.group(),null,e);
        }catch(RuntimeException unavailable){return new Party(new Side(unit==null?Kind.PERSON:Kind.RECRUIT,Flag.NONE,Water.UNKNOWN,active(e),unit==null),personKey(owner),"",owner,unit==null?null:unit.group(),null,e);}
    }
    /** Cargo/decorations are protected targets; they cannot initiate combat for a hull. */
    public static Party target(Entity entity){
        var ordinary=party(entity);
        if(ordinary.side().asset()||participant(entity)||entity instanceof net.minecraft.world.entity.LivingEntity&&!(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)||entity instanceof net.minecraft.world.entity.item.ItemEntity||ShotOrigins.dangerous(entity)||!(entity.level() instanceof ServerLevel level))return ordinary;
        var ship=org.valkyrienskies.mod.common.VSGameUtilsKt.getLoadedShipManagingPos(level,entity.getX(),entity.getY(),entity.getZ());if(ship==null)ship=Deck.support(entity);
        return ship==null?ordinary:ship(level,ship,null,null,null);
    }
    public static Party ship(ServerLevel level,LoadedServerShip ship,Entity entity,UUID person,UUID group){
        long id=ship.getId();boolean active=CombatLedger.get(level.getServer()).active(shipKey(id),level.getServer().overworld().getGameTime())||entity!=null&&active(entity);
        try {
            var data=Government.read(level,"ship",Map.of("ship",id));var flag=NavalFlags.effective(level,ship,data);Water water=Water.WILDERNESS;
            for(Object raw:(List<?>)data.getOrDefault("waters",List.of("UNKNOWN"))){var next=Water.valueOf(raw.toString());if(rank(next,flag)>rank(water,flag))water=next;}
            return new Party(new Side(Kind.SHIP,flag,water,active,Boolean.TRUE.equals(data.get("ready"))),shipKey(id),Objects.toString(data.get("town"),""),person,group,id,entity);
        }catch(RuntimeException unavailable){return new Party(new Side(Kind.SHIP,Flag.PIRATE,Water.UNKNOWN,active,false),shipKey(id),"",person,group,id,entity);}
    }
    private static int rank(Water water,Flag flag){return switch(water){case UNKNOWN->10;case OWN->9;case ALLIED->8;case NEUTRAL->flag==Flag.PEACEFUL?7:2;case ENEMY->1;case WILDERNESS->0;};}
}
