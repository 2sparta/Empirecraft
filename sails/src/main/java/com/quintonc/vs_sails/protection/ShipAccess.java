package com.quintonc.vs_sails.protection;

import java.util.*;

/** Independent of the game and Towny; UUIDs survive town/player renames. */
public final class ShipAccess {
    public static final class Grants {
        public final Set<UUID> towns = new HashSet<>(), players = new HashSet<>();
    }
    public static final class Vessel {
        public UUID town, founder, offeredTo;
        public UUID captain;
        public String name="",marketLock="";
        public long revision;
        public boolean managed,marketWasStatic;
        public final Grants grants = new Grants();
        public Vessel(UUID town, UUID founder) { this.town=town; this.founder=founder; }
        public void accept(UUID acceptingTown) {
            if (offeredTo==null || !offeredTo.equals(acceptingTown)) throw new IllegalStateException("No transfer to this town");
            town=offeredTo; offeredTo=null; grants.towns.clear(); grants.players.clear();
        }
    }
    public static boolean allows(Vessel vessel, Grants fleet, UUID actor, UUID actorTown, boolean controlPermission) {
        if (vessel==null || vessel.town==null || actor==null || !controlPermission) return false;
        return vessel.town.equals(actorTown) || vessel.grants.players.contains(actor)
                || actorTown!=null && vessel.grants.towns.contains(actorTown)
                || fleet!=null && (fleet.players.contains(actor) || actorTown!=null && fleet.towns.contains(actorTown));
    }
}
