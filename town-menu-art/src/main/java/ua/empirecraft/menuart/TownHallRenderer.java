package ua.empirecraft.menuart;

import java.lang.reflect.Method;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge render hooks preserve vanilla input, tooltips, slot positions and GUI scaling.
 * Uses installed 1.20.1 SRG member names directly; no mixins or mapping download is required.
 */
@Mod.EventBusSubscriber(modid=MenuArt.ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class TownHallRenderer {
    private static final ResourceLocation PANEL = new ResourceLocation(MenuArt.ID, "textures/gui/town_hall.png");
    private static final ResourceLocation ATLAS = new ResourceLocation(MenuArt.ID, "textures/gui/controls.png");
    private TownHallRenderer() {}

    // The installed hybrid API omits two vanilla client interfaces. Cache only the
    // three screen accessors reflectively, so compiling needs no substitute SDK classes.
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try { return new Access(type.getMethod("m_6262_"), type.getMethod("getGuiLeft"), type.getMethod("getGuiTop")); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Unsupported container screen", e); }
        }
    };
    private record Access(Method menu, Method left, Method top) {
        Object get(Method method, Object screen) {
            try { return method.invoke(screen); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot read container screen", e); }
        }
    }

    private static boolean styled(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu chest && chest.m_39265_() == 6 // six rows
            && menu.f_38839_.size() == 90
            && SkinProtocol.anchor(menu.f_38839_.get(53).m_7993_().m_41783_());
    }

    @SubscribeEvent
    public static void background(ContainerScreenEvent.Render.Background event) {
        Object screen = event.getContainerScreen();
        var access = ACCESS.get(screen.getClass());
        var menu = (AbstractContainerMenu) access.get(access.menu, screen);
        if (!styled(menu)) return;
        // This event has absolute coordinates, before the slot-local pose translation.
        event.getGuiGraphics().m_280163_(PANEL, (Integer) access.get(access.left, screen), (Integer) access.get(access.top, screen), 0f, 0f, 176, 222, 256, 256);
    }

    @SubscribeEvent
    public static void foreground(ContainerScreenEvent.Render.Foreground event) {
        Object screen = event.getContainerScreen();
        var access = ACCESS.get(screen.getClass());
        var menu = (AbstractContainerMenu) access.get(access.menu, screen);
        if (!styled(menu)) return;
        var g = event.getGuiGraphics();
        String section = SkinProtocol.section(menu.f_38839_.get(53).m_7993_().m_41783_());
        int mouseX = event.getMouseX() - (Integer) access.get(access.left, screen);
        int mouseY = event.getMouseY() - (Integer) access.get(access.top, screen);
        // Foreground is local to the panel and runs before tooltips and carried items.
        g.m_280168_().m_85836_(); // pose.pushPose
        g.m_280168_().m_252880_(0, 0, 220); // above vanilla item models
        for (int n = 0; n < 54; n++) {
            var slot = menu.f_38839_.get(n);
            CompoundTag tag = slot.m_7993_().m_41783_();
            int icon = SkinProtocol.icon(tag);
            if (icon < 0 || !SkinProtocol.section(tag).equals(section)) continue;
            int x = slot.f_40220_, y = slot.f_40221_;
            // Opaque artwork covers only marked controls. Actual provision stacks stay native.
            g.m_280411_(ATLAS, x, y, 16, 16, (icon % 8) * 32f, (icon / 8) * 32f, 32, 32, 256, 256);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                g.m_280509_(x - 1, y - 1, x + 17, y, 0xfff3d995);
                g.m_280509_(x - 1, y + 16, x + 17, y + 17, 0xffa78b50);
                g.m_280509_(x - 1, y, x, y + 16, 0xffd8bb77);
                g.m_280509_(x + 16, y, x + 17, y + 16, 0xffd8bb77);
            }
        }
        g.m_280168_().m_85849_(); // pose.popPose
    }
}
