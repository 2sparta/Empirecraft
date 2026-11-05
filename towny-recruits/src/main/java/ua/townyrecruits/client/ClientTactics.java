package ua.townyrecruits.client;

import com.talhanation.recruits.client.gui.CommandScreen;
import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.world.RecruitsGroup;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ua.townyrecruits.tactics.*;
import ua.townyrecruits.tactics.TacticalOrder.*;
import ua.townyrecruits.tactics.TacticalPlanner.Preview;

@Mod.EventBusSubscriber(modid = "townyrecruits", value = Dist.CLIENT)
public final class ClientTactics {
    public static final List<AbstractWidget> bombButtons = new ArrayList<>();
    public static boolean hasBombs;
    private static boolean active, validHit;
    private static Mode mode;
    private static Formation shape;
    private static List<UUID> groups = List.of(), inspectedGroups = List.of();
    private static float yaw;
    private static boolean tight;
    private static int sequence, requested, ticks, lastRequest = -20, inspectedAt = -20;
    private static TacticalOrder displayedOrder;
    private static TacticalOrder pendingOrder;
    private static Preview preview;
    private static Vec3 anchor;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    private ClientTactics() {}
    public static boolean active() { return active; }
    public static void begin(CommandScreen screen, Mode selected, Formation formation, boolean dense) {
        groups = ClientManager.groups.stream().filter(g -> !g.isDisabled() && !g.removed && g.getCount() > 0).map(RecruitsGroup::getUUID).limit(TacticalOrder.MAX_GROUPS).toList();
        if (groups.isEmpty()) return;
        Minecraft mc = Minecraft.m_91087_();
        mode = selected; shape = formation; tight = dense;
        yaw = TacticalMath.wrap(screen.player.m_146908_()); active = true; preview = null; displayedOrder = null;
        dimension = screen.player.m_20193_().m_46472_(); lastRequest = ticks - 10;
        screen.player.m_6915_();
    }
    public static void inspect(List<RecruitsGroup> selected) {
        Minecraft mc = Minecraft.m_91087_(); if (mc.f_91074_ == null) return;
        List<UUID> ids = selected.stream().filter(g -> !g.isDisabled()).map(RecruitsGroup::getUUID).limit(TacticalOrder.MAX_GROUPS).toList();
        if (!ids.equals(inspectedGroups)) { inspectedGroups = ids; hasBombs = false; }
        if (ids.isEmpty() || ticks - inspectedAt < 10) return;
        inspectedAt = ticks;
        TacticalOrder order = new TacticalOrder(Mode.MOVE, Formation.KEEP, ids, mc.f_91074_.m_20182_(), 0, false);
        TacticalNetwork.CHANNEL.sendToServer(new TacticalNetwork.Request(0, 2, order));
    }
    public static void receive(Preview message) {
        if (message.sequence() == 0) { hasBombs = message.hasBombs(); return; }
        if (!active || message.sequence() != requested || pendingOrder == null || anchor == null || !validHit
            || Math.abs(TacticalMath.wrap(message.yaw() - yaw)) > 1 || message.anchor().m_82554_(anchor) > 0.6) return;
        preview = message;
        displayedOrder = pendingOrder;
        PreviewRenderer.rebuild(message);
    }
    public static void cancel() {
        active = false; preview = null; displayedOrder = null; pendingOrder = null; anchor = null; validHit = false;
        PreviewRenderer.clear();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.m_91087_(); ticks++;
        if (mc.f_91074_ == null || mc.f_91073_ == null) {
            cancel(); hasBombs = false; inspectedGroups = List.of(); bombButtons.clear(); return;
        }
        if (mc.f_91080_ instanceof CommandScreen) {
            inspect(ClientManager.groups);
            for (AbstractWidget button : bombButtons) button.f_93624_ = hasBombs;
        }
        if (!active) return;
        if (!dimension.equals(mc.f_91073_.m_46472_()) || mc.f_91080_ != null || !mc.f_91074_.m_6084_()) { cancel(); return; }
        Vec3 eye = mc.f_91074_.m_20299_(1);
        Vec3 far = eye.m_82549_(mc.f_91074_.m_20252_(1).m_82490_(TacticalOrder.COMMAND_RANGE));
        var hit = mc.f_91073_.m_45547_(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.f_91074_));
        validHit = hit.m_6662_() == HitResult.Type.BLOCK;
        anchor = validHit ? hit.m_82450_() : far;
        if (preview != null && (preview.anchor().m_82554_(anchor) > 0.6 || Math.abs(TacticalMath.wrap(preview.yaw() - yaw)) > 1)) {
            preview = null; displayedOrder = null; PreviewRenderer.clear();
        }
        if (validHit && ticks - lastRequest >= 10) {
            lastRequest = ticks; requested = ++sequence;
            pendingOrder = new TacticalOrder(mode, shape, groups, anchor, yaw, tight);
            TacticalNetwork.CHANNEL.sendToServer(new TacticalNetwork.Request(requested, 0, pendingOrder));
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre event) {
        if (!active || Minecraft.m_91087_().f_91080_ != null) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        event.setCanceled(true);
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { cancel(); return; }
        if (validHit && preview != null && displayedOrder != null) {
            TacticalNetwork.CHANNEL.sendToServer(new TacticalNetwork.Request(++sequence, 1, displayedOrder));
            cancel();
        }
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!active || event.getAction() == GLFW.GLFW_RELEASE) return;
        if (event.getKey() == GLFW.GLFW_KEY_LEFT || event.getKey() == GLFW.GLFW_KEY_RIGHT) {
            yaw = TacticalMath.wrap(yaw + (event.getKey() == GLFW.GLFW_KEY_LEFT ? -15 : 15));
            preview = null; displayedOrder = null; PreviewRenderer.clear();
        } else if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) cancel();
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (active) { event.setCanceled(true); event.setSwingHand(false); }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.m_91087_();
        Component text = Component.m_237115_("tactics.townyrecruits.controls");
        long valid = preview == null ? 0 : preview.validCount();
        String status = preview == null ? "tactics.townyrecruits.waiting" : "tactics.townyrecruits.count";
        var gui = event.getGuiGraphics();
        int y = mc.m_91268_().m_85446_() - 78;
        gui.m_280430_(mc.f_91062_, text, 12, y, 0xffffff);
        gui.m_280430_(mc.f_91062_, Component.m_237110_(status, valid, preview == null ? 0 : preview.markers().size()), 12, y + 12, valid > 0 ? 0x55ff77 : 0xff6666);
        if (preview != null) preview.markers().stream().filter(m -> !m.valid()).findFirst().ifPresent(m ->
            gui.m_280430_(mc.f_91062_, TacticalCategory.label("failure", m.failure().name()), 12, y + 24, 0xff9999));
    }
}
