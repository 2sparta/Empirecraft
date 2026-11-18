package ua.townyrecruits;

import com.talhanation.recruits.entities.*;
import com.magistuarmory.thirtyyearswar.item.GunItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import ua.townyrecruits.tactics.*;

/** Runs Thirty Years War firearms through the existing recruit firing checks. */
public final class GunGoal extends Goal {
    final AbstractRecruitEntity r;
    ItemStack loading;
    long due;
    public GunGoal(AbstractRecruitEntity r){this.r=r;m_7021_(EnumSet.of(Flag.LOOK));}
    public static boolean gun(ItemStack s){return s.m_41720_() instanceof GunItem;}
    public static String ammo(ItemStack s){return "thirtyyearswar:paper_cartridge";}
    @Override public boolean m_8036_(){return Bridge.INSTANCE!=null && Bridge.INSTANCE.ready && r.getIsOwned() && r.getShouldRanged() && gun(r.m_21205_()) && !TacticalMovement.moving(r) && (r.m_5448_()!=null || TacticalFire.held(r) || TacticalCombat.manoeuvring(r)) && !r.needsToGetFood();}
    @Override public boolean m_8045_(){return m_8036_();}
    @Override public boolean m_183429_(){return true;}
    @Override public void m_8041_(){
        loading = null;
        TacticalFire.ready(r, false, false);
        r.m_5810_();
        r.m_21561_(false);
    }

    @Override public void m_8037_(){
        long now = r.m_20193_().m_46467_();
        LivingEntity target = r.m_5448_();
        ItemStack stack = r.m_21205_();
        if (!(stack.m_41720_() instanceof GunItem gun)) return;
        if (loading != stack) {
            loading = stack;
            due = now + duration(stack);
            r.m_6672_(InteractionHand.MAIN_HAND);
        }

        boolean supplied = hasAmmo(stack);
        boolean eligible = supplied && target != null && target.m_6084_() && r.getState() != 3
            && r.shouldAttack(target) && r.m_20280_(target) <= 48 * 48 && TacticalCombat.firingPhase(r);
        var aim = eligible ? TacticalAim.center(target) : null;
        if (eligible) {
            r.m_21563_().m_148051_(target);
            eligible = TacticalAim.clear(r, target, aim, 0, false);
        }
        TacticalFire.ready(r, now >= due && supplied, eligible);
        if (now < due || !eligible || !TacticalFire.permit(r) || !takeAmmo(stack)) return;

        r.m_5810_();
        r.m_21561_(true);
        var delta = aim.m_82546_(r.m_146892_());
        r.m_146922_((float) Math.toDegrees(Math.atan2(-delta.f_82479_, delta.f_82481_)));
        r.m_146926_((float) -Math.toDegrees(Math.atan2(delta.f_82480_, Math.hypot(delta.f_82479_, delta.f_82481_))));
        gun.performShooting(stack, r.m_20193_(), r);
        stack.m_41622_(1, r, entity -> entity.m_21190_(InteractionHand.MAIN_HAND));
        TacticalFire.fired(r);
        due = now + duration(stack);
        r.m_6672_(InteractionHand.MAIN_HAND);
    }
    public boolean hasAmmo(ItemStack weapon){
        String id=ammo(weapon);
        for(int i=6;i<r.getInventory().m_6643_();i++){
            ItemStack s=r.getInventory().m_8020_(i);
            if(!s.m_41619_() && id.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.m_41720_()).toString()))return true;
        }
        return false;
    }
    int duration(ItemStack s){return Math.max(20,s.m_41720_() instanceof GunItem gun ? gun.getReloadTicks() : 20);}
    boolean takeAmmo(ItemStack weapon){
        String id=ammo(weapon);
        for(int i=6;i<r.getInventory().m_6643_();i++){
            ItemStack s=r.getInventory().m_8020_(i);
            if(!s.m_41619_() && id.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.m_41720_()).toString())){s.m_41774_(1);r.getInventory().m_6596_();return true;}
        }
        return false;
    }
}
