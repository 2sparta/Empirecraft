package ua.empirecraft.townyforge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ua.empirecraft.townyforge.TownyGovernance.Here;
import ua.empirecraft.townyforge.TownySnapshot.Claim;
import ua.empirecraft.townyforge.TownySnapshot.Source;
import ua.empirecraft.townyforge.TownyTravel.Kind;

public final class TownyTravelTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final String WORLD = "world_europe";
    private static final Here ORIGIN = new Here(DIMENSION, -11, 64, -1, 0, 0);

    public static void main(String[] args) {
        var settings = new HashMap<String, String>();
        var town = new HashMap<>(Map.of("spawn", WORLD + ",1.5,65,2.5,3,90", "nation", "Nation",
                "outpostspawns", WORLD + ",19,66,1,0,0"));
        var nation = new HashMap<>(Map.of("capital", "Town"));
        var claims = List.of(new Claim(WORLD, 0, 0, Map.of("town", "Town")),
                new Claim(WORLD, 1, 0, Map.of("town", "Town", "outpost", "true")));
        var data = new Source(1, Map.of(WORLD, DIMENSION), Map.of(), Map.of("Town", town),
                Map.of("Nation", nation), Map.of("Test", Map.of("town", "Town")),
                claims, List.of(), List.of(), settings, Map.of(), Map.of(), List.of());

        reject(data, Kind.TOWN);
        settings.put("spawning.town_spawn.allow_town_spawn", "true");
        settings.put("spawning.nation_spawn.allow_nation_spawn", "true");
        settings.put("spawning.town_spawn.spawning_cooldowns.town_spawn_cooldown_time", "45");
        var home = TownyTravel.resolve(data, "Test", ORIGIN, Kind.TOWN, 0);
        check(home.point().equals(new Here(DIMENSION, 1.5, 65, 2.5, 90, 3)) && home.cooldownSeconds() == 45);
        check(TownyTravel.resolve(data, "Test", ORIGIN, Kind.NATION, 0).point().equals(home.point()));
        check(TownyTravel.resolve(data, "Test", ORIGIN, Kind.OUTPOST, 1).point().x() == 19);
        settings.put("spawning.town_spawn.prevent_town_spawn_in", "enemy,unclaimed");
        reject(data, Kind.TOWN);
        settings.remove("spawning.town_spawn.prevent_town_spawn_in");
        settings.put("economy.spawn_travel.price_town_spawn_travel", "1");
        reject(data, Kind.TOWN);
        settings.remove("economy.spawn_travel.price_town_spawn_travel");
        town.put("spawn", WORLD + ",-100,65,2,0,0");
        reject(data, Kind.TOWN);
        reject(data, Kind.NATION);
        System.out.println("PASS Towny spawn policies, import, outpost and destination ownership");
    }

    private static void reject(Source data, Kind kind) {
        try {
            TownyTravel.resolve(data, "Test", ORIGIN, kind, 0);
            throw new AssertionError("Expected a blocked teleport for " + kind);
        } catch (TownyProblem expected) {
            // Expected policy rejection.
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Invalid Towny destination or cooldown");
    }
}
