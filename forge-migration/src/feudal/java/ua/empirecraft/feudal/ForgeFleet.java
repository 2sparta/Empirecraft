package ua.empirecraft.feudal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ua.empirecraft.feudal.CityMarketService.Place;
import ua.empirecraft.feudal.FeudalService.Actor;
import ua.empirecraft.feudal.FleetService.Hull;
import ua.empirecraft.feudal.ShipRegistrationService.Anchor;
import ua.empirecraft.townyforge.TownyForge;
import ua.empirecraft.townyforge.TownyView;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.require;

final class ForgeFleet implements FleetService.World {
    private final ForgeFeudal feudal;
    private final MinecraftServer server;
    private final Method nativeApi;
    private final Method engagement;
    private final Method recruits;
    private final Map<Long, Projection> projected = new HashMap<>();
    private long projectionCursor = -1;
    final FleetService fleet;
    final ShipRegistrationService registrations;

    private record Projection(long revision, String lock) {}

    ForgeFleet(ForgeFeudal feudal, MinecraftServer server) throws SQLException {
        this.feudal = feudal;
        this.server = server;
        nativeApi = bridge("com.quintonc.vs_sails.protection.FleetNative");
        engagement = bridge("ua.empirecraft.engagement.EngagementNative");
        recruits = bridge("ua.townyrecruits.RegimentExchange");
        feudal.db.execute("CREATE TABLE IF NOT EXISTS naval_ensigns(nation VARCHAR(36) PRIMARY KEY,kind VARCHAR(16),"
                + "payload CLOB,hash VARCHAR(64),revision BIGINT,author VARCHAR(36))");
        fleet = new FleetService(feudal.markets, this);
        registrations = new ShipRegistrationService(feudal.markets, fleet, this::valid);
    }

    private static Method bridge(String type) {
        try {
            return Class.forName(type, false, ForgeFleet.class.getClassLoader()).getMethod("request", String.class, Map.class);
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (NoSuchMethodException incompatible) {
            throw new IllegalStateException("Несумісна версія " + type, incompatible);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> call(Method method, String action, Map<String, Object> data) {
        if (method == null) return Map.of();
        try {
            return (Map<String, Object>) method.invoke(null, action, data);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            Throwable cause = failure instanceof InvocationTargetException ? failure.getCause() : failure;
            if (cause instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalStateException("Корабельна система: " + cause.getMessage(), cause);
        }
    }

    void bootstrap() throws SQLException {
        var all = call(nativeApi, "list", Map.of()).get("ships");
        if (!(all instanceof List<?> ships)) return;
        for (Object item : ships) {
            @SuppressWarnings("unchecked") var record = (Map<String, Object>) item;
            if (!record.isEmpty()) importShip(record);
        }
    }

    void tick() throws SQLException {
        if (nativeApi == null) return;
        fleet.tick();
        var rows = feudal.db.query("SELECT id FROM naval_ships WHERE id>? ORDER BY id LIMIT 32", projectionCursor);
        for (var row : rows) {
            projectionCursor = number(row, "id");
            project(projectionCursor);
        }
        if (rows.size() < 32) projectionCursor = -1;
    }

    private void importShip(Map<String, Object> raw) {
        Hull hull = decode(raw);
        if (hull != null) fleet.importNative(hull, Objects.toString(raw.get("founder"), ""),
                Objects.toString(raw.get("captain"), ""), (List<?>) raw.getOrDefault("crew", List.of()));
    }

    void ensure(long ship) throws SQLException {
        if (feudal.db.one("SELECT id FROM naval_ships WHERE id=?", ship) != null) return;
        var row = call(nativeApi, "snapshot", Map.of("ship", ship));
        require(!row.isEmpty(), "Корабель не знайдено.");
        importShip(row);
    }

    void project(long ship) throws SQLException {
        var row = fleet.ship(ship);
        var lock = feudal.db.one("SELECT offer FROM naval_locks WHERE ship=?", ship);
        var current = new Projection(number(row, "revision"), lock == null ? "" : string(lock, "offer"));
        if (current.equals(projected.get(ship))) return;
        var crew = feudal.db.query("SELECT person FROM naval_crew WHERE ship=? ORDER BY person", ship).stream()
                .map(r -> string(r, "person")).toList();
        var result = call(nativeApi, "project", Map.of("ship", ship, "town", string(row, "town"),
                "name", string(row, "name"), "captain", string(row, "captain"), "crew", crew,
                "revision", current.revision(), "lock", current.lock()));
        if (Boolean.TRUE.equals(result.get("ok"))) projected.put(ship, current);
    }

    Map<String, Object> request(String action, Map<String, Object> data) throws SQLException {
        if (action.equals("policy")) {
            long ship = number(data, "ship");
            ensure(ship);
            var policy = fleet.policy(ship, string(data, "player"));
            return Map.of("allow", policy.allow(), "protect", policy.protect(), "locked", policy.locked(), "town", policy.owner());
        }
        if (action.equals("command-authority")) {
            return Map.of("allow", fleet.commanderAllowed(string(data, "town"), string(data, "player")));
        }
        if (action.equals("can-build")) return Map.of("ok", false);
        if (action.equals("ship-complete") || action.equals("ship-status")) {
            long ship = number(data, "ship");
            String permit = string(data, "permit");
            var row = action.equals("ship-status")
                    ? feudal.db.one("SELECT state FROM naval_applications WHERE permit=? AND ship=?", permit, ship)
                    : registrations.complete(permit, ship);
            return Map.of("ok", true, "assigned", row != null && "ASSIGNED".equals(string(row, "state")));
        }
        if (action.equals("ship-abort")) {
            registrations.abort(string(data, "player"), string(data, "permit"));
            return Map.of("ok", true);
        }
        String person = string(data, "player");
        ServerPlayer player = server.m_6846_().m_11259_(UUID.fromString(person));
        require(player != null, "Гравець вийшов із сервера.");
        Actor actor = new Actor(person, player.m_20310_(2));
        switch (action) {
            case "ship-tool-admin": return Map.of("admin", actor.admin());
            case "view": return call(nativeApi, "view", data);
            case "hide":
                fleet.hide(person, number(data, "ship"), true);
                return Map.of("ok", true);
            case "registered":
                ensure(number(data, "ship"));
                return Map.of("ok", true);
            case "ship-request-open", "ship-begin":
                var anchor = new Anchor(string(data, "world"), (int) number(data, "x"), (int) number(data, "y"), (int) number(data, "z"));
                nearby(player, anchor);
                if (action.equals("ship-begin")) return registrations.begin(actor, anchor);
                player.m_213846_(Component.m_237113_("Подайте заявку командою /shiprequest submit <назва>, дивлячись на блок корабля."));
                return Map.of("ok", true);
            default: throw new IllegalArgumentException("Невідома дія флоту.");
        }
    }

    Map<String, Object> engagementRequest(String action, Map<String, Object> data) throws SQLException {
        switch (action) {
            case "resident": return Map.of("town", Objects.toString(hometown(string(data, "player")), ""));
            case "recruit-owner": return call(recruits, "identity", data);
            case "point": {
                String dimension = feudal.dimensionFor(string(data, "world"));
                if (dimension == null) return Map.of("water", "UNKNOWN");
                var plot = TownyForge.view().at(dimension, (int) Math.floor(coordinate(data, "x")), (int) Math.floor(coordinate(data, "z")));
                return Map.of("water", water(string(data, "town"), plot == null ? null : ForgeFeudal.townId(plot.town())));
            }
            case "friendly": {
                String a = string(data, "a"), b = string(data, "b");
                return Map.of("friendly", allied(a, b) && !war(a, b));
            }
            case "ship", "controls", "open": {
                long id = number(data, "ship");
                ensure(id);
                String owner = string(fleet.ship(id), "town");
                boolean unavailable = !fleet.ownerAvailable(owner) || registrations.pending(id);
                boolean locked = unavailable || feudal.db.one("SELECT ship FROM naval_locks WHERE ship=?", id) != null;
                if (action.equals("ship")) {
                    var hull = hull(id);
                    if (unavailable || hull == null) return Map.of("ready", false, "locked", locked);
                    var waters = territories(hull).stream().map(city -> water(owner, city)).sorted().toList();
                    String nation = nation(owner);
                    var design = feudal.db.one("SELECT nation,kind,payload,hash,revision FROM naval_ensigns WHERE nation=?", nation);
                    return Map.of("ready", true, "town", owner, "locked", locked, "nation", nation,
                            "waters", waters.isEmpty() ? List.of("WILDERNESS") : waters, "design", design == null ? Map.of() : design);
                }
                String person = string(data, "player");
                var player = server.m_6846_().m_11259_(UUID.fromString(person));
                boolean crew = fleet.controls(person, id);
                boolean allow = !locked && player != null && (crew || player.m_20310_(2));
                if (action.equals("controls")) return Map.of("allow", allow, "crew", crew, "locked", locked);
                require(allow, "Керування прапором недоступне.");
                player.m_213846_(Component.m_237113_("Морський прапор · /fleet flag status | prepare <режим> | apply <режим> <підтвердження>"));
                return Map.of("ok", true);
            }
            default: throw new IllegalArgumentException("Невідома дія бойової системи.");
        }
    }

    Map<String, Object> engagement(String action, Map<String, Object> data) {
        require(engagement != null, "Потрібен Empirecraft Engagement.");
        return call(engagement, action, data);
    }

    String nation(String town) {
        var record = town(town);
        return record == null ? "" : Objects.toString(record.nation(), "");
    }

    private String water(String owner, String territory) {
        if (territory == null) return "WILDERNESS";
        if (owner == null || owner.isEmpty()) return "UNKNOWN";
        if (owner.equals(territory)) return "OWN";
        if (war(owner, territory)) return "ENEMY";
        return allied(owner, territory) ? "ALLIED" : "NEUTRAL";
    }

    void nearby(ServerPlayer player, Anchor anchor) {
        var p = ForgeFeudal.place(player);
        double x = p.x() - anchor.x() - .5, y = p.y() - anchor.y() - .5, z = p.z() - anchor.z() - .5;
        require(p.world().equals(anchor.world()) && x * x + y * y + z * z <= 64, "Підійдіть до блока корабля.");
    }

    private boolean valid(Anchor anchor) {
        var level = feudal.level(anchor.world());
        var pos = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        return nativeApi != null && level != null && anchor.y() >= level.m_141937_() && anchor.y() < level.m_151558_()
                && level.m_6857_().m_61937_(pos) && !level.m_8055_(pos).m_60795_()
                && !call(nativeApi, "at", Map.of("world", anchor.world(), "x", anchor.x(), "y", anchor.y(), "z", anchor.z())).containsKey("ship");
    }

    private static Hull decode(Map<String, Object> row) {
        if (row.isEmpty()) return null;
        var points = new ArrayList<FleetGeometry.Point>();
        for (Object corner : (List<?>) row.get("footprint")) {
            var p = (List<?>) corner;
            points.add(new FleetGeometry.Point(((Number) p.get(0)).doubleValue(), ((Number) p.get(1)).doubleValue()));
        }
        return new Hull(number(row, "id"), string(row, "world"), FleetGeometry.hull(points),
                coordinate(row, "x"), coordinate(row, "y"), coordinate(row, "z"),
                string(row, "town"), string(row, "name"), string(row, "information"), bool(row, "loaded"));
    }

    private static double coordinate(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).doubleValue();
    }

    @Override public Hull hull(long id) {
        return decode(call(nativeApi, "snapshot", Map.of("ship", id)));
    }

    @Override public boolean combat(long id) {
        return Boolean.TRUE.equals(call(engagement, "combat", Map.of("ship", id)).get("active"));
    }

    @Override public String hometown(String person) {
        return feudal.hometown(person);
    }

    @Override public boolean person(String id) {
        return TownyForge.view().residents().containsKey(UUID.fromString(id));
    }

    private TownyView.Town town(String id) {
        if (id == null || !id.startsWith("t:")) return null;
        return TownyForge.view().towns().get(UUID.fromString(id.substring(2)));
    }

    @Override public boolean mayor(String person, String town) {
        var record = town(town);
        return record != null && record.mayor().toString().equals(person);
    }

    @Override public boolean direct(String seller, String buyer) {
        var town = town(seller);
        ServerPlayer a = town == null ? null : server.m_6846_().m_11259_(town.mayor());
        ServerPlayer b = server.m_6846_().m_11259_(UUID.fromString(buyer));
        return a != null && b != null && a.m_284548_() == b.m_284548_() && a.m_20280_(b) <= 36;
    }

    @Override public boolean allied(String a, String b) {
        if (Objects.equals(a, b)) return true;
        var left = town(a);
        var right = town(b);
        if (left == null || right == null || left.nation() == null || right.nation() == null) return false;
        return left.nation().equals(right.nation()) || TownyForge.view().nations().get(left.nation()).allies().contains(right.nation())
                || feudal.treatyAlliance(left.nation(), right.nation());
    }

    @Override public boolean war(String a, String b) {
        var left = town(a);
        var right = town(b);
        if (left == null || right == null || left.nation() == null || right.nation() == null) return false;
        var nations = TownyForge.view().nations();
        return nations.get(left.nation()).enemies().contains(right.nation()) || nations.get(right.nation()).enemies().contains(left.nation());
    }

    @Override public boolean republic(String town) {
        try {
            var seen = new HashSet<String>();
            for (String id = town; id != null && seen.add(id); id = feudal.realms.parent(id)) {
                if (feudal.politics.state(id).stage() == PoliticalModel.Stage.TRADE_REPUBLIC
                        && feudal.politics.state(id).government() == PoliticalModel.Government.REPUBLIC) return true;
            }
            return false;
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override public Set<String> territories(Hull hull) {
        String dimension = feudal.dimensionFor(hull.world());
        var points = hull.footprint();
        if (dimension == null || points.isEmpty()) return Set.of();
        int x1 = chunk(points.stream().mapToDouble(FleetGeometry.Point::x).min().orElseThrow());
        int x2 = chunk(Math.nextDown(points.stream().mapToDouble(FleetGeometry.Point::x).max().orElseThrow()));
        int z1 = chunk(points.stream().mapToDouble(FleetGeometry.Point::z).min().orElseThrow());
        int z2 = chunk(Math.nextDown(points.stream().mapToDouble(FleetGeometry.Point::z).max().orElseThrow()));
        require((x2 - x1 + 1L) * (z2 - z1 + 1L) <= 4096, "Корпус завеликий для перевірки приватів.");
        var claims = TownyForge.view().territory();
        Set<String> owned = new HashSet<>();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                var plot = claims.get(new TownyView.Chunk(dimension, x, z));
                if (plot != null && !FleetGeometry.intersection(points, x * 16.0, z * 16.0, (x + 1) * 16.0, (z + 1) * 16.0).isEmpty()) {
                    owned.add(ForgeFeudal.townId(plot.town()));
                }
            }
        }
        return owned;
    }

    private static int chunk(double block) {
        return (int) Math.floor(block / 16);
    }

    @Override public boolean docked(Hull hull, String town, Place counter) {
        return hull.world().equals(counter.world()) && FleetGeometry.docked(hull.footprint(), counter.x(), counter.z(), 16, 200,
                (x, z) -> feudal.owns(town, hull.world(), x, z));
    }

    @Override public boolean visible(String person, Hull hull) {
        return Boolean.TRUE.equals(call(nativeApi, "visible", Map.of("player", person, "ship", hull.id())).get("visible"));
    }
}
