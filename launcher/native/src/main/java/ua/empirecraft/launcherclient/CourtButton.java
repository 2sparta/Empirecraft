package ua.empirecraft.launcherclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** The same focus, narration and keyboard behavior as vanilla buttons. */
final class CourtButton extends Button {
    private final boolean primary;
    CourtButton(int x, int y, int width, int height, Component label, OnPress action, boolean primary) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
        this.primary = primary;
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean focus = isHoveredOrFocused();
        int x = getX(), y = getY();
        int border = focus ? 0xffedcf91 : primary ? 0xffab8953 : 0xff5c5447;
        g.fill(x, y, x + width, y + height, border);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1,
            focus ? 0xee3f4037 : primary ? 0xed363729 : 0xd91a2021);
        g.hLine(x + 2, x + width - 3, y + 2, focus ? 0xff716b50 : 0xff47483b);
        var font = Minecraft.getInstance().font;
        // Long translations stay inside the frame on the smallest supported GUI.
        float fit = Math.min(1f, (width - 12f) / Math.max(1, font.width(getMessage())));
        g.pose().pushPose();
        g.pose().translate(x + width / 2f, y + (height - 8 * fit) / 2f, 0);
        g.pose().scale(fit, fit, 1);
        g.drawCenteredString(font, getMessage(), 0, 0, active ? (focus ? 0xffebdaaf : 0xffd4c6aa) : 0xff77756d);
        g.pose().popPose();
    }
}
