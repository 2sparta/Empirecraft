package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.town.*;
import com.palmergames.bukkit.towny.event.nation.*;
import org.bukkit.event.*;
import static ua.empirecraft.feudal.LocalText.tr;

/** Locks actual Towny operations, including aliases and API-fired native events, rather than command text. */
final class RecruitTownyGuard implements Listener {
    final RecruitController c;
    RecruitTownyGuard(RecruitController controller){c=controller;}
    private boolean person(String person){if(c.world.moving.contains(person))return false;try{return c.service.citizenLocked(person);}catch(Exception error){return true;}}
    private boolean city(String town){try{return c.service.cityLocked(town);}catch(Exception error){return true;}}
    private void stop(CancellableTownyEvent event){event.setCancelled(true);event.setCancelMessage(tr("Громадянство й командир найманців захищені до завершення битви та повернення додому."));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void leave(TownLeaveEvent event){if(person(event.getResident().getUUID().toString()))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void kick(TownKickEvent event){if(person(event.getKickedResident().getUUID().toString()))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void join(TownPreAddResidentEvent event){if(person(event.getResident().getUUID().toString()))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void delete(PreDeleteTownEvent event){if(city(TownyBridge.townId(event.getTown())))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void merge(TownPreMergeEvent event){if(city(TownyBridge.townId(event.getRemainingTown()))||city(TownyBridge.townId(event.getSuccumbingTown())))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void rank(TownAddResidentRankEvent event){if(person(event.getResident().getUUID().toString()))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mayor(TownMayorChangeEvent event){if(person(event.getNewMayor().getUUID().toString()))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void king(NationKingChangeEvent event){if(person(event.getNewKing().getUUID().toString()))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void leaveNation(NationPreTownLeaveEvent event){if(city(TownyBridge.townId(event.getTown())))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void kickNation(NationPreTownKickEvent event){if(city(TownyBridge.townId(event.getTown())))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void deleteNation(PreDeleteNationEvent event){if(event.getNation().getTowns().stream().anyMatch(t->city(TownyBridge.townId(t))))stop(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mergeNation(NationPreMergeEvent event){if(event.getNation().getTowns().stream().anyMatch(t->city(TownyBridge.townId(t)))||event.getRemainingNation().getTowns().stream().anyMatch(t->city(TownyBridge.townId(t))))stop(event);}
}
