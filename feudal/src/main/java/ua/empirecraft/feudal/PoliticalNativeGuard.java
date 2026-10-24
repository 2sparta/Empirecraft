package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.event.nation.NationKingChangeEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent;
import org.bukkit.event.*;

/** Native Towny commands must not bypass a republican election. */
public final class PoliticalNativeGuard implements Listener {
    private final FeudalPlugin plugin;private final PoliticsService p;
    public PoliticalNativeGuard(FeudalPlugin plugin,PoliticsService p){this.plugin=plugin;this.p=p;}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void king(NationKingChangeEvent event){
        try{if(!p.authorityChangeAllowed(TownyBridge.nationId(event.getNation()),event.getNewKing().getUUID().toString())){
            event.setCancelled(true);event.setCancelMessage("Республіканська влада передається через /state election.");
        }}catch(Exception e){event.setCancelled(true);event.setCancelMessage("Не вдалося перевірити виборні повноваження.");plugin.failure("Перевірка виборів Towny",e);}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void mayor(TownMayorChangeEvent event){
        try{String next=event.getNewMayor().getUUID().toString();boolean allowed=p.authorityChangeAllowed(TownyBridge.townId(event.getTown()),next);
            if(event.getTown().isCapital() && event.getTown().getNationOrNull()!=null)allowed &= p.authorityChangeAllowed(TownyBridge.nationId(event.getTown().getNationOrNull()),next);
            if(!allowed){event.setCancelled(true);event.setCancelMessage("Республіканський голова обирається через /state election.");}
        }catch(Exception e){event.setCancelled(true);event.setCancelMessage("Не вдалося перевірити виборні повноваження.");plugin.failure("Перевірка виборів міста",e);}
    }
}
