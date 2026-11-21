/* SPDX-License-Identifier: LGPL-3.0-only */
package me.mss1r.recruitsrtscommand.client;

import journeymap.api.v2.client.fullscreen.IFullscreen;
import me.mss1r.recruitsrtscommand.api.WorldMapOverlayRegistry;
import me.mss1r.recruitsrtscommand.api.WorldMapView;
import me.mss1r.recruitsrtscommand.client.command.MapCommandClientState;
import me.mss1r.recruitsrtscommand.client.command.MapCommandNetworkClient;
import me.mss1r.recruitsrtscommand.client.gui.worldmap.command.WorldMapCommandOverlay;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ua.empirecraft.townymap.client.ClientAccess;

public final class RecruitsRTSCommandClientEvents {
    private static final int SYNC_INTERVAL_TICKS = 10;
    private int syncTicks;
    private Object openMap;

    public RecruitsRTSCommandClientEvents() {
        WorldMapOverlayRegistry.register(WorldMapCommandOverlay.INSTANCE);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Object screen = ClientAccess.screen();
        Object map = ClientAccess.isMap(screen) ? screen : null;
        if (openMap != map) {
            WorldMapCommandOverlay.INSTANCE.reset();
            openMap = map;
            syncTicks = 0;
        }
        if (map != null && MapCommandClientState.isCommandModeEnabled()
                && syncTicks++ % SYNC_INTERVAL_TICKS == 0) {
            MapCommandNetworkClient.requestSync();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.f_46443_) {
            MapCommandClientState.clear();
            WorldMapCommandOverlay.INSTANCE.reset();
            openMap = null;
            syncTicks = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void render(ScreenEvent.Render.Post event) {
        WorldMapView view = view(event);
        if (view == null) return;
        var overlays = WorldMapOverlayRegistry.overlays();
        for (var overlay : overlays) {
            overlay.renderMap(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick(), view);
        }
        for (var overlay : overlays) {
            overlay.renderUi(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick(), view);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void click(ScreenEvent.MouseButtonPressed.Pre event) {
        WorldMapView view = view(event);
        if (view == null) return;
        double x = event.getMouseX(), y = event.getMouseY();
        if (event.getButton() != 0 && view.isOverToggle(x, y)) return;
        for (var overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.mouseClicked(x, y, event.getButton(), view)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void drag(ScreenEvent.MouseDragged.Pre event) {
        WorldMapView view = activeView(event);
        if (view == null) return;
        for (var overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(),
                    event.getDragX(), event.getDragY(), view)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void release(ScreenEvent.MouseButtonReleased.Pre event) {
        WorldMapView view = activeView(event);
        if (view == null) return;
        for (var overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton(), view)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void key(ScreenEvent.KeyPressed.Pre event) {
        WorldMapView view = activeView(event);
        if (view == null) return;
        for (var overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers(), view)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private WorldMapView activeView(ScreenEvent event) {
        return MapCommandClientState.isCommandModeEnabled() ? view(event) : null;
    }

    private WorldMapView view(ScreenEvent event) {
        Object screen = event.getScreen();
        if (!ClientAccess.isMap(screen) || ClientAccess.player() == null
                || Boolean.TRUE.equals(ClientAccess.field(ClientAccess.field(screen, "popupMenu"), "visible"))
                || Boolean.TRUE.equals(ClientAccess.call(screen, "isChatOpen"))
                || Boolean.TRUE.equals(ClientAccess.call(screen, "isSearchFocused"))) return null;
        IFullscreen map = (IFullscreen) screen;
        var state = map.getUiState();
        Level level = (Level) ClientAccess.field(ClientAccess.client(), "f_91073_");
        if (level == null || state == null || !state.active || state.blockSize <= 0
                || !level.m_46472_().equals(state.dimension)) return null;
        return new WorldMapView(map);
    }
}
