package ua.empirecraft.feudal;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ua.empirecraft.townyforge.TownyForge;
import ua.empirecraft.townyforge.TownyView;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

public final class ForgeFeudal {
    private static final long SECOND = 1000;
    private static ForgeFeudal active;
    private static volatile String mapView;
    private TownyView lastView;
    private long nextCycle;
    private long nextProvision;
    private long nextPolitics;
    private MinecraftServer server;
    Database db;
    FeudalService realms;
    PoliticsService politics;
    CurrencyMarket currency;
    CommerceService commerce;
    CityMarketService markets;
    ProvisionService provisions;
    ContractService treaties;
    ForgeCastles castles;
    ArmyService army;
    ForgeFleet fleet;

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        server = event.getServer();
        try {
            Path world = server.m_129843_(LevelResource.f_78182_);
            Path work = Files.createDirectories(world.resolve("empirecraft/feudal"));
            Class.forName("org.h2.Driver", true, ForgeFeudal.class.getClassLoader());
            db = new Database("jdbc:h2:file:" + work.resolve("feudal").toAbsolutePath()
                    + ";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE");
            db.retireBase();
            realms = new FeudalService(db, Clock.systemUTC(), 7 * FeudalService.DAY, 48 * 3600 * SECOND, 20, 12, 2);
            realms.debtsEnabled = false;
            synchronizeTowns();
            politics = new PoliticsService(realms, new PoliticsRules(key -> -1));
            updatePoliticalPopulation(lastView);
            castles = new ForgeCastles(this);
            currency = new CurrencyMarket(realms, politics);
            realms.currencies.migratePersonalGold();
            currency.printingOffice = (person, estate) -> {
                try {
                    String capital = realms.kingdomCapitals.get(estate);
                    if (capital == null) capital = estate.startsWith("t:") ? estate : politics.state(estate).capital();
                    return capital != null && capital.equals(hometown(person));
                } catch (SQLException failure) {
                    throw new IllegalStateException(failure);
                }
            };
            currency.citizen = (person, estate) -> estate.equals(hometown(person)) || realms.citizen(person, estate);
            commerce = new CommerceService(realms);
            markets = new CityMarketService(realms, commerce);
            markets.land = this::owns;
            var buildings = new ForgeMarketBuildings(this);
            markets.buildingValid = buildings::valid;
            markets.buildingApproval = buildings::capture;
            markets.fishing.nativeReady = false;
            treaties = new ContractService(realms, politics);
            realms.contractBuilder = treaties;
            treaties.names = this::playerName;
            treaties.changed = () -> lastView = null;
            provisions = new ProvisionService(realms, commerce);
            army = new ArmyService(realms, provisions, politics);
            fleet = new ForgeFleet(this, server);
            currency.exchange.names = this::playerName;
            db.execute("UPDATE provision_actions SET status='CANCELLED',detail='Forge migration' WHERE status='PENDING'");
            publishMap();
            active = this;
            fleet.bootstrap();
            LogUtils.getLogger().info("Empirecraft Feudal Forge ready ({} estates)", realms.estates().size());
        } catch (Exception failure) {
            try {
                close();
            } catch (SQLException closing) {
                failure.addSuppressed(closing);
            }
            throw new IllegalStateException("Empirecraft Feudal could not open the world database", failure);
        }
    }

    @SubscribeEvent
    public void stop(ServerStoppingEvent event) {
        try {
            close();
        } catch (SQLException failure) {
            LogUtils.getLogger().error("Failed to close the Feudal database", failure);
        }
    }

    private void close() throws SQLException {
        active = null;
        mapView = null;
        lastView = null;
        nextCycle = nextProvision = nextPolitics = 0;
        if (db != null) {
            try {
                db.close();
            } finally {
                db = null;
            }
        }
        politics = null;
        server = null;
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        new ForgeFeudalCommands(this).register(event.getDispatcher());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || active != this) return;
        long now = realms.now();
        if (now < nextCycle) return;
        nextCycle = now + SECOND;
        try {
            synchronizeTowns();
            realms.tick();
            treaties.tick();
            currency.tick();
            markets.tick();
            commerce.tick();
            fleet.tick();
            if (now >= nextPolitics) {
                nextPolitics = now + 60 * SECOND;
                politics.tick();
            }
            if (now >= nextProvision) {
                nextProvision = now + 60 * SECOND;
                settleProvision(now);
            }
        } catch (SQLException | RuntimeException failure) {
            nextCycle = now + 10 * SECOND;
            LogUtils.getLogger().error("Empirecraft Feudal maintenance failed", failure);
        }
    }

    boolean ready() {
        return active == this && server.m_18695_();
    }

    public static ForgeFeudal active() {
        return active != null && active.ready() ? active : null;
    }

    public static String mapView() {
        return mapView;
    }

    void synchronizeTowns() throws SQLException {
        TownyView view = TownyForge.view();
        if (view == lastView) return;
        var estates = new ArrayList<Estate>();
        var identities = new HashMap<UUID, String>();
        realms.kingdomCapitals.clear();
        for (var nation : view.nations().values()) {
            var capital = view.towns().get(nation.capital());
            String id = "n:" + nation.id();
            realms.kingdomCapitals.put(id, townId(capital.id()));
            identities.put(nation.id(), id);
            estates.add(new Estate(id, Kind.KINGDOM, nation.name(), id, capital.mayor().toString(), true));
        }
        var membership = new HashMap<String, String>();
        Set<String> citizenship = new HashSet<>();
        for (var town : view.towns().values()) {
            String id = townId(town.id());
            String nation = town.nation() == null ? null : identities.get(town.nation());
            membership.put(id, nation);
            estates.add(new Estate(id, Kind.BARONY, town.name(), nation, town.mayor().toString(), true));
        }
        var parents = new HashMap<String, String>();
        for (var row : db.query("SELECT child,parent FROM ties")) parents.put(string(row, "child"), string(row, "parent"));
        for (var player : view.residents().values()) {
            if (player.town() == null) continue;
            String id = townId(player.town()), nation = membership.get(id);
            if (nation != null) citizenship.add(player.id() + "@" + nation);
            var visited = new HashSet<String>();
            while (id != null && visited.add(id)) {
                citizenship.add(player.id() + "@" + id);
                id = parents.get(id);
            }
        }
        realms.sync(new Snapshot(estates, citizenship));
        lastView = view;
        if (politics != null) {
            updatePoliticalPopulation(view);
            publishMap();
        }
    }

    private void updatePoliticalPopulation(TownyView view) {
        var population = new HashMap<String, Set<String>>();
        for (var resident : view.residents().values()) {
            if (resident.town() != null) population.computeIfAbsent(townId(resident.town()), key -> new HashSet<>())
                    .add(resident.id().toString());
        }
        politics.population(population);
        politics.syncDomains();
    }

    private void publishMap() throws SQLException {
        var estates = db.query("SELECT e.id,e.name,e.kind,e.lord,COALESCE(t.parent,'') AS parent FROM estates e "
                + "LEFT JOIN ties t ON e.id=t.child WHERE e.active=TRUE ORDER BY e.id");
        var points = db.one("SELECT table_name FROM information_schema.tables WHERE table_name='map_points'") == null
                ? List.of() : db.query("SELECT p.* FROM map_points p JOIN estates e ON p.estate=e.id "
                        + "WHERE e.active=TRUE ORDER BY p.estate,p.kind");
        mapView = new Gson().toJson(Map.of("estates", estates, "points", points));
    }

    void settleProvision(long now) {
        var view = TownyForge.view();
        var chunks = new HashMap<UUID, Long>();
        for (var territory : view.territory().values()) chunks.merge(territory.town(), 1L, Long::sum);
        for (var town : view.towns().values()) settleTown(town.id(), chunks.getOrDefault(town.id(), 0L), now);
    }

    private ProvisionClock townClock(UUID id, long now) {
        var town = TownyForge.view().towns().get(id);
        require(town != null, "Місто недоступне.");
        String world = town.spawn() == null ? null : TownyForge.legacyWorld(town.spawn().dimension());
        return ProvisionClock.world(world, now);
    }

    void settleTown(UUID id, long chunks, long now) {
        ProvisionClock clock;
        try {
            clock = townClock(id, now);
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            return;
        }
        provisions.settle(townId(id), chunks, now, clock);
    }

    public void requireClaimProvision(UUID id, long existing) {
        long now = realms.now();
        try {
            ProvisionClock clock = townClock(id, now);
            require(provisions.settle(townId(id), existing, now, clock), "У місті є незавершена передача провізії.");
            require(provisions.canSupport(townId(id), existing + 1, now, clock),
                    "Для розширення міста внесіть провізію на 24 години для всієї нової території.");
        } catch (SQLException failure) {
            throw new IllegalStateException("Не вдалося перевірити провізію.", failure);
        }
    }

    public int feedRecruit(UUID recruit, UUID owner, UUID home, double hunger) throws SQLException {
        require(ready(), "Провізія ще завантажується.");
        var town = TownyForge.view().towns().get(home);
        String townId = townId(home);
        if (town == null || provisions.town(townId) == null || hunger > 70) return 0;
        long now = realms.now();
        String unit = recruit.toString();
        var record = army.unit(unit);
        if (record == null || !bool(record, "loaded") || !Objects.equals(string(record, "home"), townId)
                || !owner.toString().equals(string(record, "owner")) || now - number(record, "last_seen") >= 60000) {
            army.observe(unit, owner.toString(), townId, hunger);
            db.execute("UPDATE army_units SET home=? WHERE id=?", townId, unit);
            record = army.unit(unit);
        }
        if (!bool(record, "alive")) return 0;
        return army.feed(unit, number(record, "feed_seq") + 1, 10);
    }

    public void retireRecruit(UUID recruit) {
        if (ready()) army.retire(recruit.toString());
    }

    boolean owns(String town, String world, int chunkX, int chunkZ) {
        String dimension = dimensionFor(world);
        if (dimension == null) return false;
        var plot = TownyForge.view().territory().get(new TownyView.Chunk(dimension, chunkX, chunkZ));
        return plot != null && town.equals(townId(plot.town()));
    }

    static String townId(UUID town) {
        return town == null ? null : "t:" + town;
    }

    String hometown(String person) {
        return townId(TownyForge.view().townOf(UUID.fromString(person)));
    }

    String playerName(String person) {
        try {
            var resident = TownyForge.view().residents().get(UUID.fromString(person));
            return resident == null ? person : resident.name();
        } catch (IllegalArgumentException invalid) {
            return person;
        }
    }

    static String dimension(ServerLevel level) {
        return level.m_46472_().m_135782_().toString();
    }

    String dimensionFor(String world) {
        if (world == null) return null;
        for (ServerLevel level : server.m_129785_()) {
            String dimension = dimension(level);
            if (world.equals(dimension) || world.equals(TownyForge.legacyWorld(dimension))) return dimension;
        }
        return null;
    }

    ServerLevel level(String world) {
        String dimension = dimensionFor(world);
        return dimension == null ? null : server.m_129880_(ResourceKey.m_135785_(Registries.f_256858_, new ResourceLocation(dimension)));
    }

    static CityMarketService.Place place(net.minecraft.server.level.ServerPlayer player) {
        String dimension = dimension(player.m_284548_());
        return new CityMarketService.Place(Objects.toString(TownyForge.legacyWorld(dimension), dimension),
                player.m_20185_(), player.m_20186_(), player.m_20189_());
    }

    public Map<String, Object> engagementRequest(String action, Map<String, Object> data) throws SQLException {
        require(ready(), "Флот ще завантажується.");
        return fleet.engagementRequest(action, data);
    }

    public Map<String, Object> fleetRequest(String action, Map<String, Object> data) throws Exception {
        require(ready(), "Флот ще завантажується.");
        return fleet.request(action, data);
    }
}
