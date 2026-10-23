package ua.empirecraft.feudal;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;

/** Every economic edit invalidates both revision-bound confirmations. */
final class TradeSession {
    static final int MAX_STACKS=16;
    static final class Offer {final Map<Integer,ItemStack> items=new TreeMap<>();final Map<String,Long> coins=new TreeMap<>();String currency="BASE";long ready=-1;}
    final UUID id=UUID.randomUUID(),a,b;
    final Offer first=new Offer(),second=new Offer();final Set<UUID> editing=new HashSet<>();
    long revision,settleAt,touched;int renderedCountdown=-1;
    TradeSession(UUID a,UUID b,long now){require(!a.equals(b),"Оберіть іншого гравця.");this.a=a;this.b=b;touched=now;}
    static boolean inRange(Object aw,double ax,double ay,double az,Object bw,double bx,double by,double bz,double distance){double dx=ax-bx,dy=ay-by,dz=az-bz;return aw!=null&&aw.equals(bw)&&dx*dx+dy*dy+dz*dz<=distance*distance;}
    UUID other(UUID p){require(p.equals(a)||p.equals(b),"Ви не учасник обміну.");return p.equals(a)?b:a;}
    Offer offer(UUID p){other(p);return p.equals(a)?first:second;}
    void changed(long now){revision++;first.ready=second.ready=-1;settleAt=0;touched=now;renderedCountdown=-1;}
    void confirm(UUID p,long now,long delay){require(editing.isEmpty(),"Зачекайте введення суми монет.");var o=offer(p);o.ready=o.ready==revision?-1:revision;settleAt=first.ready==revision&&second.ready==revision?now+delay:0;touched=now;renderedCountdown=-1;}
    boolean due(long now){return settleAt>0&&now>=settleAt&&first.ready==revision&&second.ready==revision&&editing.isEmpty();}
    void coins(UUID p,String code,long amount,long now){require(amount>=0&&amount<=CurrencyService.MAX_MONEY,"Неправильна сума монет.");var o=offer(p);if(o.coins.getOrDefault(code,0L)==amount)return;if(amount==0)o.coins.remove(code);else o.coins.put(code,amount);changed(now);}
    void toggle(UUID p,int slot,ItemStack item,long now){var o=offer(p);if(o.items.containsKey(slot))o.items.remove(slot);else{require(slot>=0&&slot<36&&!TradeInventory.empty(item),"Оберіть предмет у головному інвентарі.");require(o.items.size()<MAX_STACKS,"Ліміт: 16 стосів.");o.items.put(slot,item.clone());}changed(now);}
}
