package ua.empirecraft.townyforge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ua.empirecraft.townyforge.TownySnapshot.Source;

/** Immutable public geography and membership. Contains geography and membership only. */
public record TownyView(Map<UUID, Town> towns, Map<UUID, Nation> nations, Map<UUID, Resident> residents,
                        Map<Chunk, Territory> territory) {
    public record Town(UUID id, String name, UUID nation, UUID mayor, String color, Spawn spawn) {}
    public record Nation(UUID id, String name, UUID capital, String color, Set<UUID> allies, Set<UUID> enemies) {}
    public record Resident(UUID id, String name, UUID town, Set<String> nationRanks) {}
    public record Chunk(String dimension, int x, int z) {}
    public record Territory(UUID town, String type) {}
    public record Spawn(String dimension, double x, double y, double z, float yaw, float pitch) {}

    static TownyView of(Source data) {
        var towns = new HashMap<UUID, Town>();
        var nations = new HashMap<UUID, Nation>();
        var residents = new HashMap<UUID, Resident>();
        var territory = new HashMap<Chunk, Territory>();
        data.towns().forEach((name, row) -> {
            UUID uuid = id(row);
            Spawn spawn = null;
            if (row.containsKey("spawn")) try {
                var point = TownyTravel.parse(data.dimensions(), row.get("spawn"));
                spawn = new Spawn(point.dimension(), point.x(), point.y(), point.z(), point.yaw(), point.pitch());
            } catch (TownyProblem invalid) {
                // A town without a usable spawn can still own territory.
            }
            towns.put(uuid, new Town(uuid, name, id(data.nations().get(row.getOrDefault("nation", ""))), id(data.residents().get(row.get("mayor"))),
                    row.getOrDefault("mapColorHexCode", ""), spawn));
        });
        data.nations().forEach((name, row) -> {
            UUID uuid = id(row);
            nations.put(uuid, new Nation(uuid, name, id(data.towns().get(row.get("capital"))),
                    row.getOrDefault("mapColorHexCode", ""), organizations(data.nations(), row.get("allies")),
                    organizations(data.nations(), row.get("enemies"))));
        });
        data.residents().forEach((name, row) -> {
            UUID uuid = id(row);
            residents.put(uuid, new Resident(uuid, name, id(data.towns().get(row.getOrDefault("town", ""))),
                    fields(row.get("nation-ranks")).stream().collect(Collectors.toUnmodifiableSet())));
        });
        for (var claim : data.claims()) {
            if (TownySnapshot.flag(data.worlds().get(claim.world()), "usingTowny")) {
                territory.put(new Chunk(data.dimensions().get(claim.world()), claim.x(), claim.z()),
                        new Territory(id(data.towns().get(claim.town())), claim.type()));
            }
        }
        return new TownyView(Map.copyOf(towns), Map.copyOf(nations), Map.copyOf(residents), Map.copyOf(territory));
    }

    public UUID townOf(UUID resident) {
        var person = resident == null ? null : residents.get(resident);
        return person == null ? null : person.town();
    }

    public Territory at(String dimension, int x, int z) {
        return territory.get(new Chunk(dimension, x >> 4, z >> 4));
    }

    record Entry(String dimension, int x, int z, UUID town, String type) {}

    String exportJson() {
        var claims = territory.entrySet().stream().map(e -> new Entry(e.getKey().dimension(), e.getKey().x(),
                e.getKey().z(), e.getValue().town(), e.getValue().type())).toList();
        return TownySnapshot.JSON.toJson(Map.of("towns", towns, "nations", nations, "residents", residents, "claims", claims));
    }

    private static Set<UUID> organizations(Map<String, Map<String, String>> records, String csv) {
        return fields(csv).stream().filter(records::containsKey).map(key -> id(records.get(key)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> fields(String csv) {
        return csv == null || csv.isBlank() ? List.of() : java.util.Arrays.stream(csv.split(",")).map(String::strip).toList();
    }

    private static UUID id(Map<String, String> row) {
        return row == null ? null : UUID.fromString(row.get("uuid"));
    }
}
