package com.quintonc.vs_sails.protection;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** One registry in the world's overworld data, shared by dimensions, never copied with a ship attachment. */
public final class ShipRegistry extends SavedData {
    public final Map<Long,ShipAccess.Vessel> ships=new HashMap<>();
    public final Map<UUID,ShipAccess.Grants> fleets=new HashMap<>();
    public static ShipRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ShipRegistry::load,ShipRegistry::new,"vs_sails_ownership");
    }
    private static UUID uuid(CompoundTag tag,String key) { return tag.hasUUID(key)?tag.getUUID(key):null; }
    private static void uuid(CompoundTag tag,String key,UUID value) { if(value!=null)tag.putUUID(key,value); }
    private static void readGrants(CompoundTag tag,ShipAccess.Grants grants) {
        for(Tag raw:tag.getList("towns",Tag.TAG_STRING))grants.towns.add(UUID.fromString(raw.getAsString()));
        for(Tag raw:tag.getList("players",Tag.TAG_STRING))grants.players.add(UUID.fromString(raw.getAsString()));
    }
    private static CompoundTag writeGrants(ShipAccess.Grants grants) {
        CompoundTag tag=new CompoundTag();
        for(var e:Map.of("towns",grants.towns,"players",grants.players).entrySet()) {
            ListTag list=new ListTag();e.getValue().stream().map(UUID::toString).sorted().forEach(s->list.add(StringTag.valueOf(s)));tag.put(e.getKey(),list);
        }
        return tag;
    }
    public static ShipRegistry load(CompoundTag tag) {
        ShipRegistry result=new ShipRegistry();
        for(Tag raw:tag.getList("ships",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)raw;
            var vessel=new ShipAccess.Vessel(uuid(row,"town"),uuid(row,"founder"));vessel.offeredTo=uuid(row,"offeredTo");
            readGrants(row.getCompound("grants"),vessel.grants);
            if(result.ships.put(row.getLong("id"),vessel)!=null)throw new IllegalStateException("Duplicate ship ownership");
        }
        for(Tag raw:tag.getList("fleets",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)raw;var grants=new ShipAccess.Grants();readGrants(row,grants);
            UUID town=uuid(row,"owner");if(town==null || result.fleets.put(town,grants)!=null)throw new IllegalStateException("Invalid fleet ownership");
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag shipsTag=new ListTag(),fleetTag=new ListTag();
        ships.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            var vessel=e.getValue();CompoundTag row=new CompoundTag();row.putLong("id",e.getKey());
            uuid(row,"town",vessel.town);uuid(row,"founder",vessel.founder);uuid(row,"offeredTo",vessel.offeredTo);
            row.put("grants",writeGrants(vessel.grants));shipsTag.add(row);
        });
        fleets.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{var row=writeGrants(e.getValue());row.putUUID("owner",e.getKey());fleetTag.add(row);});
        tag.put("ships",shipsTag);tag.put("fleets",fleetTag);return tag;
    }
}
