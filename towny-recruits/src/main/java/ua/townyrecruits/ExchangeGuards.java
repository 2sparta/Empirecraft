package ua.townyrecruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

/** Authenticate native per-soldier GUI packets too; their native UUID search alone is not ownership proof. */
public final class ExchangeGuards {
    public static final Map<String,String> OWNER_FIELDS=Map.ofEntries(
        Map.entry("MessageAggro","player"),Map.entry("MessageAttack","playerUuid"),Map.entry("MessageBackToMountEntity","uuid"),
        Map.entry("MessageClearTarget","uuid"),Map.entry("MessageClearUpkeep","uuid"),Map.entry("MessageDismount","uuid"),
        Map.entry("MessageFaceCommand","player_uuid"),Map.entry("MessageFormationFollowMovement","player_uuid"),Map.entry("MessageMountEntity","uuid"),
        Map.entry("MessageMovement","player_uuid"),Map.entry("MessageProtectEntity","uuid"),Map.entry("MessageRangedFire","player"),
        Map.entry("MessageRest","player"),Map.entry("MessageShields","player"),Map.entry("MessageStrategicFire","player"),
        Map.entry("MessageUpkeepEntity","player_uuid"),Map.entry("MessageUpkeepPos","player"),
        Map.entry("MessageAssignGroupToCompanion","ownerUUID"),Map.entry("MessageRemoveAssignedGroupFromCompanion","owner"),Map.entry("MessageApplyNoGroup","owner")
    );
    private static final ClassValue<List<Field>> FIELDS=new ClassValue<>(){
        protected List<Field> computeValue(Class<?> type){var out=new ArrayList<Field>();for(Field field:type.getDeclaredFields())if(field.getType()==UUID.class){field.setAccessible(true);out.add(field);}return List.copyOf(out);}
    };
    private ExchangeGuards() {}
    public static boolean authenticOwner(Object packet,UUID sender){
        String expected=OWNER_FIELDS.get(packet.getClass().getSimpleName());if(expected==null)return true;
        try{for(Field field:FIELDS.get(packet.getClass()))if(field.getName().equals(expected))return sender!=null&&sender.equals(field.get(packet));}catch(IllegalAccessException ignored){}
        return false;
    }
    public static boolean controls(AbstractRecruitEntity recruit,Player player){return player!=null&&RegimentExchange.mayControl(recruit,player.m_20148_());}
    public static boolean packet(Object packet,NetworkEvent.Context context){
        var player=context.getSender();if(player==null)return false;
        if(!authenticOwner(packet,player.m_20148_()))return false;
        try {
            String name=packet.getClass().getSimpleName();boolean individual=!OWNER_FIELDS.containsKey(name);
            if(Set.of("MessageAssignGroupToCompanion","MessageRemoveAssignedGroupFromCompanion").contains(name))individual=true;
            if(name.equals("MessageAggro")){Field gui=packet.getClass().getDeclaredField("fromGui");gui.setAccessible(true);individual=gui.getBoolean(packet);}
            if(!individual)return true;
            Set<UUID> ids=new HashSet<>();for(Field field:FIELDS.get(packet.getClass())){Object id=field.get(packet);if(id!=null)ids.add((UUID)id);}
            for(var recruit:player.m_20193_().m_6443_(AbstractRecruitEntity.class,player.m_20191_().m_82400_(128),r->ids.contains(r.m_20148_())))if(!controls(recruit,player))return false;
            return true;
        }catch(ReflectiveOperationException error){return false;}
    }
}
