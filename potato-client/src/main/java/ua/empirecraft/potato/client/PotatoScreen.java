package ua.empirecraft.potato.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import ua.empirecraft.potato.PotatoState;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;

public final class PotatoScreen extends Screen {
    private final Screen parent;
    private boolean failed;
    private int scroll, lineCount, visibleLines;
    public PotatoScreen(Screen parent) { super(Component.translatable("potato.title")); this.parent = parent; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable(PotatoState.enabled() ? "potato.disable" : "potato.enable"), button -> {
            try {
                button.active = false;
                PotatoClient.setEnabled(!PotatoState.enabled()).whenComplete((ignored, error) -> minecraft.execute(() -> {
                    failed = error != null; rebuildWidgets();
                }));
            } catch (Exception error) { failed = true; button.active = true; }
        }).bounds(width / 2 - 110, height - 74, 220, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose()).bounds(width / 2 - 55, height - 45, 110, 20).build());
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, lineCount-visibleLines), scroll+(delta>0?-3:3))); return true;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xffe6c478);
        int x = Math.max(15, (width - 450) / 2), wrap = Math.min(450, width - 30);
        var lines = new ArrayList<FormattedCharSequence>();
        for (String key : new String[]{PotatoState.enabled() ? "potato.on" : "potato.off", "potato.effects", "potato.preserved", "potato.reversible"}) {
            lines.addAll(font.split(Component.translatable(key), wrap)); lines.add(FormattedCharSequence.EMPTY);
        }
        lineCount = lines.size(); visibleLines = Math.max(1,(height-160)/12);
        scroll = Math.min(scroll,Math.max(0,lineCount-visibleLines));
        graphics.enableScissor(x,50,x+wrap,Math.max(51,height-106));
        for(int i=scroll;i<Math.min(lineCount,scroll+visibleLines);i++) graphics.drawString(font,lines.get(i),x,55+(i-scroll)*12,0xffe0d4bd,false);
        graphics.disableScissor();
        if (failed || PotatoState.error() != null) graphics.drawCenteredString(font, Component.translatable("potato.error"), width / 2, height - 96, 0xffff7777);
        super.render(graphics, mouseX, mouseY, partial);
    }
}
