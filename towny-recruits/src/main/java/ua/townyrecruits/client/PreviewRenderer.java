package ua.townyrecruits.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import ua.townyrecruits.tactics.*;
import ua.townyrecruits.tactics.TacticalPlanner.*;

/** Cached, depth-tested ground mesh. Every corner is projected onto the local collision shape. */
@Mod.EventBusSubscriber(modid = "townyrecruits", value = Dist.CLIENT)
public final class PreviewRenderer {
    private record Patch(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {}
    private static final List<Patch> PATCHES = new ArrayList<>();
    private static final int GREEN = 0x6633ee66, YELLOW = 0x44ffdd33, RED = 0x88ff3333;
    private static final int MAX_PATCHES = 6000;
    private PreviewRenderer() {}
    public static void clear() { PATCHES.clear(); }
    public static void rebuild(Preview preview) {
        rebuild(preview, List.of());
    }
    public static void rebuild(Preview preview, List<Preview> confirmed) {
        PATCHES.clear(); Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null) return;
        boolean any = preview.validCount() > 0;
        if (preview.mode().bomb()) {
            if (preview.mode() == TacticalOrder.Mode.BOMB_AREA) disk(preview.anchor(), TacticalOrder.SCATTER, any ? GREEN : RED);
            else bombFront(preview);
        } else {
            // Reserve the mesh budget for all final slots before adding long movement lanes.
            for (Marker marker : preview.markers()) {
                rectangle(marker.target(), marker.footprint(), marker.footprint(), marker.yaw(), marker.valid() ? GREEN : RED);
                arrow(marker.target(), marker.yaw(), marker.valid() ? GREEN : RED);
            }
            for (Preview point : confirmed) {
                if (PATCHES.size() >= 4500) break;
                for (Marker marker : point.markers()) {
                    if (PATCHES.size() >= 4500) break;
                    outline(marker.target(), marker.footprint(), marker.footprint(), marker.yaw(), marker.valid() ? GREEN : RED);
                }
                arrow(point.anchor(), point.yaw(), point.validCount() > 0 ? GREEN : RED);
            }
            // Movement lanes are yellow; an unusable soldier's route is red.
            for (Marker marker : preview.markers()) {
                Vec3 prev = marker.from();
                List<Vec3> route = marker.route().isEmpty() ? List.of(marker.target()) : marker.route();
                for (Vec3 point : route) {
                    if (PATCHES.size() >= MAX_PATCHES) break;
                    strip(prev, point, 0.35, marker.valid() ? YELLOW : RED); prev = point;
                }
            }
            if (!preview.markers().isEmpty()) {
                // One enclosing rectangle makes the regiment's footprint and chosen orientation readable.
                double minX = Double.MAX_VALUE, maxX = -minX, minZ = minX, maxZ = -minX;
                for (Marker marker : preview.markers()) {
                    double[] p = TacticalMath.rotate(marker.target().f_82479_ - preview.anchor().f_82479_,
                        marker.target().f_82481_ - preview.anchor().f_82481_, -preview.yaw());
                    minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                    minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]);
                }
                double[] offset = TacticalMath.rotate((minX + maxX) / 2, (minZ + maxZ) / 2, preview.yaw());
                outline(preview.anchor().m_82520_(offset[0], 0, offset[1]), maxX - minX + 1.1, maxZ - minZ + 1.1,
                    preview.yaw(), any ? GREEN : RED);
            }
        }
        if (preview.markers().isEmpty()) rectangle(preview.anchor(), 2, 2, preview.yaw(), RED);
    }
    private static void bombFront(Preview preview) {
        double[] right = TacticalMath.rotate(1, 0, preview.yaw());
        var sorted = preview.markers().stream().sorted(Comparator.comparingDouble(m ->
            m.target().f_82479_ * right[0] + m.target().f_82481_ * right[1])).toList();
        for (int i = 0; i < sorted.size(); i++) {
            Marker m = sorted.get(i);
            double left = i == 0 ? TacticalOrder.SCATTER : m.target().m_82554_(sorted.get(i - 1).target()) / 2;
            double next = i + 1 == sorted.size() ? TacticalOrder.SCATTER : m.target().m_82554_(sorted.get(i + 1).target()) / 2;
            Vec3 center = m.target().m_82520_(right[0] * (next - left) / 2, 0, right[1] * (next - left) / 2);
            rectangle(center, Math.max(0.1, left + next), TacticalOrder.SCATTER * 2, preview.yaw(), m.valid() ? GREEN : RED);
        }
    }
    private static Vec3 project(Vec3 p) {
        Minecraft mc = Minecraft.m_91087_();
        Vec3 ground = TacticalTerrain.surface(mc.f_91073_, p.f_82479_, p.f_82481_, p.f_82480_, mc.f_91074_);
        return ground == null ? null : ground.m_82520_(0, 0.025, 0);
    }
    private static void patch(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        if (PATCHES.size() >= MAX_PATCHES) return;
        a = project(a); b = project(b); c = project(c); d = project(d);
        if (a == null || b == null || c == null || d == null) return;
        double lo = Math.min(Math.min(a.f_82480_, b.f_82480_), Math.min(c.f_82480_, d.f_82480_));
        double hi = Math.max(Math.max(a.f_82480_, b.f_82480_), Math.max(c.f_82480_, d.f_82480_));
        // Split surfaces at cliffs rather than drawing a floating sheet between different elevations.
        if (hi - lo > 1.1) return;
        PATCHES.add(new Patch(a, b, c, d, color));
    }
    private static Vec3 local(Vec3 center, double x, double z, float yaw) {
        double[] p = TacticalMath.rotate(x, z, yaw); return center.m_82520_(p[0], 0, p[1]);
    }
    private static void rectangle(Vec3 center, double width, double depth, float yaw, int color) {
        int nx = Math.max(1, (int)Math.ceil(width / 0.4)), nz = Math.max(1, (int)Math.ceil(depth / 0.4));
        for (int x = 0; x < nx; x++) for (int z = 0; z < nz; z++) {
            double x0 = width * (x / (double)nx - 0.5), x1 = width * ((x + 1.0) / nx - 0.5);
            double z0 = depth * (z / (double)nz - 0.5), z1 = depth * ((z + 1.0) / nz - 0.5);
            patch(local(center, x0, z0, yaw), local(center, x1, z0, yaw),
                local(center, x1, z1, yaw), local(center, x0, z1, yaw), color);
        }
    }
    private static void outline(Vec3 center, double width, double depth, float yaw, int color) {
        rectangle(local(center, -width / 2, 0, yaw), 0.1, depth, yaw, color);
        rectangle(local(center, width / 2, 0, yaw), 0.1, depth, yaw, color);
        rectangle(local(center, 0, -depth / 2, yaw), width, 0.1, yaw, color);
        rectangle(local(center, 0, depth / 2, yaw), width, 0.1, yaw, color);
    }
    private static void strip(Vec3 a, Vec3 b, double width, int color) {
        double dx = b.f_82479_ - a.f_82479_, dz = b.f_82481_ - a.f_82481_, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) return;
        double rx = dz / len * width / 2, rz = -dx / len * width / 2;
        int segments = Math.max(1, (int)Math.ceil(len / 0.4));
        for (int i = 0; i < segments && PATCHES.size() < MAX_PATCHES; i++) {
            double t0 = i / (double)segments, t1 = (i + 1.0) / segments;
            Vec3 p = a.m_82520_(dx * t0, (b.f_82480_ - a.f_82480_) * t0, dz * t0);
            Vec3 q = a.m_82520_(dx * t1, (b.f_82480_ - a.f_82480_) * t1, dz * t1);
            patch(p.m_82520_(rx, 0, rz), p.m_82520_(-rx, 0, -rz), q.m_82520_(-rx, 0, -rz), q.m_82520_(rx, 0, rz), color);
        }
    }
    private static void arrow(Vec3 center, float yaw, int color) {
        Vec3 tip = local(center, 0, 1.25, yaw);
        strip(center, tip, 0.08, color);
        strip(tip, local(center, -0.3, 0.85, yaw), 0.08, color);
        strip(tip, local(center, 0.3, 0.85, yaw), 0.08, color);
    }
    private static void disk(Vec3 center, double radius, int color) {
        int steps = (int)Math.ceil(radius * 2 / 0.35);
        for (int x = 0; x < steps; x++) for (int z = 0; z < steps; z++) {
            double x0 = -radius + x * radius * 2 / steps, z0 = -radius + z * radius * 2 / steps;
            double side = radius * 2 / steps;
            if (Math.hypot(x0 + side / 2, z0 + side / 2) > radius) continue;
            patch(center.m_82520_(x0, 0, z0), center.m_82520_(x0 + side, 0, z0),
                center.m_82520_(x0 + side, 0, z0 + side), center.m_82520_(x0, 0, z0 + side), color);
        }
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
            strip(center.m_82520_(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                center.m_82520_(Math.cos(b) * radius, 0, Math.sin(b) * radius), 0.1, color);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (!ClientTactics.active() || PATCHES.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var pose = event.getPoseStack(); Vec3 camera = event.getCamera().m_90583_();
        pose.m_85836_(); pose.m_85837_(-camera.f_82479_, -camera.f_82480_, -camera.f_82481_);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::m_172811_);
        BufferBuilder buffer = Tesselator.m_85913_().m_85915_();
        buffer.m_166779_(VertexFormat.Mode.QUADS, DefaultVertexFormat.f_85815_);
        Matrix4f matrix = pose.m_85850_().m_252922_();
        for (Patch patch : PATCHES) for (Vec3 point : new Vec3[]{patch.a, patch.b, patch.c, patch.d}) {
            int c = patch.color;
            buffer.m_252986_(matrix, (float)point.f_82479_, (float)point.f_82480_, (float)point.f_82481_)
                .m_6122_((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).m_5752_();
        }
        BufferUploader.m_231202_(buffer.m_231175_());
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); pose.m_85849_();
    }
}
