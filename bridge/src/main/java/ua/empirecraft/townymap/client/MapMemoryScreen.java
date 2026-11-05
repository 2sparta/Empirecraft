package ua.empirecraft.townymap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import ua.empirecraft.townymap.MapHistoryNetwork;
import java.util.*;

/** A native historical card. Returning to the map never performs a live estate lookup. */
public final class MapMemoryScreen extends Screen {
    private final Screen parent;
    private final String request = UUID.randomUUID().toString(), dimension;
    private final int blockX, blockZ;
    private MapHistoryNetwork.Reply reply;
    private int scroll, lineCount, visibleLines, waiting;

    private MapMemoryScreen(Screen parent, String dimension, int x, int z) {
        super(Component.translatable("empirecraft.map.memory_title"));
        this.parent = parent; this.dimension = dimension; this.blockX = x; this.blockZ = z;
    }
    public static void open(String dimension, int x, int z) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null || !MapHistoryNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) return;
        var screen = new MapMemoryScreen(mc.screen, dimension, x, z); mc.setScreen(screen);
        MapHistoryNetwork.CHANNEL.sendToServer(new MapHistoryNetwork.Query(screen.request, dimension, x, z));
    }
    public static void receive(MapHistoryNetwork.Reply reply) {
        if (Minecraft.getInstance().screen instanceof MapMemoryScreen screen && screen.request.equals(reply.request())) {
            screen.reply = reply; screen.scroll = 0;
        }
    }
    @Override protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 55, height - 36, 110, 20).build());
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { if (reply == null) waiting++; }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, lineCount - visibleLines), scroll + (delta > 0 ? -3 : 3))); return true;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        int panelWidth = Math.min(560, width - 24), left = (width - panelWidth) / 2;
        graphics.fill(left, 14, left + panelWidth, height - 45, 0xf0271c12);
        graphics.fill(left, 14, left + panelWidth, 16, 0xffbc9258);
        graphics.drawCenteredString(font, title, width / 2, 26, 0xfff2cf8f);
        graphics.drawCenteredString(font, dimension + " · " + blockX + ", " + blockZ, width / 2, 43, 0xffcbb38d);
        var lines = new ArrayList<FormattedCharSequence>();
        String status = reply == null ? waiting < 100 ? "loading" : "unavailable" : switch (reply.status()) {
            case UNKNOWN -> "unknown"; case EMPTY -> "remembered_wilderness"; case KNOWN -> "remembered";
        };
        int wrapWidth = Math.max(16, panelWidth - 30);
        lines.addAll(font.split(Component.translatable("empirecraft.map." + status), wrapWidth));
        if (reply != null) for (var entry : reply.entries()) {
            lines.add(FormattedCharSequence.EMPTY);
            lines.addAll(font.split(Component.literal(entry.name()).withStyle(net.minecraft.ChatFormatting.GOLD), wrapWidth));
            lines.addAll(font.split(Component.literal(entry.detail()), wrapWidth));
        }
        lineCount = lines.size(); visibleLines = Math.max(1, (height - 122) / 12);
        scroll = Math.min(scroll, Math.max(0, lineCount - visibleLines));
        graphics.enableScissor(left + 10, 65, left + panelWidth - 10, Math.max(66, height - 55));
        for (int i = scroll; i < Math.min(lineCount, scroll + visibleLines); i++)
            graphics.drawString(font, lines.get(i), left + 15, 67 + (i - scroll) * 12, 0xffe0cdb0, false);
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partial);
    }
}
