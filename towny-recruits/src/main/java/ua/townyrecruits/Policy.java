package ua.townyrecruits;

import java.util.*;

/** Pure policy. Towny UUIDs, never display names, are the identity. */
public final class Policy {
    private Policy() {}
    public record Nation(UUID id, String name, UUID king, String kingName, int towns,
                         Map<UUID,String> members, Set<UUID> enemies, Set<UUID> allies) {}
    public record Snapshot(Map<UUID,Nation> nations, Map<UUID,UUID> residents) {
        public static Snapshot empty() { return new Snapshot(Map.of(), Map.of()); }
    }
    public static String team(UUID id) { return "tr_" + id.toString().replace("-", ""); }
    public static int limit(int towns, Integer override) {
        return override != null ? Math.max(0, override) : Math.toIntExact(Math.min(Integer.MAX_VALUE,20L + 5L*Math.max(0,towns-1)));
    }
    // Enemy declaration by either party wins. An accepted alliance is also symmetric here.
    public static int relation(Snapshot s, UUID a, UUID b) {
        if(a==null || b==null) return 0;
        if(a.equals(b)) return 1;
        Nation x=s.nations.get(a), y=s.nations.get(b);
        if(x==null || y==null) return 0;
        if(x.enemies.contains(b) || y.enemies.contains(a)) return -1;
        return x.allies.contains(b) || y.allies.contains(a) ? 1 : 0;
    }
}
