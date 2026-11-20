package ua.empirecraft.launcherclient;

import com.quickskin.mod.client.gui.screen.PlayerCapeMenuScreen;
import com.quickskin.mod.client.gui.screen.PlayerSkinMenuScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class EmpireHome extends Screen {
    private static final ResourceLocation HALL = new ResourceLocation("empirecraft_launcher_client", "textures/gui/castle_hall.png");
    private HomeLayout layout;
    private final HomeAppearance appearance = new HomeAppearance();
    private float rotation = 18;
    private boolean dragging;
    private int ticks;
    public EmpireHome() { super(Component.literal("Empirecraft")); }
    private Component text(String key) { return Component.translatable("empirecraft.home." + key); }
    @Override protected void init() {
        layout = HomeLayout.of(width, height, HomePreferences.closeUp());
        dragging = false;
        appearance.invalidate();
        appearance.refresh();
        com.mojang.logging.LogUtils.getLogger().info("Empirecraft home screen ready: {}x{}", width, height);
        addRenderableWidget(new CourtButton(layout.buttonX(), layout.buttonY(0), layout.buttonWidth(), layout.buttonHeight(), text("play"), b -> {
            LauncherClient.requestConnection();
            ServerData server = new ServerData("Empirecraft", LauncherClient.SERVER, false);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(LauncherClient.SERVER), server, false);
        }, true));
        addRenderableWidget(new CourtButton(layout.buttonX(), layout.buttonY(1), layout.buttonWidth(), layout.buttonHeight(), Component.translatable("menu.options"), b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)), false));
        addRenderableWidget(new CourtButton(layout.buttonX(), layout.buttonY(2), layout.buttonWidth(), layout.buttonHeight(), Component.translatable("menu.quit"), b -> minecraft.stop(), false));
        int aw = layout.appearanceWidth(), cx = layout.characterX(), ay = layout.appearanceY() - 25;
        var skin = addRenderableWidget(new CourtButton(cx - aw - 3, ay, aw, 22, text("skin"), b -> minecraft.setScreen(new PlayerSkinMenuScreen(this)), false));
        skin.setTooltip(Tooltip.create(text("skin_tip")));
        var cape = addRenderableWidget(new CourtButton(cx + 3, ay, aw, 22, text("cape"), b -> minecraft.setScreen(new PlayerCapeMenuScreen(this)), false));
        cape.setTooltip(Tooltip.create(text("cape_tip")));
        var framing = addRenderableWidget(new CourtButton(cx-aw-3,layout.appearanceY(),aw,22,text(HomePreferences.closeUp()?"waist":"full"),b->{HomePreferences.toggleClose();rebuildWidgets();},false));
        framing.setTooltip(Tooltip.create(text("framing_tip")));
        var equipment = addRenderableWidget(new CourtButton(cx+3,layout.appearanceY(),aw,22,text(HomePreferences.armor()?"armor":"skin_only"),b->{HomePreferences.toggleArmor();b.setMessage(text(HomePreferences.armor()?"armor":"skin_only"));},false));
        equipment.setTooltip(Tooltip.create(text("armor_tip")));
        var left = addRenderableWidget(new CourtButton(layout.stageLeft(), layout.clipBottom() - 20, 20, 20, Component.literal("<"), b -> rotate(-30), false));
        left.setTooltip(Tooltip.create(text("rotate_left")));
        var right = addRenderableWidget(new CourtButton(layout.stageRight() - 20, layout.clipBottom() - 20, 20, 20, Component.literal(">"), b -> rotate(30), false));
        right.setTooltip(Tooltip.create(text("rotate_right")));
    }
    private void rotate(float delta) { rotation = (rotation + delta) % 360; }
    @Override public void tick() {
        if (++ticks % 20 == 0) appearance.refresh();
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // A single static texture, cover cropped without stretching; no video or world renderer.
        float cover = Math.max(width / 1536f, height / 1024f);
        float visibleW = width / cover, visibleH = height / cover;
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.blit(HALL, 0, 0, width, height, (1536 - visibleW) / 2, (1024 - visibleH) / 2,
            (int) visibleW, (int) visibleH, 1536, 1024);
        g.fillGradient(0, 0, width, height, 0x10050907, 0xb0070b0d);
        int m = layout.margin(), right = m + layout.panelWidth();
        g.fill(m, m, right, height - m, 0xe30d1518);
        g.hLine(m, right - 1, m, 0xffa58752);
        g.hLine(m, right - 1, height - m - 1, 0xff6d5a3d);
        g.vLine(right - 1, m, height - m - 1, 0xff66583e);
        int center = m + layout.panelWidth() / 2;
        // Small heraldic diamond, drawn rather than another texture or a font glyph.
        int crestY = m + 19;
        for (int row = -6; row <= 6; row++) {
            int half = 6 - Math.abs(row);
            g.hLine(center - half, center + half, crestY + row, 0xffc9aa70);
        }
        scaledLabel(g, Component.literal("EMPIRECRAFT"), center, m + 37, layout.panelWidth() - 22, 1.35f, 0xe8d4a7);
        scaledLabel(g, text("subtitle"), center, m + 58, layout.panelWidth() - 22, 1f, 0x9f9e8c);
        g.hLine(layout.buttonX() + 12, layout.buttonX() + layout.buttonWidth() - 12, layout.firstButtonY() - 12, 0xff66583e);
        if (height >= 320) {
            g.drawCenteredString(font, text("welcome"), center, layout.firstButtonY() - 32, 0xbdb9a8);
            g.drawCenteredString(font, LauncherClient.SERVER, center, height - m - 25, 0x969b93);
        }
        g.enableScissor(layout.stageLeft(),layout.clipTop(),layout.stageRight(),layout.clipBottom());
        try {
        // Soft ground contact keeps the character visually anchored to the hall floor.
        if (!layout.closeUp()) for (int row = -5; row <= 5; row++) {
            int half = (int) (Math.sqrt(1 - row * row / 36d) * layout.scale() * .54);
            g.hLine(layout.characterX() - half, layout.characterX() + half, layout.feetY() + row, 0x26000000);
        }
        appearance.render(g, layout, rotation);
        g.flush();
        } finally { g.disableScissor(); }
        scaledLabel(g, Component.literal(minecraft.getUser().getName()), layout.characterX(), height - 82,
            layout.stageRight() - layout.stageLeft(), 1f, 0xe8d4a7);
        // Keep the hint short; arrows are also keyboard and screen-reader accessible.
        if (height >= 300) scaledLabel(g, text(HomePreferences.armor()?(HomeEquipment.known()?"armor_cached":"armor_empty"):"rotate_hint"), layout.characterX(), height - 71, layout.stageRight() - layout.stageLeft(), 1f, 0xa5a897);
        super.render(g, mouseX, mouseY, partial);
    }
    private void scaledLabel(GuiGraphics g, Component text, int x, int y, int maxWidth, float preferred, int color) {
        float scale = Math.min(preferred, maxWidth / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && layout.onCharacter(x, y)) { dragging = true; return true; }
        if (button == 1 && layout.onCharacter(x, y)) { rotation = 18; return true; }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) { rotate((float) dx * 1.5f); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        dragging = false;
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == 82 /* GLFW_KEY_R */) { rotation = 18; return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
