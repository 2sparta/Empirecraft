package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Uses collision shapes at the actual sub-block coordinates, including Copycats slopes. */
public final class TacticalTerrain {
    private TacticalTerrain() {}
    public static Vec3 surface(Level level, double x, double z, double nearY, Entity context) {
        BlockPos at = BlockPos.m_274561_(x, nearY, z);
        if (!level.m_46805_(at)) return null;
        Vec3 top = new Vec3(x, nearY + 1.25, z), bottom = new Vec3(x, nearY - 4, z);
        var hit = level.m_45547_(new ClipContext(top, bottom, ClipContext.Block.COLLIDER,
                                                ClipContext.Fluid.NONE, context));
        return hit.m_6662_() == HitResult.Type.BLOCK ? hit.m_82450_() : null;
    }
    public static Vec3 stand(AbstractRecruitEntity r, Vec3 wanted) {
        Level level = r.m_20193_();
        double half = r.m_20205_() / 2.0 - 0.01, y = -Double.MAX_VALUE, lowest = Double.MAX_VALUE;
        for (double x : new double[]{-half, 0, half}) for (double z : new double[]{-half, 0, half}) {
            Vec3 p = surface(level, wanted.f_82479_ + x, wanted.f_82481_ + z, wanted.f_82480_, r);
            if (p == null) return null;
            y = Math.max(y, p.f_82480_);
            lowest = Math.min(lowest, p.f_82480_);
        }
        if (y - lowest > 1.1) return null;
        Vec3 result = new Vec3(wanted.f_82479_, y + 0.005, wanted.f_82481_);
        AABB box = new AABB(result.f_82479_ - half, result.f_82480_, result.f_82481_ - half,
                            result.f_82479_ + half, result.f_82480_ + r.m_20206_(), result.f_82481_ + half);
        if (!level.m_6857_().m_61935_(box)) return null;
        for (var shape : level.m_186434_(r, box)) if (!shape.m_83281_()) return null;
        // Ordinary marching is terrestrial. Never preview water/lava as usable ground.
        if (!level.m_6425_(BlockPos.m_274446_(result)).m_76178_()) return null;
        return result;
    }
    public static boolean near(Vec3 a, Vec3 b, double radius) {
        double dx = a.f_82479_ - b.f_82479_, dz = a.f_82481_ - b.f_82481_;
        return dx * dx + dz * dz < radius * radius && Math.abs(a.f_82480_ - b.f_82480_) < 1.1;
    }
    /** Validate the whole body, not only the destination block. Does not load chunks. */
    public static List<Vec3> segment(AbstractRecruitEntity r, Vec3 from, Vec3 to) {
        int steps = Math.max(1, (int)Math.ceil(from.m_82554_(to) / 0.5));
        if (steps > 512) return List.of();
        List<Vec3> result = new ArrayList<>(); Vec3 previous = from;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double)steps;
            Vec3 requested = new Vec3(from.f_82479_ + (to.f_82479_ - from.f_82479_) * t,
                from.f_82480_ + (to.f_82480_ - from.f_82480_) * t,
                from.f_82481_ + (to.f_82481_ - from.f_82481_) * t);
            Vec3 p = stand(r, requested);
            if (p == null || p.f_82480_ - previous.f_82480_ > 1.05
                || previous.f_82480_ - p.f_82480_ > 1.5) return List.of();
            if (i % 3 == 0 || i == steps) result.add(p);
            previous = p;
        }
        return result;
    }
    public static List<Vec3> route(AbstractRecruitEntity r, Vec3 dest) {
        if (near(r.m_20182_(), dest, 0.3)) return List.of(dest);
        List<Vec3> direct = segment(r, r.m_20182_(), dest);
        if (!direct.isEmpty()) return direct;
        // createPath also updates a navigation's cached target/reach range. A preview must
        // never redirect the recruit's existing order when its active path is recomputed.
        var planning = new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(r, r.m_20193_());
        var original = r.m_21573_().m_26575_(); var evaluator = planning.m_26575_();
        evaluator.m_77351_(original.m_77357_()); evaluator.m_77355_(original.m_77360_());
        evaluator.m_77358_(original.m_77361_()); evaluator.m_254850_(original.m_255100_());
        var path = planning.m_26524_(dest.f_82479_, dest.f_82480_, dest.f_82481_, 0);
        if (path == null || !path.m_77403_() || path.m_77398_() > 256) return List.of();
        List<Vec3> result = new ArrayList<>(); Vec3 prev = r.m_20182_();
        for (int i = 0; i < path.m_77398_(); i++) {
            var node = path.m_77375_(i);
            Vec3 next = new Vec3(node.f_77271_ + 0.5, node.f_77272_, node.f_77273_ + 0.5);
            List<Vec3> part = segment(r, prev, next);
            if (part.isEmpty()) return List.of();
            result.addAll(part); prev = part.get(part.size() - 1);
        }
        List<Vec3> last = segment(r, prev, dest);
        if (last.isEmpty()) return List.of();
        result.addAll(last);
        return result.size() > 256 ? List.of() : result;
    }
}
