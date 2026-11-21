package ua.townyrecruits;
import java.util.*;
/** Trusted server plugin bridge. Authorization is enforced by the requesting admin command. */
public final class VillageApi {
    private VillageApi(){}
    public static Map<String,Object> approve(String id,UUID town,int houses,int hours){
        var b=Bridge.INSTANCE;if(!org.bukkit.Bukkit.isPrimaryThread()||b==null||!b.ready)throw new IllegalStateException("Recruitment bridge unavailable");
        b.recruitment.state.approveVillage(id,town,houses,hours,System.currentTimeMillis(),new java.util.Random());
        var v=b.recruitment.state.villages.get(id);return Map.of("capacity",v.capacity(),"available",v.available,"houses",v.households.size());
    }
}
