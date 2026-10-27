package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.event.nation.NationKingChangeEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent;
import org.bukkit.event.*;

/** Native Towny commands must not bypass a republican election. */
public final class PoliticalNativeGuard implements Listener {
    private final FeudalPlugin plugin;private final PoliticsService p;private final TownyBridge bridge;
    public PoliticalNativeGuard(FeudalPlugin plugin,PoliticsService p,TownyBridge bridge){this.plugin=plugin;this.p=p;this.bridge=bridge;}
    @EventHandler(priority=EventPriority.MONITOR)
    public void landLost(com.palmergames.bukkit.towny.event.town.TownUnclaimEvent event){
        try{p.landLost(TownyBridge.townId(event.getTown()));}catch(Exception e){plugin.failure("Скидання перевірки замку після втрати землі",e);}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void king(NationKingChangeEvent event){
        if(bridge.famineAuthority(TownyBridge.nationId(event.getNation())))return;
        try{if(!p.authorityChangeAllowed(TownyBridge.nationId(event.getNation()),event.getNewKing().getUUID().toString())){
            event.setCancelled(true);event.setCancelMessage("Республіканська влада передається через /t state election.");
        }}catch(Exception e){event.setCancelled(true);event.setCancelMessage("Не вдалося перевірити виборні повноваження.");plugin.failure("Перевірка виборів Towny",e);}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void mayor(TownMayorChangeEvent event){
        if(bridge.famineAuthority(TownyBridge.townId(event.getTown())))return;
        try{String next=event.getNewMayor().getUUID().toString();boolean allowed=p.authorityChangeAllowed(TownyBridge.townId(event.getTown()),next);
            if(event.getTown().isCapital() && event.getTown().getNationOrNull()!=null)allowed &= p.authorityChangeAllowed(TownyBridge.nationId(event.getTown().getNationOrNull()),next);
            if(!allowed){event.setCancelled(true);event.setCancelMessage("Республіканський голова обирається через /t state election.");}
        }catch(Exception e){event.setCancelled(true);event.setCancelMessage("Не вдалося перевірити виборні повноваження.");plugin.failure("Перевірка виборів міста",e);}
    }
}
