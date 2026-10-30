package ua.empirecraft.forging;
import java.util.*;
/** A cancelled mining attempt still stays cancelled when its message is throttled. */
public final class ToolNoticeGate {
    private final Map<UUID,Long> last=new HashMap<>();
    public boolean allow(UUID player,long milliseconds){
        Long previous=last.get(player);
        if(previous!=null && milliseconds>=previous && milliseconds-previous<1000)return false;
        last.put(player,milliseconds);return true;
    }
    public void forget(UUID player){last.remove(player);}
}
