/* SPDX-License-Identifier: LGPL-3.0-only */
package me.mss1r.recruitsrtscommand.api;

import journeymap.api.v2.client.fullscreen.IFullscreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import ua.empirecraft.townymap.client.ClientAccess;

/** The affine transform between the JourneyMap canvas and the RTS GUI. */
public final class WorldMapView {
    public static final int TOGGLE_TOP = 25;
    public static final int TOGGLE_WIDTH = 42;
    public static final int TOGGLE_HEIGHT = 20;

    private final IFullscreen map;
    private final double scaleX;
    private final double scaleZ;
    private final double offsetX;
    private final double offsetZ;

    public WorldMapView(IFullscreen map) {
        this.map = map;
        Object window = ClientAccess.call(minecraft(), "m_91268_");
        double blockPixels = map.getUiState().blockSize;
        int width = ((Number) ClientAccess.call(window, "m_85443_")).intValue();
        int height = ((Number) ClientAccess.call(window, "m_85444_")).intValue();
        this.scaleX = blockPixels * screenWidth() / width;
        this.scaleZ = blockPixels * screenHeight() / height;
        this.offsetX = screenWidth() / 2.0 - map.getCenterBlockX(true) * scaleX;
        this.offsetZ = screenHeight() / 2.0 - map.getCenterBlockZ(true) * scaleZ;
    }

    public Screen screen() {
        return map.getScreen();
    }

    public Minecraft minecraft() {
        return map.getMinecraft();
    }

    public Player player() {
        return (Player) ClientAccess.player();
    }

    public int screenWidth() {
        return ClientAccess.width(screen());
    }

    public int screenHeight() {
        return ClientAccess.height(screen());
    }

    public static int toggleLeft(int screenWidth) {
        return Math.max(10, screenWidth - 10 - TOGGLE_WIDTH);
    }

    public boolean isOverToggle(double x, double y) {
        int left = toggleLeft(screenWidth());
        return x >= left && x < left + TOGGLE_WIDTH && y >= TOGGLE_TOP && y < TOGGLE_TOP + TOGGLE_HEIGHT;
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetZ() {
        return offsetZ;
    }

    public double scale() {
        return scaleX;
    }

    public void pan(double deltaX, double deltaY) {
        ClientAccess.call(map, "moveCanvas", -deltaX / scaleX, -deltaY / scaleZ);
    }

    public double worldToScreenX(double worldX) {
        return worldX * scaleX + offsetX;
    }

    public double worldToScreenY(double worldZ) {
        return worldZ * scaleZ + offsetZ;
    }

    public double screenToWorldX(double screenX) {
        return (screenX - offsetX) / scaleX;
    }

    public double screenToWorldZ(double screenY) {
        return (screenY - offsetZ) / scaleZ;
    }

    public BlockPos screenToWorld(double screenX, double screenY) {
        int worldX = (int) Math.floor(screenToWorldX(screenX));
        int worldZ = (int) Math.floor(screenToWorldZ(screenY));
        return new BlockPos(worldX, resolveSurfaceY(worldX, worldZ), worldZ);
    }

    public int resolveSurfaceY(int worldX, int worldZ) {
        Level level = (Level) ClientAccess.field(minecraft(), "f_91073_");
        if (level == null || !level.m_7726_().m_5563_(worldX >> 4, worldZ >> 4)) {
            return 64;
        }
        return Math.max(level.m_6924_(Heightmap.Types.WORLD_SURFACE, worldX, worldZ), level.m_141937_());
    }
}
