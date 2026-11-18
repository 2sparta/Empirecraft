package ua.empirecraft.feudal;

import java.util.*;
import static ua.empirecraft.feudal.Model.*;

/** Public political facts only. The map bridge freezes this DTO with the observed territory. */
final class MapProfiles {
    static Map<String,Object> build(FeudalPlugin plugin, FeudalService service, PoliticsService politics,
                                   TownyBridge towny, EngagementController engagement, Estate estate) throws Exception {
        var out = new LinkedHashMap<String,Object>();
        // The estate can be visible in Towny before the next political synchronization.
        var row = politics.db.one("SELECT * FROM political_states WHERE estate=?", estate.id());
        var state = row == null ? null : PoliticalModel.State.from(row);
        var government = state == null ? null : state.government();
        out.put("id", estate.id()); out.put("name", estate.name().replace('_',' '));
        out.put("rank", plugin.politicalLabel(estate));
        if (state != null) {
            out.put("government", government.name());
            out.put("territory", state.territory().name());
        }
        String parent = service.parent(estate.id());
        out.put("overlord", parent == null ? "Незалежна держава" : service.estate(parent).name().replace('_',' '));
        var leaders = new ArrayList<Map<String,Object>>();
        leaders.add(Map.of("uuid", estate.lord(), "name", towny.playerName(estate.lord()), "role", plugin.politicalTitle(estate)));
        var nativeNation = towny.kingdoms.nativeNation(estate.id());
        var capital = nativeNation == null ? null : nativeNation.getCapital();
        out.put("country", nativeNation == null ? estate.name() : nativeNation.getName().replace('_',' '));
        out.put("capital", capital == null ? "—" : capital.getName().replace('_',' '));
        if (nativeNation != null) {
            out.put("towns", nativeNation.getTowns().size());
            out.put("residents", nativeNation.getTowns().stream().mapToInt(t -> t.getResidents().size()).sum());
            boolean collective = government == PoliticalModel.Government.REPUBLIC
                    || government == PoliticalModel.Government.TRIBAL_COUNCIL;
            boolean sovereign = Objects.equals(towny.kingdoms.root(estate.id()),estate.id());
            var seen = new HashSet<String>(); seen.add(estate.lord());
            if(sovereign)nativeNation.getTowns().stream().flatMap(t -> t.getResidents().stream())
                    .sorted(Comparator.comparing(r -> r.getName())).forEach(r -> {
                var ranks = r.getNationRanks();
                boolean coRuler = ranks.stream().anyMatch(rank -> Set.of("consul","co-ruler","ruler","leader").contains(rank));
                if ((coRuler || collective && ranks.contains("assistant")) && leaders.size() < 16 && seen.add(r.getUUID().toString()))
                    leaders.add(Map.of("uuid", r.getUUID().toString(), "name", r.getName(), "role",
                            coRuler ? "Співправитель" : government == PoliticalModel.Government.REPUBLIC ? "Член уряду" : "Член ради"));
            });
            try { out.put("color", Integer.parseInt(nativeNation.getMapColorHexCode().replace("#",""),16)); }
            catch (RuntimeException invalid) { out.put("color", 0x94764e); }
        }
        out.put("leaders", List.copyOf(leaders));
        if (capital != null && engagement != null) {
            var design = engagement.design(TownyBridge.townId(capital));
            if (!design.isEmpty()) out.put("flag", Map.of("kind", design.get("kind"), "payload", design.get("payload"), "hash", design.get("hash")));
        }
        return Map.copyOf(out);
    }
}
