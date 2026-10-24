package ua.empirecraft.worldrules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class RenewalData extends SavedData {
    private final Set<Long> converted = new HashSet<>();
    private final Map<Long, Long> plants = new HashMap<>();
    public boolean spent(long chunk) { return converted.contains(chunk); }
    public void spend(long chunk) { converted.add(chunk); m_77762_(); }
    public boolean plantReady(long chunk, long now, int cooldown) {
        Long last = plants.get(chunk);
        return last == null || now >= last && now-last >= cooldown;
    }
    public void planted(long chunk, long time) { plants.put(chunk, time); m_77762_(); }
    public static RenewalData load(CompoundTag tag) {
        var data = new RenewalData();
        for (long key : tag.m_128467_("SaltChunks")) data.converted.add(key);
        ListTag list = tag.m_128437_("PlantTimes", 10);
        for (int i=0;i<list.size();i++) {
            CompoundTag entry = list.m_128728_(i);
            data.plants.put(entry.m_128454_("Chunk"), entry.m_128454_("Time"));
        }
        return data;
    }
    @Override public CompoundTag m_7176_(CompoundTag tag) {
        tag.m_128388_("SaltChunks", converted.stream().mapToLong(Long::longValue).sorted().toArray());
        var list = new ListTag();
        plants.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            var entry = new CompoundTag(); entry.m_128356_("Chunk", e.getKey()); entry.m_128356_("Time", e.getValue()); list.add(entry);
        });
        tag.m_128365_("PlantTimes", list); return tag;
    }
}
