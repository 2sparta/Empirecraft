package ua.empirecraft.townymap;

import journeymap.api.v2.common.Context;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.server.overlay.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public final class BridgeService {
    private static final String MOD_ID = EmpireTownyMap.MOD_ID;
    private final IServerOverlayAPI overlays;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private MinecraftServer server;
    private TownyAccess towny;
    private ExecutorService worker;
    private KnowledgeStore store;
    private boolean indexing, stopping;
    private long tick, nextRefresh, nextWarning, nextTownyStatus, sourceRevision;
    private String waitingForTowny;
    private Map<MapMode, Map<String, Claims>> source = Map.of();
    private ClaimIndex index;
    private BanditTerritories bandits = BanditTerritories.EMPTY;
    private static BridgeService active;

    BridgeService(IServerOverlayAPI overlays) { this.overlays = overlays; }

    public static void select(UUID player, MapMode mode) {
        if (active != null) active.session(player).mode = mode;
    }
    public static MapMode selected(UUID player) {
        return active == null || !active.sessions.containsKey(player) ? MapMode.KINGDOMS : active.sessions.get(player).mode;
    }
    /** Queries never touch Towny and never refresh a remote observation. */
    static MapHistoryNetwork.Reply describe(ServerPlayer player, MapHistoryNetwork.Query query) {
        if (active == null) return null;
        Session session = active.session(player.getUUID());
        if (active.tick < session.queryAt) return null;
        session.queryAt = active.tick + 10;
        int x = query.x(), z = query.z(); String dimension = query.dimension();
        ResourceLocation location = ResourceLocation.tryParse(dimension);
        var chunk = location == null ? null : ClaimKnowledge.Chunk.tryAt(location.toString(), x, z);
        if (chunk == null) return null;
        String currentDimension = player.serverLevel().dimension().location().toString();
        var nearby = active.bandits.nearby(currentDimension, player.getX(), player.getZ());
        var claims = new ArrayList<>(active.bandits.visible(session.mode, currentDimension, nearby));
        var observation = session.knowledge == null ? null : session.knowledge.at(chunk);
        ClaimKnowledge history = observation == null ? null : ClaimKnowledge.restore(active.bandits.history(
                new ClaimKnowledge.Snapshot(ClaimKnowledge.FORMAT, List.of(observation))));
        var entries = new ArrayList<MapHistoryNetwork.Entry>();
        var earthDimensions=ClimateObservations.earthDimensions();
        if (history != null) history.labelsAt(chunk, session.mode, x, z).stream()
                .filter(label -> ClimateObservations.displayable(label,earthDimensions))
                .forEach(label -> entries.add(new MapHistoryNetwork.Entry(label.name(), label.detail(), label.profile())));
        claims.stream().filter(c -> c.dimension().equals(dimension)
                && c.cells().contains(new ClaimGeometry.Cell(Math.floorDiv(x, c.cellSize()), Math.floorDiv(z, c.cellSize()))))
                .forEach(c -> entries.add(new MapHistoryNetwork.Entry(c.name(), c.detail(), c.profile())));
        if (entries.isEmpty() && (history == null || history.at(chunk) == null))
            return new MapHistoryNetwork.Reply(query.request(), MapHistoryNetwork.Status.UNKNOWN, List.of());
        return new MapHistoryNetwork.Reply(query.request(), entries.isEmpty() ? MapHistoryNetwork.Status.EMPTY : MapHistoryNetwork.Status.KNOWN,
                entries.stream().distinct().limit(32).toList());
    }

    @SubscribeEvent public void started(ServerStartedEvent event) { start(event.getServer()); }
    private void start(MinecraftServer target) {
        if (server == target) return;
        server = target; active = this; stopping = false;
        tick = 0; nextRefresh = 0; nextWarning = 0; sourceRevision = 0;
        waitingForTowny = null; nextTownyStatus = 0;
        sessions.clear(); source = Map.of(); index = null; bandits = BanditTerritories.EMPTY; towny = null; indexing = false;
        store = new KnowledgeStore(target.getWorldPath(LevelResource.ROOT).resolve("empirecraft/claim-knowledge"),
                error -> EmpireTownyMap.LOG.error("Could not save personal claim history; retaining pending data for retry", error));
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "EmpireCraft-claim-polygons"); thread.setDaemon(true); return thread;
        });
    }
    private Session session(UUID player) {
        return sessions.computeIfAbsent(player, ignored -> new Session(tick + 100));
    }
    private void save(UUID player, Session session) {
        if (session.knowledge != null && session.knowledge.dirty()) {
            store.save(player, session.knowledge.snapshot()); session.knowledge.saved();
        }
    }
    @SubscribeEvent public void stopped(ServerStoppingEvent event) {
        if (event.getServer() != server) return;
        stopping = true;
        sessions.forEach(this::save);
        try { store.close(); } catch (IOException error) { EmpireTownyMap.LOG.error("Personal claim history could not be fully saved", error); }
        for (var player : server.getPlayerList().getPlayers()) {
            try { overlays.clearAll(player, MOD_ID); } catch (RuntimeException error) { warn("Could not clear JourneyMap on shutdown", error); }
        }
        server = null; active = null; index = null; bandits = BanditTerritories.EMPTY; source = Map.of(); towny = null; sessions.clear();
        if (worker != null) worker.shutdownNow();
        worker = null; store = null;
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID(); Session session = sessions.remove(player);
        if (session != null && store != null) save(player, session);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stopping) return;
        // JourneyMap may register us after ServerStartedEvent's listener list was captured.
        if (server == null) {
            MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
            if (current == null || !current.isRunning()) return;
            start(current);
        }
        tick++;
        if (tick >= nextRefresh && !indexing) refresh();
        Style style = new Style(EmpireTownyMap.FILL_OPACITY.get().floatValue(), EmpireTownyMap.BORDER_WIDTH.get().floatValue());
        for (var player : server.getPlayerList().getPlayers()) {
            Session session = session(player.getUUID());
            String dimension = player.serverLevel().dimension().location().toString();
            var nearby = bandits.nearby(dimension, player.getX(), player.getZ());
            if (!nearby.equals(session.nearby) || !dimension.equals(session.dimension)) {
                session.nearby = nearby; session.dimension = dimension; session.visibilityRevision++;
                // Drop temporary geometry now, even while a replacement is still being built.
                var retained = new TreeMap<>(session.view);
                retained.keySet().removeIf(id -> id.startsWith("temporary/"));
                session.view = Map.copyOf(retained);
            }
            // Observe independently of JourneyMap's handshake, including short visits during login.
            if (session.knowledge == null && tick >= session.loadAt) {
                try { session.knowledge = store.load(player.getUUID()); }
                catch (IOException error) {
                    session.loadAt = tick + 1200;
                    warn("Could not load personal claim history; leaving its file untouched and retrying", error);
                }
            }
            if (session.knowledge != null) {
                var level = player.serverLevel();
                var center = ClaimKnowledge.Chunk.tryAt(dimension, player.getBlockX(), player.getBlockZ());
                if (center != null) {
                    var currentIndex = index;
                    long now = System.currentTimeMillis();
                    session.survey.visit(center, currentIndex == null ? -1 : sourceRevision, tick, chunk -> {
                        var loaded = level.getChunkSource().getChunkNow(chunk.x(),chunk.z());
                        if (loaded == null) return false;
                        var political = currentIndex == null ? null : currentIndex.observe(chunk);
                        var climate = ClimateObservations.sample(level, loaded, chunk);
                        session.knowledge.survey(chunk, political, climate, now);
                        return true;
                    });
                }
                buildView(player.getUUID(), session, style);
            }
            if (tick < session.readyAt) continue;
            try {
                if (!session.started) { overlays.clearAll(player, MOD_ID); session.started = true; }
                session.sync.removeMatching(id -> id.startsWith("temporary/") && !session.view.containsKey(id),
                        id -> overlays.remove(player, MOD_ID, id));
                if (tick >= session.resendAt) {
                    // Also remove overlays cached by an older, globally broadcasting bridge.
                    overlays.clearAll(player, MOD_ID); session.sync.invalidate(); session.resendAt = tick + 1200;
                }
                session.sync.update(session.view, 4, id -> overlays.remove(player, MOD_ID, id),
                        (id, polygon) -> overlays.show(player, MOD_ID, polygon));
            } catch (RuntimeException error) { warn("Could not send JourneyMap observations; will retry", error); }
        }
        if (tick % 600 == 0) { sessions.forEach(this::save); store.retryPending(); }
    }

    private void buildView(UUID player, Session session, Style style) {
        long revision = session.knowledge.revision(); MapMode mode = session.mode;
        long visibilityRevision = session.visibilityRevision, liveRevision = sourceRevision;
        var nearby = session.nearby; String dimension = session.dimension;
        var liveBandits = bandits;
        var earthDimensions=ClimateObservations.earthDimensions();
        if (session.building || revision == session.viewRevision && mode == session.viewMode && style.equals(session.style)
                && visibilityRevision == session.viewVisibilityRevision && liveRevision == session.viewSourceRevision
                && earthDimensions.equals(session.viewEarthDimensions)) return;
        var observation = session.knowledge.snapshot();
        var previous = session.cache; boolean restyle = !style.equals(session.style);
        var target = server; session.building = true;
        CompletableFuture.supplyAsync(() -> {
            var built = new TreeMap<String, CachedClaims>();
            var visible = new ArrayList<>(ClaimKnowledge.claims(liveBandits.history(observation), mode,
                    label -> ClimateObservations.displayable(label,earthDimensions)));
            visible.addAll(liveBandits.visible(mode, dimension, nearby));
            for (Claims claims : visible) {
                CachedClaims old = previous.get(claims.id());
                built.put(claims.id(), !restyle && old != null && old.claims().equals(claims)
                        ? old : new CachedClaims(claims, polygons(claims, style, mode.environmental())));
            }
            return Map.copyOf(built);
        }, worker).whenComplete((built, error) -> target.execute(() -> {
            if (server != target || sessions.get(player) != session) return;
            session.building = false;
            if (error != null) { warn("Could not build personal claim boundaries; retaining previous observations", error); return; }
            if (session.knowledge.revision() != revision || session.mode != mode
                    || session.visibilityRevision != visibilityRevision || sourceRevision != liveRevision) return;
            var view = new TreeMap<String, ServerPolygon>();
            built.values().forEach(c -> c.polygons().forEach(p -> view.put(p.overlayId(), p)));
            session.cache = built; session.view = Map.copyOf(view);
            session.viewRevision = revision; session.viewMode = mode; session.style = style;
            session.viewVisibilityRevision = visibilityRevision; session.viewSourceRevision = liveRevision;
            session.viewEarthDimensions = earthDimensions;
        }));
    }
    private void refresh() {
        nextRefresh = tick + EmpireTownyMap.UPDATE_SECONDS.get() * 20L;
        try {
            if (towny == null) towny = new TownyAccess();
            var snapshot = towny.snapshotAll(EmpireTownyMap.INDEPENDENT_TOWNS.get());
            if (waitingForTowny != null) { EmpireTownyMap.LOG.info("Towny is ready; personal JourneyMap observations resumed"); waitingForTowny = null; }
            if (index != null && source.equals(snapshot)) return;
            var immutable = Map.copyOf(snapshot); var target = server; indexing = true;
            CompletableFuture.supplyAsync(() -> new SourceIndex(new ClaimIndex(immutable), new BanditTerritories(immutable)), worker).whenComplete((built, error) -> target.execute(() -> {
                if (server != target) return;
                indexing = false;
                if (error != null) { index = null; warn("Could not index Towny; personal observations paused", error); return; }
                source = immutable; index = built.permanent(); bandits = built.bandits(); sourceRevision++;
            }));
        } catch (TownyAccess.NotReadyException error) {
            towny = null; index = null; nextRefresh = tick + 20;
            String reason = error.getMessage();
            if (!reason.equals(waitingForTowny)) {
                EmpireTownyMap.LOG.info("Towny bridge waiting: {}. Retrying every second; personal history is retained", reason);
                waitingForTowny = reason; nextTownyStatus = tick + 1200;
            } else if (tick >= nextTownyStatus) {
                EmpireTownyMap.LOG.warn("Towny bridge still waiting: {}. Check Towny's startup log; personal history is retained", reason);
                nextTownyStatus = tick + 1200;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            towny = null; index = null;
            warn("Towny bridge could not read Towny; personal observations paused", error);
        }
    }
    private void warn(String message, Throwable error) {
        if (tick < nextWarning) return;
        nextWarning = tick + 1200; EmpireTownyMap.LOG.warn(message, error);
    }

    private static List<ServerPolygon> polygons(Claims claims, Style style, boolean environmental) {
        List<OverlayPolygon> shapes = ClaimGeometry.buildBounded(claims.cells(), claims.cellSize()).stream()
                .map(p -> new OverlayPolygon(points(p.outer()), p.holes().stream().map(BridgeService::points).toList()))
                .toList();
        var dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(claims.dimension()));
        float opacity=claims.layer()==0?style.opacity:claims.layer()==4?0.9f:0;
        var props = new OverlayShapeProps(claims.color(), opacity, claims.color(), claims.layer()==4?1:environmental?Math.min(0.6f,style.width):style.width,
                0.95f, 1000+claims.layer()*10, claims.layer()==3?Math.max(2,UIState.FULLSCREEN_ZOOM_MIN):UIState.FULLSCREEN_ZOOM_MIN, UIState.ZOOM_IN_MAX,
                EnumSet.allOf(Context.UI.class), EnumSet.allOf(Context.MapType.class),
                claims.name(), claims.detail());
        List<ServerPolygon> pages = new ArrayList<>();
        List<OverlayPolygon> page = new ArrayList<>();
        int points = 0;
        for (OverlayPolygon shape : shapes) {
            int count = shape.outer().points().size() + shape.holes().stream().mapToInt(h -> h.points().size()).sum();
            if (!page.isEmpty() && (points + count > 8192 || page.size() >= 64)) {
                pages.add(new ServerPolygon(claims.id() + "/" + pages.size(), dimension, List.copyOf(page), props));
                page.clear(); points = 0;
            }
            page.add(shape); points += count;
        }
        if (!page.isEmpty()) pages.add(new ServerPolygon(claims.id() + "/" + pages.size(), dimension, List.copyOf(page), props));
        return List.copyOf(pages);
    }

    private static OverlayPoints points(List<ClaimGeometry.Point> ring) {
        return new OverlayPoints(ring.stream().map(p -> BlockPos.asLong(p.x(), 64, p.z())).toList());
    }

    private record CachedClaims(Claims claims, List<ServerPolygon> polygons) {}
    private record SourceIndex(ClaimIndex permanent, BanditTerritories bandits) {}
    private record Style(float opacity, float width) {}
    private static final class Session {
        final long readyAt;
        long resendAt, loadAt, queryAt, viewRevision = -1;
        long visibilityRevision, viewVisibilityRevision = -1, viewSourceRevision = -1;
        boolean started, building;
        MapMode mode = MapMode.KINGDOMS, viewMode;
        final LocalSurvey survey = new LocalSurvey();
        ClaimKnowledge knowledge;
        Set<String> nearby = Set.of();
        Set<String> viewEarthDimensions = Set.of();
        String dimension = "";
        Style style;
        Map<String, CachedClaims> cache = Map.of();
        Map<String, ServerPolygon> view = Map.of();
        final OverlaySync<ServerPolygon> sync = new OverlaySync<>();
        Session(long readyAt) { this.readyAt = readyAt; this.resendAt = readyAt + 1200; }
    }
}
