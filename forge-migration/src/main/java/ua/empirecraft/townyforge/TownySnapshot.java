package ua.empirecraft.townyforge;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class TownySnapshot {
    static final Gson JSON = new Gson();
    enum Action {
        BUILD("Build"), DESTROY("Destroy"), SWITCH("Switch"), ITEM_USE("ItemUse");

        final String suffix;

        Action(String suffix) {
            this.suffix = suffix;
        }
    }

    record Claim(String world, int x, int z, Map<String, String> data) {
        String town() {
            return data.get("town");
        }

        String type() {
            return data.getOrDefault("type", "default").toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record Position(String dimension, int x, int z) {}
    private record Resident(String name, String town) {}
    record Invitation(String kind, String owner, String recipient) {}

    record Source(int formatVersion, Map<String, String> dimensions,
                  Map<String, Map<String, String>> worlds, Map<String, Map<String, String>> towns,
                  Map<String, Map<String, String>> nations, Map<String, Map<String, String>> residents,
                  List<Claim> claims, List<String> itemUse, List<String> switches, Map<String, String> settings,
                  Map<String, List<String>> permissions, Map<String, String> balances, List<Invitation> invitations) {}

    private final Source source;
    private final Map<String, Map<String, String>> worlds = new HashMap<>();
    private final Map<Position, Claim> claims = new HashMap<>();
    private final Map<UUID, Resident> residents = new HashMap<>();
    private TownyView view;
    private String integrationJson;

    static TownySnapshot load(Path world) throws IOException {
        return read(world.resolve("empirecraft/towny-import.json"));
    }

    static TownySnapshot read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return new TownySnapshot(JSON.fromJson(reader, Source.class));
        }
    }

    TownySnapshot(Source source) throws IOException {
        if (source == null || source.formatVersion != 1) throw new IOException("Invalid Towny import version");
        this.source = source;
        Objects.requireNonNull(source.switches, "Towny switch rules");
        Objects.requireNonNull(source.itemUse, "Towny item-use rules");
        for (var entry : source.dimensions.entrySet()) {
            Map<String, String> world = source.worlds.get(entry.getKey());
            if (world == null || worlds.putIfAbsent(entry.getValue(), world) != null) {
                throw new IOException("Invalid or duplicate Towny dimension mapping");
            }
        }
        for (Claim claim : source.claims) {
            String dimension = source.dimensions.get(claim.world);
            if (dimension == null || !source.towns.containsKey(claim.town())
                    || claims.putIfAbsent(new Position(dimension, claim.x, claim.z), claim) != null) {
                throw new IOException("Unresolved or duplicate Towny claim");
            }
        }
        for (var entry : source.residents.entrySet()) {
            Map<String, String> record = entry.getValue();
            UUID uuid = UUID.fromString(record.get("uuid"));
            Resident resident = new Resident(entry.getKey(), record.getOrDefault("town", ""));
            if (residents.putIfAbsent(uuid, resident) != null) throw new IOException("Duplicate Towny resident UUID");
            if (!resident.town.isEmpty() && !source.towns.containsKey(resident.town)) {
                throw new IOException("A resident belongs to a missing Towny town");
            }
        }
        for (var entry : source.towns.entrySet()) {
            var town = entry.getValue();
            var mayor = source.residents.get(town.get("mayor"));
            String nation = town.getOrDefault("nation", "");
            if (mayor == null || !entry.getKey().equals(mayor.get("town"))
                    || !nation.isEmpty() && !source.nations.containsKey(nation)) {
                throw new IOException("Invalid Towny town membership");
            }
        }
        for (var entry : source.nations.entrySet()) {
            var capital = source.towns.get(entry.getValue().get("capital"));
            if (capital == null || !entry.getKey().equals(capital.get("nation"))) {
                throw new IOException("Invalid Towny national capital");
            }
        }
    }

    Source editableCopy() {
        return JSON.fromJson(JSON.toJsonTree(source), Source.class);
    }

    boolean editable() {
        return source.settings != null && source.permissions != null && source.balances != null
                && source.invitations != null;
    }

    Map<String, String> settings() {
        return source.settings == null ? Map.of() : java.util.Collections.unmodifiableMap(source.settings);
    }

    boolean settingEnabled(String key) {
        return flag(settings(), key);
    }

    Map<String, String> dimensions() {
        return java.util.Collections.unmodifiableMap(source.dimensions);
    }

    Map<String, String> resident(String name) {
        return source.residents.get(name);
    }

    TownyView view() {
        if (view == null) view = TownyView.of(source);
        return view;
    }

    String integrationJson() {
        if (integrationJson == null) integrationJson = view().exportJson();
        return integrationJson;
    }

    Claim claim(String dimension, int x, int z) {
        return claims.get(new Position(dimension, x >> 4, z >> 4));
    }

    boolean isClaimed(String dimension, int x, int z) {
        Map<String, String> world = worlds.get(dimension);
        return world != null && flag(world, "usingTowny") && claim(dimension, x, z) != null;
    }

    String townOf(UUID player) {
        Resident resident = residents.get(player);
        return resident == null ? "" : resident.town;
    }

    String residentOf(UUID player) {
        Resident resident = residents.get(player);
        return resident == null ? "" : resident.name;
    }

    List<String> towns() {
        return source.towns.keySet().stream().sorted().toList();
    }

    List<String> nations() {
        return source.nations.keySet().stream().sorted().toList();
    }

    Map<String, String> town(String query) {
        return find(source.towns, query);
    }

    Map<String, String> nation(String query) {
        return find(source.nations, query);
    }

    List<String> townResidents(String town) {
        return members(source.residents, "town", town);
    }

    List<String> nationTowns(String nation) {
        return members(source.towns, "nation", nation);
    }

    private static List<String> members(Map<String, Map<String, String>> records, String key, String owner) {
        return records.entrySet().stream().filter(entry -> owner.equalsIgnoreCase(entry.getValue().get(key)))
                .map(Map.Entry::getKey).sorted().toList();
    }

    private static Map<String, String> find(Map<String, Map<String, String>> records, String query) {
        return records.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(query))
                .findFirst().map(Map.Entry::getValue).orElse(null);
    }

    boolean restrictedItem(String material) {
        return matchesAny(source.itemUse, material);
    }

    boolean restrictedSwitch(String material) {
        return matchesAny(source.switches, material);
    }

    boolean restricted(String dimension, int x, int z, String material, Action action) {
        Claim plot = claim(dimension, x, z);
        boolean switchAction = action == Action.SWITCH;
        if (plot != null) {
            if (switchAction && plot.type().equals("inn") && material.endsWith("_BED")) return false;
            String configured = settings().get("plots." + plot.type() + (switchAction ? ".switchIds" : ".itemUseIds"));
            if (configured != null && !configured.isBlank()) return matchesAny(List.of(configured.split(",")), material);
        }
        return switchAction ? restrictedSwitch(material) : restrictedItem(material);
    }

    private static boolean matchesAny(List<String> configured, String material) {
        if (material.isEmpty()) return false;
        for (String element : configured) {
            String pattern = element.strip();
            if (pattern.equalsIgnoreCase(material)) return true;
            boolean groupMatch = switch (pattern) {
                case "BOATS" -> material.endsWith("_BOAT") || material.endsWith("_RAFT");
                case "MINECARTS" -> material.equals("MINECART") || material.endsWith("_MINECART");
                case "SHULKER_BOXES" -> material.equals("SHULKER_BOX") || material.endsWith("_SHULKER_BOX");
                case "BUTTONS" -> material.endsWith("_BUTTON");
                case "WOOD_DOORS" -> material.endsWith("_DOOR") && !material.equals("IRON_DOOR");
                case "FENCE_GATES" -> material.endsWith("_FENCE_GATE");
                case "TRAPDOORS" -> material.endsWith("_TRAPDOOR");
                case "PRESSURE_PLATES" -> material.endsWith("_PRESSURE_PLATE");
                case "NON_WOODEN_PRESSURE_PLATES" -> material.equals("STONE_PRESSURE_PLATE")
                        || material.equals("POLISHED_BLACKSTONE_PRESSURE_PLATE")
                        || material.endsWith("_WEIGHTED_PRESSURE_PLATE");
                default -> false;
            };
            if (groupMatch) return true;
        }
        return false;
    }

    boolean allows(String dimension, int x, int z, UUID player, Action action, String material) {
        var world = worlds.get(dimension);
        if (world == null || !flag(world, "usingTowny")) return true;
        Claim claim = claim(dimension, x, z);
        if (claim == null) {
            return flag(world, "unclaimedZone" + action.suffix)
                    || listed(world.get("unclaimedZoneIgnoreIds"), material);
        }

        var town = source.towns.get(claim.town());
        if (action == Action.BUILD && flag(town, "bankrupt")) return false;
        var resident = residents.get(player);
        if (resident != null && resident.name.equals(town.get("mayor"))) return true;

        var plot = claim.data;
        if (resident != null && (resident.name.equals(plot.get("resident"))
                || listed(town.get("trustedResidents"), resident.name)
                || listed(plot.get("trustedResidents"), resident.name))) {
            return true;
        }
        if ((claim.type().equals("farm") || claim.type().equals("wilds"))
                && (action == Action.BUILD || action == Action.DESTROY)) {
            String allowed = settings().getOrDefault("plots." + claim.type() + ".allowedBlocks", "");
            if (!matchesAny(List.of(allowed.split(",")), material)) return false;
        }
        if (!"{}".equals(plot.getOrDefault("customPermissionData", "{}"))) return false;

        String permissions = plot.getOrDefault("permissions", plot.getOrDefault("protectionStatus",
                town.get("protectionStatus")));
        String relation = relation(town, claim.town(), resident);
        return listed(permissions, relation + action.suffix);
    }

    private String relation(Map<String, String> town, String townName, Resident resident) {
        if (resident == null || resident.town.isEmpty() || listed(town.get("outlaws"), resident.name)) {
            return "outsider";
        }
        if (resident.town.equals(townName)) return "resident";
        var otherTown = source.towns.get(resident.town);
        if (otherTown == null) return "outsider";
        String nation = town.get("nation");
        String otherNation = otherTown.get("nation");
        if (nation != null && !nation.isEmpty()) {
            if (nation.equals(otherNation)) return "nation";
            var nationData = source.nations.get(nation);
            if (nationData != null && listed(nationData.get("allies"), otherNation)) return "ally";
        }
        return "outsider";
    }

    boolean environmental(String dimension, int x, int z, String worldFlag, String forceFlag, String plotFlag) {
        var world = worlds.get(dimension);
        if (world == null || !flag(world, "usingTowny")) return true;
        if (flag(world, forceFlag)) return true;
        Claim claim = claim(dimension, x, z);
        if (claim == null) return flag(world, worldFlag);
        var town = source.towns.get(claim.town());
        if (plotFlag.equals("pvp")) {
            if (flag(town, "adminDisabledPvP")) return false;
            if (claim.type().equals("arena")) return true;
            if (flag(town, "adminEnabledPvP")) return true;
        }
        return listed(claim.data.getOrDefault("permissions", claim.data.getOrDefault("protectionStatus",
                town.get("protectionStatus"))), plotFlag);
    }

    boolean sameClaim(String dimension, int x, int z, int otherX, int otherZ) {
        Claim first = claim(dimension, x, z);
        Claim second = claim(dimension, otherX, otherZ);
        return Objects.equals(first, second) || first != null && second != null && first.town().equals(second.town())
                && first.type().equals(second.type())
                && !first.data.containsKey("resident") && !second.data.containsKey("resident")
                && !flag(first.data, "changed") && !flag(second.data, "changed");
    }

    static boolean flag(Map<String, String> map, String key) {
        return Boolean.parseBoolean(map.get(key));
    }

    static boolean listed(String csv, String value) {
        if (csv == null || value == null || value.isEmpty()) return false;
        for (String entry : csv.split(",")) {
            if (entry.strip().equalsIgnoreCase(value)) return true;
        }
        return false;
    }
}
