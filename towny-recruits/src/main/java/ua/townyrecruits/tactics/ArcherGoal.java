package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.*;
import java.util.EnumSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import ua.townyrecruits.Bridge;
import ua.townyrecruits.GunGoal;

/** Owned foot and mounted archers share charging, fire discipline and obstruction checks. */
public final class ArcherGoal extends Goal {
    final AbstractRecruitEntity r;
    ItemStack weapon; long due; boolean loaded;
    public ArcherGoal(AbstractRecruitEntity r) { this.r = r; m_7021_(EnumSet.of(Flag.LOOK)); }
    public static boolean weapon(ItemStack s) { return !GunGoal.gun(s) && (s.m_41720_() instanceof BowItem || s.m_41720_() instanceof CrossbowItem); }
    public static boolean arrows(AbstractRecruitEntity r) {
        for (int i = 6; i < r.getInventory().m_6643_(); i++) if (r.getInventory().m_8020_(i).m_204117_(ItemTags.f_13161_)) return true;
        return false;
    }
    @Override public boolean m_8036_() {
        return Bridge.INSTANCE != null && Bridge.INSTANCE.ready && r.getIsOwned() && r.getShouldRanged()
            && weapon(r.m_21205_()) && !r.needsToGetFood() && !TacticalMovement.moving(r)
            && (r.m_5448_() != null || TacticalFire.held(r) || TacticalCombat.manoeuvring(r));
    }
    @Override public boolean m_8045_() { return m_8036_(); }
    @Override public boolean m_183429_() { return true; }
    @Override public void m_8041_() { TacticalFire.ready(r, loaded, false); r.m_5810_(); r.m_21561_(false); }
    @Override public void m_8037_() {
        long now = r.m_20193_().m_46467_(); ItemStack stack = r.m_21205_();
        if (stack != weapon) { weapon = stack; loaded = false; due = now + loadTime(stack); r.m_6672_(InteractionHand.MAIN_HAND); }
        boolean ammo = arrows(r);
        if (!loaded && now >= due && ammo) { loaded = true; r.m_5810_(); if (stack.m_41720_() instanceof CrossbowItem) CrossbowItem.m_40884_(stack, true); }
        LivingEntity target = r.m_5448_();
        boolean eligible = ammo && target != null && target.m_6084_() && r.getState() != 3 && r.shouldAttack(target)
            && r.m_20280_(target) <= 48 * 48 && TacticalCombat.firingPhase(r);
        var aim = eligible ? TacticalAim.predict(r, target, stack.m_41720_() instanceof CrossbowItem ? 2.25 : 2.6) : null;
        if (eligible) {
            r.m_21563_().m_148051_(target);
            eligible = TacticalAim.clear(r, target, aim, stack.m_41720_() instanceof CrossbowItem ? 2.25 : 2.6, true);
        }
        TacticalFire.ready(r, loaded, eligible);
        if (!loaded || !eligible || !TacticalFire.permit(r)) return;
        {
            ItemStack ammoStack = ItemStack.f_41583_;
            for (int i = 6; i < r.getInventory().m_6643_(); i++) if (r.getInventory().m_8020_(i).m_204117_(ItemTags.f_13161_)) { ammoStack = r.getInventory().m_8020_(i); break; }
            AbstractArrow arrow = ProjectileUtil.m_37300_(r, ammoStack, 1);
            boolean crossbow = stack.m_41720_() instanceof CrossbowItem;
            if (!crossbow) arrow = ((BowItem)stack.m_41720_()).customArrow(arrow);
            if (r instanceof BowmanEntity bowman) arrow.m_36781_(arrow.m_36789_() + bowman.arrowDamageModifier());
            if (crossbow) arrow.m_36767_((byte)EnchantmentHelper.m_44843_(Enchantments.f_44961_, stack));
            var flight = TacticalAim.arrow(r, aim, crossbow ? 2.25 : 2.6);
            if (flight == null) return;
            double length = Math.sqrt(flight.x() * flight.x() + flight.y() * flight.y() + flight.z() * flight.z());
            arrow.m_6686_(flight.x(), flight.y(), flight.z(), (float)length, TacticalActor.mounted(r) ? 2 : 1.25f);
            arrow.f_36705_ = AbstractArrow.Pickup.DISALLOWED;
            if (!r.m_20193_().m_7967_(arrow)) return;
            r.consumeArrow(); r.damageMainHandItem(); r.m_5496_(SoundEvents.f_11687_, 1, 1);
            if (crossbow) CrossbowItem.m_40884_(stack, false);
        }
        TacticalFire.fired(r); loaded = false; due = now + loadTime(stack);
        r.m_6674_(InteractionHand.MAIN_HAND); r.m_6672_(InteractionHand.MAIN_HAND);
    }
    static int loadTime(ItemStack s) {
        return s.m_41720_() instanceof CrossbowItem ? Math.max(20, CrossbowItem.m_40939_(s) + 15) : 40;
    }
}
