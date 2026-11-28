package ua.empirecraft.clientdebug;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;

import static ua.empirecraft.clientdebug.ClientHooks.key;

final class DebugScreen extends Screen {
    private final Screen parent;
    private ClientHooks.Status shown;

    DebugScreen(Screen parent) {
        super(key("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shown = ClientHooks.status();
        int left = width / 2 - 100;
        var box = addRenderableWidget(new Checkbox(left, 44, 200, 20, key("enabled"), ClientHooks.running()) {
            @Override
            public void onPress() {
                super.onPress();
                ClientHooks.setEnabled(selected());
            }
        });
        box.active = !shown.key().equals("saving");
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    @Override
    public void tick() {
        if (shown != ClientHooks.status()) rebuildWidgets();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        int textWidth = Math.min(380, width - 32);
        int left = (width - textWidth) / 2;
        int y = 72;
        for (var line : font.split(key("status." + shown.key(), shown.detail()), textWidth)) {
            graphics.drawString(font, line, left, y, 0xFFFFFF, false);
            y += 11;
        }
        y += 6;
        for (var line : font.split(key("hint", ClientHooks.REPORTS), textWidth)) {
            if (y + 10 > height - 32) break;
            graphics.drawString(font, line, left, y, 0xAAAAAA, false);
            y += 11;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
