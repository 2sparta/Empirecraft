package ua.empirecraft.engagement;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World game time survives restarts; logout, town changes and lowering a flag never clear combat. */
public final class CombatLedger extends SavedData {
    final Map<String,Long> until=new HashMap<>();
    final Map<Long,Long> flagPositions=new HashMap<>();
    public static CombatLedger get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(CombatLedger::load,CombatLedger::new,"empirecraft_engagement");}
    public boolean active(String key,long now){return key!=null&&until.getOrDefault(key,0L)>now;}
    public void tag(String key,long now,long duration){if(key==null||key.isBlank())return;long end=Math.addExact(now,Math.max(1,Math.min(72000,duration)));if(end>until.getOrDefault(key,0L)){until.put(key,end);setDirty();}}
    public void expire(long now){if(until.entrySet().removeIf(e->e.getValue()<=now))setDirty();}
    public static CombatLedger load(CompoundTag tag){
        var out=new CombatLedger();for(Tag item:tag.getList("Combat",Tag.TAG_COMPOUND)){var row=(CompoundTag)item;String key=row.getString("Key");if(key.length()<=96)out.until.put(key,row.getLong("Until"));}
        for(Tag item:tag.getList("Flags",Tag.TAG_COMPOUND)){var row=(CompoundTag)item;out.flagPositions.put(row.getLong("Ship"),row.getLong("Pos"));}return out;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var combat=new ListTag();until.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{var row=new CompoundTag();row.putString("Key",e.getKey());row.putLong("Until",e.getValue());combat.add(row);});tag.put("Combat",combat);
        var flags=new ListTag();flagPositions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{var row=new CompoundTag();row.putLong("Ship",e.getKey());row.putLong("Pos",e.getValue());flags.add(row);});tag.put("Flags",flags);return tag;
    }
}
