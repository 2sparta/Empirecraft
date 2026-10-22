package ua.empirecraft.townymap;

import journeymap.api.v2.common.Context;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.server.overlay.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.*;

public final class BridgeService {
    private static final String MOD_ID = EmpireTownyMap.MOD_ID;
    private final IServerOverlayAPI overlays;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private MinecraftServer server;
    private TownyAccess towny;
    private ExecutorService worker;
    private boolean building;
    private long tick, nextRefresh, nextWarning;
    private Map<String, CachedClaims> cache = Map.of();
    private Map<String, ServerPolygon> desired = Map.of();
    private Style lastStyle;

    BridgeService(IServerOverlayAPI overlays) { this.overlays = overlays; }

    @SubscribeEvent
    public void started(ServerStartedEvent event) {
        start(event.getServer());
    }

    private void start(MinecraftServer target) {
        if (server == target) return;
        server = target;
        tick = 0; nextRefresh = 100; nextWarning = 0;
        sessions.clear(); cache = Map.of(); desired = Map.of(); lastStyle = null;
        towny = null; building = false;
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "EmpireCraft-claim-polygons");
            thread.setDaemon(true);
            return thread;
        });
    }

    @SubscribeEvent
    public void stopped(ServerStoppingEvent event) {
        if (event.getServer() != server) return;
        server.getPlayerList().getPlayers().forEach(p -> overlays.clearAll(p, MOD_ID));
        server = null; towny = null; sessions.clear(); cache = Map.of(); desired = Map.of();
        if (worker != null) worker.shutdownNow();
        worker = null;
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        sessions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // JourneyMap discovers server addons during ServerStartedEvent. Registration can happen
        // after that event's listener list was captured, so initialise lazily on the next tick too.
        if (server == null) {
            MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
            if (current == null || !current.isRunning()) return;
            start(current);
        }
        tick++;
        if (tick >= nextRefresh && !building) refresh();
        // New players wait for JourneyMap's client to finish its login handshake.
        for (var player : server.getPlayerList().getPlayers()) {
            Session session = sessions.computeIfAbsent(player.getUUID(), id -> new Session(tick + 100));
            if (tick < session.readyAt) continue;
            try {
                if (!session.started) {
                    overlays.clearAll(player, MOD_ID);
                    session.started = true;
                }
                // JourneyMap can drop a push during client initialisation. Refresh once a minute to recover.
                if (tick >= session.resendAt) {
                    session.sync.invalidate();
                    session.resendAt = tick + 1200;
                }
                session.sync.update(desired, 4, id -> overlays.remove(player, MOD_ID, id),
                        (id, polygon) -> overlays.show(player, MOD_ID, polygon));
            } catch (RuntimeException e) { warn("Could not send JourneyMap overlays; will retry", e); }
        }
    }

    private void refresh() {
        nextRefresh = tick + EmpireTownyMap.UPDATE_SECONDS.get() * 20L;
        try {
            if (towny == null) towny = new TownyAccess();
            Map<String, Claims> snapshot = towny.snapshot(EmpireTownyMap.INDEPENDENT_TOWNS.get());
            Style style = new Style(EmpireTownyMap.FILL_OPACITY.get().floatValue(),
                    EmpireTownyMap.BORDER_WIDTH.get().floatValue());
            boolean changed = !style.equals(lastStyle) || snapshot.size() != cache.size()
                    || snapshot.entrySet().stream().anyMatch(e -> !cache.containsKey(e.getKey())
                    || !cache.get(e.getKey()).claims.equals(e.getValue()));
            if (!changed) return;
            // Snapshot capture is on the server thread. Only immutable data goes to the geometry worker.
            Map<String, CachedClaims> previous = cache;
            boolean restyle = !style.equals(lastStyle);
            MinecraftServer target = server;
            building = true;
            CompletableFuture.supplyAsync(() -> {
                Map<String, CachedClaims> built = new TreeMap<>();
                snapshot.forEach((id, claims) -> {
                    CachedClaims old = previous.get(id);
                    built.put(id, !restyle && old != null && old.claims.equals(claims)
                            ? old : new CachedClaims(claims, polygons(claims, style)));
                });
                return built;
            }, worker).whenComplete((built, error) -> target.execute(() -> {
                if (server != target) return;
                building = false;
                if (error != null) { warn("Could not build Towny boundaries; retaining previous snapshot", error); return; }
                cache = Map.copyOf(built);
                Map<String, ServerPolygon> next = new TreeMap<>();
                built.values().forEach(g -> g.polygons.forEach(p -> next.put(p.overlayId(), p)));
                desired = Collections.unmodifiableMap(next);
                if (lastStyle == null)
                    EmpireTownyMap.LOG.info("Towny bridge published {} territories ({} JourneyMap overlay pages)", cache.size(), desired.size());
                lastStyle = style;
                EmpireTownyMap.LOG.debug("Updated {} Towny territories ({} overlay pages)", cache.size(), desired.size());
            }));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            towny = null;
            // Disabled/missing Towny must not leave obsolete borders on players' maps.
            cache = Map.of(); desired = Map.of(); lastStyle = null;
            warn("Towny bridge could not read Towny on this Mohist server; retrying", e);
        }
    }

    private void warn(String message, Throwable error) {
        if (tick < nextWarning) return;
        nextWarning = tick + 1200;
        EmpireTownyMap.LOG.warn(message, error);
    }

    private static List<ServerPolygon> polygons(Claims claims, Style style) {
        List<OverlayPolygon> shapes = ClaimGeometry.buildBounded(claims.cells(), claims.cellSize()).stream()
                .map(p -> new OverlayPolygon(points(p.outer()), p.holes().stream().map(BridgeService::points).toList()))
                .toList();
        var dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(claims.dimension()));
        var props = new OverlayShapeProps(claims.color(), style.opacity, claims.color(), style.width,
                0.95f, 1000, UIState.FULLSCREEN_ZOOM_MIN, UIState.ZOOM_IN_MAX,
                EnumSet.allOf(Context.UI.class), EnumSet.allOf(Context.MapType.class),
                claims.name(), claims.name() + " · " + claims.cells().size() + " Towny plots");
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
    private record Style(float opacity, float width) {}
    private static final class Session {
        final long readyAt;
        long resendAt;
        boolean started;
        final OverlaySync<ServerPolygon> sync = new OverlaySync<>();
        Session(long readyAt) { this.readyAt = readyAt; this.resendAt = readyAt + 1200; }
    }
}
