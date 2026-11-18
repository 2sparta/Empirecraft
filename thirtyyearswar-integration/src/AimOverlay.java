package ua.empirecraft.thirtyyearswar;

import com.magistuarmory.thirtyyearswar.item.GunItem;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "thirtyyearswar", value = Dist.CLIENT)
public final class AimOverlay {
    private static final DecimalFormat DEGREES = new DecimalFormat("0.0'°'", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final int PALE = 0xffece1c1;
    private static final int SHADOW = 0xbf231d16;
    private static final int[][] RING = {
        {-1,-5,2,-4}, {-3,-4,-1,-3}, {2,-4,4,-3}, {-4,-3,-3,-1}, {4,-3,5,-1},
        {-5,-1,-4,2}, {5,-1,6,2}, {-4,2,-3,4}, {4,2,5,4},
        {-3,4,-1,5}, {2,4,4,5}, {-1,5,2,6}
    };

    private AimOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Pre event) {
        if (!VanillaGuiOverlay.CROSSHAIR.id().equals(event.getOverlay().id())) return;
        Minecraft game = Minecraft.m_91087_();
        var player = game.f_91074_;
        if (player == null || player.m_5833_() || player.m_6117_() || game.f_91066_.f_92062_
                || !game.f_91066_.m_92176_().m_90612_()) return;
        var gun = player.m_21205_();
        if (!(gun.m_41720_() instanceof GunItem) || !GunItem.isCharged(gun)
                || player.m_21324_(event.getPartialTick()) >= 0.001f) return;

        event.setCanceled(true);
        GuiGraphics graphics = event.getGuiGraphics();
        int x = event.getWindow().m_85445_() / 2;
        int y = event.getWindow().m_85446_() / 2;
        for (int[] p : RING) {
            graphics.m_280509_(x+p[0]+1, y+p[1]+1, x+p[2]+1, y+p[3]+1, SHADOW);
        }
        for (int[] p : RING) {
            graphics.m_280509_(x+p[0], y+p[1], x+p[2], y+p[3], PALE);
        }
        float elevation = -player.m_5686_(event.getPartialTick());
        graphics.m_280614_(game.f_91062_, Component.m_237113_(DEGREES.format(elevation)), x+10, y-4, PALE, true);
    }
}
