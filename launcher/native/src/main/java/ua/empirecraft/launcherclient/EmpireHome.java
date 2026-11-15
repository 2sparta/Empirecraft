package ua.empirecraft.launcherclient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public final class EmpireHome extends Screen {
    public EmpireHome() { super(Component.literal("Empirecraft")); }
    @Override protected void init() {
        int left = width / 2 - 110, top = height / 2;
        addRenderableWidget(Button.builder(Component.literal("Приєднатися до Empirecraft"), b -> {
            ServerData server = new ServerData("Empirecraft", LauncherClient.SERVER, false);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(LauncherClient.SERVER), server, false);
        }).bounds(left, top, 220, 24).build());
        addRenderableWidget(Button.builder(Component.translatable("menu.options"), b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))).bounds(left, top + 32, 106, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("menu.quit"), b -> minecraft.stop()).bounds(left + 114, top + 32, 106, 20).build());
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xff0b151b);
        g.hLine(width / 2 - 110, width / 2 + 110, height / 2 - 25, 0xffe4bc79);
        g.drawCenteredString(font, "EMPIRECRAFT", width / 2, height / 2 - 60, 0xe4bc79);
        g.drawCenteredString(font, LauncherClient.SERVER, width / 2, height / 2 - 40, 0x9aafb4);
        super.render(g, mouseX, mouseY, partial);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
