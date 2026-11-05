package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.mehvahdjukaar.supplementaries.common.block.fire_behaviors.ProjectileStats;
import net.mehvahdjukaar.supplementaries.common.entities.BombEntity;
import net.mehvahdjukaar.supplementaries.common.items.BombItem;
import net.mehvahdjukaar.supplementaries.configs.CommonConfigs;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

public final class RecruitBombs {
    public static final int COOLDOWN = 15 * 60 * 20;
    private static final String READY = "TownyRecruitsBombReadyAt";
    private RecruitBombs() {}
    public static boolean bomb(ItemStack s) { return !s.m_41619_() && s.m_41720_() instanceof BombItem; }
    public static boolean inventory(Container c) {
        return c instanceof RecruitBombInventory r && r.townyrecruits$isRecruitInventory();
    }
    public static int count(Container c, int except) {
        int total = 0;
        for (int i = 0; i < c.m_6643_(); i++) if (i != except && bomb(c.m_8020_(i)))
            total += c.m_8020_(i).m_41613_();
        return total;
    }
    public static boolean ready(AbstractRecruitEntity r) {
        return !TacticalMath.cooling(r.m_20193_().m_46467_(), r.getPersistentData().m_128454_(READY));
    }
    public static long remaining(AbstractRecruitEntity r) {
        return Math.max(0, r.getPersistentData().m_128454_(READY) - r.m_20193_().m_46467_());
    }
    /** Repairs pre-existing/automation-supplied stacks by dropping the excess, never deleting items. */
    public static void enforce(AbstractRecruitEntity r) {
        if (r.m_20193_().m_5776_()) return;
        Container inv = r.getInventory(); boolean kept = false;
        for (int i = 0; i < inv.m_6643_(); i++) {
            ItemStack s = inv.m_8020_(i); if (!bomb(s)) continue;
            int reserve = i >= 6 && !kept ? 1 : 0;
            int excess = s.m_41613_() - reserve; kept |= reserve == 1;
            if (excess > 0) { r.m_19983_(s.m_41620_(excess)); inv.m_6596_(); }
        }
    }
    public record Shot(Vec3 origin, Vec3 target, TacticalMath.Flight flight) {}
    public static Shot shot(AbstractRecruitEntity r, Vec3 target) {
        Vec3 origin = new Vec3(r.m_20185_(), r.m_20188_() - 0.1, r.m_20189_());
        // Use the actual Supplementaries speed/gravity and its configured fuse lifetime.
        int fuse = CommonConfigs.Tools.BOMB_FUSE.get();
        int maxTicks = fuse > 0 ? Math.min(100, fuse - 2) : 100;
        TacticalMath.Flight flight = TacticalMath.flight(target.f_82479_ - origin.f_82479_,
            target.f_82480_ + 0.12 - origin.f_82480_, target.f_82481_ - origin.f_82481_,
            ProjectileStats.BOMB_SPEED, ProjectileStats.BOMB_GRAVITY, (double)0.99f, maxTicks);
        if (flight == null) return null;
        Vec3 p = origin, v = new Vec3(flight.x(), flight.y(), flight.z());
        for (int i = 0; i < flight.ticks(); i++) {
            Vec3 next = p.m_82549_(v);
            if (!r.m_20193_().m_46805_(net.minecraft.core.BlockPos.m_274446_(next))) return null;
            var hit = r.m_20193_().m_45547_(new ClipContext(p, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, r));
            if (hit.m_6662_() != HitResult.Type.MISS && hit.m_82450_().m_82554_(target) > 0.65) return null;
            p = next; v = v.m_82490_((double)0.99f).m_82520_(0, -ProjectileStats.BOMB_GRAVITY, 0);
        }
        return new Shot(origin, target, flight);
    }
    public static boolean throwBomb(AbstractRecruitEntity r, Shot shot, java.util.UUID commander) {
        if (shot == null || !ready(r)) return false;
        Container inv = r.getInventory();
        for (int i = 6; i < inv.m_6643_(); i++) {
            ItemStack stack = inv.m_8020_(i); if (!bomb(stack)) continue;
            BombEntity entity = new BombEntity(r.m_20193_(), r, ((BombItem)stack.m_41720_()).getType());
            entity.m_6034_(shot.origin.f_82479_, shot.origin.f_82480_, shot.origin.f_82481_);
            entity.m_20334_(shot.flight.x(), shot.flight.y(), shot.flight.z());
            // The existing claim guard expects a player UUID for mod weapon attribution.
            entity.getPersistentData().m_128362_("EmpireCraftClaimGuardShooter", commander);
            if (!r.m_20193_().m_7967_(entity)) return false;
            stack.m_41774_(1); inv.m_6596_();
            r.getPersistentData().m_128356_(READY, r.m_20193_().m_46467_() + COOLDOWN);
            r.m_6674_(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }
}
