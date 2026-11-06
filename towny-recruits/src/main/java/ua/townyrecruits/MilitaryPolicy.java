package ua.townyrecruits;

import java.util.*;
import ua.townyrecruits.MilitaryData.*;

public final class MilitaryPolicy {
    private MilitaryPolicy() {}
    public static boolean manages(UUID nation, UUID actor, UUID leader, boolean officer, Policy.Snapshot snapshot) {
        if(nation==null)return false;
        var n = snapshot.nations().get(nation);
        return n != null && nation.equals(snapshot.residents().get(actor)) && (officer || actor.equals(leader) || actor.equals(n.king()));
    }
    public static boolean protectsAssignment(MilitaryData data,UUID owner,UUID assigned,UUID requested,boolean destinationLocked){
        boolean same=Objects.equals(assigned,requested);
        if(data.companies.containsKey(assigned)&&!same)return true;
        var destination=data.companies.get(requested);if(destination==null)return false;
        return !destination.commander().equals(owner)||!same&&(destinationLocked||data.loans.containsKey(requested));
    }
    /** Update loaded and unloaded records atomically without spending a hire slot or retiring a soldier. */
    public static void transfer(ArmyData army, MilitaryData data, UUID group, Regiment regiment, UUID commander, Policy.Snapshot snapshot) {
        transfer(army,data,group,regiment,commander,snapshot,Policy.relation(snapshot,regiment.nation(),snapshot.residents().get(commander))==1);
    }
    public static void transfer(ArmyData army, MilitaryData data, UUID group, Regiment regiment, UUID commander, Policy.Snapshot snapshot,boolean authorizedCity) {
        if (!authorizedCity) throw new IllegalArgumentException("Командир має бути жителем міста-власника або союзного міста.");
        var ids = data.members.entrySet().stream().filter(e -> group.equals(e.getValue())).map(Map.Entry::getKey).toList();
        for (UUID id : ids) { var u = army.units.get(id); if (u != null && !u.retired() && !regiment.nation().equals(u.nation())) throw new IllegalArgumentException("У загоні є солдат іншої нації."); }
        data.companies.put(group, new Company(regiment.id(), commander));
        for (UUID id : ids) { var u = army.units.get(id); if (u != null && !u.retired()) army.units.put(id, new ArmyData.Unit(commander, u.nation(), false)); }
        army.recount(); army.m_77762_(); data.m_77762_();
    }
}
