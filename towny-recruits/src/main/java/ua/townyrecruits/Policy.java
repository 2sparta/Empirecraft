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
    private static final Map<UUID,String> teamIds=new HashMap<>();
    public static void bindTeam(UUID nation,String faction){teamIds.put(nation,faction);}
    public static void clearBindings(){teamIds.clear();}
    public static String team(UUID id) { return teamIds.getOrDefault(id,"tr_" + id.toString().replace("-", "")); }
    public static boolean managedTeam(String id) { return id!=null && (id.startsWith("tr_") || id.startsWith("tn_") || teamIds.containsValue(id)); }
    public static Set<String> staleTeams(Set<String> desired,Collection<String> existing) {
        Set<String> result=new HashSet<>();for(String id:existing)if(managedTeam(id)&&!desired.contains(id))result.add(id);return result;
    }
    public static <T> void clearRelations(Set<String> removed,Map<String,Map<String,T>> relations,Map<UUID,String> embargoes) {
        removed.forEach(relations::remove);relations.values().forEach(row->row.keySet().removeAll(removed));
        embargoes.replaceAll((id,csv)->String.join(",",Arrays.stream(csv.split(",")).filter(x->!removed.contains(x)).toList()));
        embargoes.values().removeIf(String::isBlank);
    }
    public enum HireStatus { ALLOWED, NOT_READY, CENSUS_FAILED, ALREADY_OWNED, NO_NATION, LIMIT_REACHED }
    public static HireStatus hireStatus(boolean ready,boolean census,boolean owned,boolean hasNation,int count,int limit) {
        if(!ready)return HireStatus.NOT_READY;
        if(!census)return HireStatus.CENSUS_FAILED;
        if(owned)return HireStatus.ALREADY_OWNED;
        if(!hasNation)return HireStatus.NO_NATION;
        return count<limit?HireStatus.ALLOWED:HireStatus.LIMIT_REACHED;
    }
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
