package ua.empirecraft.worldrules;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class RenewalData extends SavedData {
    private final Map<Long, Long> plants = new HashMap<>();
    private final Map<Long, Long> trees = new HashMap<>();
    public boolean plantReady(long chunk, long now, int cooldown) { return ready(plants, chunk, now, cooldown); }
    public boolean treeReady(long chunk, long now, int cooldown) { return ready(trees, chunk, now, cooldown); }
    private static boolean ready(Map<Long, Long> times, long chunk, long now, int cooldown) {
        Long last = times.get(chunk);
        return last == null || now >= last && now-last >= cooldown;
    }
    public void planted(long chunk, long time) { plants.put(chunk, time); m_77762_(); }
    public void treePlanted(long chunk, long time) { trees.put(chunk, time); m_77762_(); }
    public static RenewalData load(CompoundTag tag) {
        var data = new RenewalData();
        // Ignore legacy SaltChunks; existing PlantTimes stays compatible with 1.0.0.
        readTimes(tag, "PlantTimes", data.plants);
        readTimes(tag, "TreeTimes", data.trees);
        return data;
    }
    private static void readTimes(CompoundTag tag, String name, Map<Long, Long> times) {
        ListTag list = tag.m_128437_(name, 10);
        for (int i=0;i<list.size();i++) {
            CompoundTag entry = list.m_128728_(i);
            times.put(entry.m_128454_("Chunk"), entry.m_128454_("Time"));
        }
    }
    @Override public CompoundTag m_7176_(CompoundTag tag) {
        tag.m_128473_("SaltChunks");
        writeTimes(tag, "PlantTimes", plants);
        writeTimes(tag, "TreeTimes", trees);
        return tag;
    }
    private static void writeTimes(CompoundTag tag, String name, Map<Long, Long> times) {
        var list = new ListTag();
        times.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            var entry = new CompoundTag(); entry.m_128356_("Chunk", e.getKey()); entry.m_128356_("Time", e.getValue()); list.add(entry);
        });
        tag.m_128365_(name, list);
    }
}
