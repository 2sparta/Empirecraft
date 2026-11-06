package ua.empirecraft.engagement;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import com.quintonc.vs_sails.protection.ShipProtection;

/** Server-only DTO API; no client packet can submit a flag texture, owner, combat expiry or launch origin. */
public final class EngagementNative {
    private EngagementNative(){}
    public static Map<String,Object> request(String action,Map<String,Object> data){
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null||!server.isSameThread())throw new IllegalStateException("Engagement requires the server thread");
        if(action.equals("combat")){
            long id=((Number)data.get("ship")).longValue();return Map.of("active",CombatLedger.get(server).active(Combatants.shipKey(id),server.overworld().getGameTime()));
        }
        if(action.equals("towny-damage")){
            net.minecraft.world.entity.Entity victim=find(UUID.fromString(data.get("victim").toString())),attacker=data.containsKey("attacker")?find(UUID.fromString(data.get("attacker").toString())):null;
            if(victim==null||!(victim.level() instanceof ServerLevel level))return Map.of("handled",false);
            var origin=ShotOrigins.find(attacker);if(origin==null)origin=ShotOrigins.current();
            var source=origin!=null?origin.party():attacker==null?EngagementRuntime.environment():Combatants.party(attacker);var target=Combatants.party(victim);
            // An aggressor's combat tag cannot turn unrelated protected civilians
            // into participants. Towny retains its ordinary decision for them.
            boolean handled=target.side().asset()||target.side().active();
            return Map.of("handled",handled,"allow",!handled||EngagementRuntime.decide(source,target,level,true).allow());
        }
        ServerPlayer player=server.getPlayerList().getPlayer(UUID.fromString(data.get("player").toString()));if(player==null)throw new IllegalArgumentException("Гравець вийшов із сервера.");
        if(action.equals("towny-block")){
            var pos=new BlockPos(((Number)data.get("x")).intValue(),((Number)data.get("y")).intValue(),((Number)data.get("z")).intValue());var ship=ShipProtection.ship(player.level(),pos);
            Boolean ram=RamCombat.permitted(player.serverLevel(),ship);if(ram!=null)return Map.of("handled",true,"allow",ram);
            return ship==null?Map.of("handled",false):Map.of("handled",true,"allow",Government.controls(player.serverLevel(),player.getUUID(),ship.getId())||EngagementRuntime.decide(Combatants.party(player),Combatants.ship(player.serverLevel(),ship,null,null,null),player.serverLevel(),true).allow());
        }
        if(action.equals("design"))return PaintingDesign.inspect(player,data.get("source").toString());
        if(action.equals("look")){
            var hit=player.pick(8,0,false);LoadedServerShip ship=hit instanceof BlockHitResult block?ShipProtection.ship(player.level(),block.getBlockPos()):null;if(ship==null)ship=Deck.support(player);
            if(ship==null)throw new IllegalArgumentException("Наведіться на морський прапор свого корабля або станьте на його палубу.");return Map.of("ship",ship.getId());
        }
        long id=((Number)data.get("ship")).longValue();var level=player.serverLevel();var ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);
        if(ship==null||!ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(level)))throw new IllegalArgumentException("Цей корабель не завантажено у вашому світі.");
        if(action.equals("status"))return Map.of("mode",NavalFlags.effective(level,ship,Government.read(level,"ship",Map.of("ship",id))).name(),"combat",CombatLedger.get(server).active(Combatants.shipKey(id),server.overworld().getGameTime()));
        var mode=CombatPolicy.Flag.valueOf(data.get("mode").toString());if(mode==CombatPolicy.Flag.NONE)throw new IllegalArgumentException("Виберіть морський прапор.");
        var quote=NavalFlags.configure(player,ship,mode,false);String stamp=PaintingDesign.hash(player.getUUID()+"|"+quote+"|"+Government.read(level,"ship",Map.of("ship",id)).get("town"));
        if(action.equals("inspect")){var out=new LinkedHashMap<String,Object>(quote);out.put("stamp",stamp);return out;}
        if(action.equals("apply")){
            if(!stamp.equals(data.get("expected")))throw new IllegalArgumentException("Прапор або права керування змінилися. Перегляньте умови повторно.");
            return NavalFlags.configure(player,ship,mode,true);
        }
        throw new IllegalArgumentException("Невідома дія бойової системи.");
    }
    private static net.minecraft.world.entity.Entity find(UUID id){var server=ServerLifecycleHooks.getCurrentServer();for(var level:server.getAllLevels()){var e=level.getEntity(id);if(e!=null)return e;}return server.getPlayerList().getPlayer(id);}
}
