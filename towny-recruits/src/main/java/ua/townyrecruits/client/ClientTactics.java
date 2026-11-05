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
    private static double width = 1;
    private static final List<Waypoint> waypoints = new ArrayList<>();
    private static final List<Preview> confirmed = new ArrayList<>();
    private static TacticalOrder confirmedOrder;
    private static int sequence, requested, ticks, lastRequest = -20, inspectedAt = -20;
    private static TacticalOrder displayedOrder;
    private static TacticalOrder pendingOrder;
    private static Preview preview;
    private static Vec3 anchor;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    private ClientTactics() {}
    public static boolean active() { return active; }
    private static List<UUID> selectedGroups() {
        return ClientManager.groups.stream().filter(g -> !g.isDisabled() && !g.removed && g.getCount() > 0)
            .map(RecruitsGroup::getUUID).limit(TacticalOrder.MAX_GROUPS).toList();
    }
    public static void issue(CommandScreen screen, Mode selected) {
        var ids = selectedGroups(); if (ids.isEmpty()) return;
        TacticalOrder order = new TacticalOrder(selected, Formation.KEEP, ids, screen.player.m_20182_(), 0, false,
            width, TacticalCategory.speed(), TacticalCategory.leash(), TacticalCategory.distance(), List.of());
        TacticalNetwork.CHANNEL.sendToServer(new TacticalNetwork.Request(++sequence, 1, order));
    }
    public static void begin(CommandScreen screen, Mode selected, Formation formation, boolean dense) {
        if (selected.action()) { issue(screen, selected); return; }
        groups = selectedGroups();
        if (groups.isEmpty()) return;
        Minecraft mc = Minecraft.m_91087_();
        mode = selected; shape = formation; tight = dense;
        waypoints.clear(); confirmed.clear(); confirmedOrder = null;
        yaw = TacticalMath.wrap(screen.player.m_146908_()); active = true; invalidate();
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
            || Math.abs(TacticalMath.wrap(message.yaw() - yaw)) > 1 || message.anchor().m_82554_(anchor) > 0.6
            || message.formation() != shape || Math.abs(message.width() - width) > 0.001
            || (mode == Mode.ROUTE && message.points() != waypoints.size() + 1)) return;
        preview = message;
        displayedOrder = pendingOrder;
        PreviewRenderer.rebuild(message, confirmed);
    }
    private static void invalidate() {
        preview = null; displayedOrder = null; pendingOrder = null; PreviewRenderer.clear(); lastRequest = ticks - 10;
        if (active && mode == Mode.ROUTE && !confirmed.isEmpty())
            PreviewRenderer.rebuild(confirmed.get(confirmed.size() - 1), confirmed.subList(0, confirmed.size() - 1));
    }
    public static void cancel() {
        active = false; preview = null; displayedOrder = null; pendingOrder = null; anchor = null; validHit = false;
        PreviewRenderer.clear();
        waypoints.clear(); confirmed.clear(); confirmedOrder = null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.m_91087_(); ticks++;
        if (mc.f_91074_ == null || mc.f_91073_ == null) {
            cancel(); hasBombs = false; inspectedGroups = List.of(); bombButtons.clear(); return;
        }
        if (mc.f_91080_ instanceof CommandScreen) {
            inspect(ClientManager.groups);
            for (AbstractWidget button : bombButtons) button.f_93624_ = hasBombs && TacticalCategory.bombPage();
        }
        if (!active) return;
        if (!dimension.equals(mc.f_91073_.m_46472_()) || mc.f_91080_ != null || !mc.f_91074_.m_6084_()) { cancel(); return; }
        Vec3 eye = mc.f_91074_.m_20299_(1);
        Vec3 far = eye.m_82549_(mc.f_91074_.m_20252_(1).m_82490_(TacticalOrder.COMMAND_RANGE));
        var hit = mc.f_91073_.m_45547_(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.f_91074_));
        validHit = hit.m_6662_() == HitResult.Type.BLOCK;
        anchor = validHit ? hit.m_82450_() : far;
        if (preview != null && (preview.anchor().m_82554_(anchor) > 0.6 || Math.abs(TacticalMath.wrap(preview.yaw() - yaw)) > 1)) {
            invalidate();
        }
        if (validHit && ticks - lastRequest >= 10 && (mode != Mode.ROUTE || waypoints.size() < TacticalOrder.MAX_WAYPOINTS)) {
            lastRequest = ticks; requested = ++sequence;
            List<Waypoint> route = new ArrayList<>(waypoints);
            if (mode == Mode.ROUTE) route.add(new Waypoint(anchor, yaw, shape, width));
            pendingOrder = new TacticalOrder(mode, shape, groups, anchor, yaw, tight, width,
                TacticalCategory.speed(), TacticalCategory.leash(), TacticalCategory.distance(), List.copyOf(route));
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
            if (mode == Mode.ROUTE) {
                if (waypoints.size() >= TacticalOrder.MAX_WAYPOINTS) return;
                confirmedOrder = displayedOrder;
                waypoints.add(new Waypoint(displayedOrder.anchor(), yaw, shape, width)); confirmed.add(preview); invalidate();
            } else {
                TacticalNetwork.CHANNEL.sendToServer(new TacticalNetwork.Request(++sequence, 1, displayedOrder)); cancel();
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!active || Minecraft.m_91087_().f_91080_ != null) return;
        event.setCanceled(true);
        width = Math.max(0.6, Math.min(3, Math.round((width + Math.signum(event.getScrollDelta()) * 0.1) * 10) / 10.0));
        invalidate();
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!active || event.getAction() == GLFW.GLFW_RELEASE) return;
        if (event.getKey() == GLFW.GLFW_KEY_LEFT || event.getKey() == GLFW.GLFW_KEY_RIGHT) {
            yaw = TacticalMath.wrap(yaw + (event.getKey() == GLFW.GLFW_KEY_LEFT ? -15 : 15));
            invalidate();
        } else if (mode == Mode.ROUTE && (event.getKey() == GLFW.GLFW_KEY_UP || event.getKey() == GLFW.GLFW_KEY_DOWN)) {
            shape = Formation.values()[Math.floorMod(shape.ordinal() + (event.getKey() == GLFW.GLFW_KEY_UP ? 1 : -1), Formation.values().length)];
            invalidate();
        } else if (mode == Mode.ROUTE && event.getKey() == GLFW.GLFW_KEY_BACKSPACE && !waypoints.isEmpty()) {
            waypoints.remove(waypoints.size() - 1); confirmed.remove(confirmed.size() - 1);
            confirmedOrder = confirmed.isEmpty() ? null : orderForConfirmed(); invalidate();
        } else if (mode == Mode.ROUTE && (event.getKey() == GLFW.GLFW_KEY_ENTER || event.getKey() == GLFW.GLFW_KEY_KP_ENTER) && confirmedOrder != null) {
            TacticalNetwork.CHANNEL.sendToServer(new TacticalNetwork.Request(++sequence, 1, confirmedOrder)); cancel();
        } else if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) cancel();
    }
    private static TacticalOrder orderForConfirmed() {
        Waypoint last = waypoints.get(waypoints.size() - 1);
        return new TacticalOrder(mode, last.formation(), groups, last.anchor(), last.yaw(), tight, last.width(),
            TacticalCategory.speed(), TacticalCategory.leash(), TacticalCategory.distance(), List.copyOf(waypoints));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (active) { event.setCanceled(true); event.setSwingHand(false); }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.m_91087_();
        Component text = Component.m_237115_(mode == Mode.ROUTE ? "tactics.townyrecruits.route_controls" : "tactics.townyrecruits.controls");
        Preview shown = preview == null && mode == Mode.ROUTE && waypoints.size() == TacticalOrder.MAX_WAYPOINTS ? confirmed.get(confirmed.size() - 1) : preview;
        long valid = shown == null ? 0 : shown.validCount();
        String status = shown == null ? "tactics.townyrecruits.waiting" : "tactics.townyrecruits.count";
        var gui = event.getGuiGraphics();
        int y = mc.m_91268_().m_85446_() - 90;
        gui.m_280430_(mc.f_91062_, text, 12, y, 0xffffff);
        gui.m_280430_(mc.f_91062_, Component.m_237115_(mode == Mode.ROUTE ? "tactics.townyrecruits.route_help" : "tactics.townyrecruits.cancel_controls"), 12, y + 12, 0xdddddd);
        gui.m_280430_(mc.f_91062_, Component.m_237110_(mode == Mode.ROUTE ? "tactics.townyrecruits.route_settings" : "tactics.townyrecruits.settings", TacticalCategory.label("formation", shape.name()),
            String.format(Locale.ROOT, "%.1f", width), TacticalCategory.label("speed", TacticalCategory.speed().name()), waypoints.size()), 12, y + 24, 0xffffff);
        gui.m_280430_(mc.f_91062_, Component.m_237110_(status, valid, shown == null ? 0 : shown.markers().size()), 12, y + 36, valid > 0 ? 0x55ff77 : 0xff6666);
        if (shown != null) shown.markers().stream().filter(m -> !m.valid()).findFirst().ifPresent(m ->
            gui.m_280430_(mc.f_91062_, TacticalCategory.label("failure", m.failure().name()), 12, y + 48, 0xff9999));
    }
}
