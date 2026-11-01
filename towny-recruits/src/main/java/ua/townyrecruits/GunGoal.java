package ua.townyrecruits;

import com.talhanation.recruits.entities.*;
import com.TBK.medieval_boomsticks.common.items.*;
import com.TBK.medieval_boomsticks.common.registers.MBSounds;
import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import ewewukek.musketmod.GunItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import java.util.*;

/** Uses native musket projectiles; avoids Boomsticks' unsafe ServerPlayer cast in its firing helper. */
public final class GunGoal extends Goal {
    final AbstractRecruitEntity r;
    ItemStack loading;
    long due;
    public GunGoal(AbstractRecruitEntity r){this.r=r;m_7021_(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    public static boolean gun(ItemStack s){return s.m_41720_() instanceof GunItem || RechargeItem.isFireGun(s);}
    public static String ammo(ItemStack s){return s.m_41720_() instanceof GunItem?"musketmod:cartridge":"medieval_boomsticks:round_ball";}
    @Override public boolean m_8036_(){return Bridge.INSTANCE!=null && Bridge.INSTANCE.ready && r.getIsOwned() && r.getShouldRanged() && gun(r.m_21205_()) && r.m_5448_()!=null && r.m_5448_().m_6084_() && !r.needsToGetFood() && r.getState()!=3;}
    @Override public boolean m_8045_(){return m_8036_();}
    @Override public boolean m_183429_(){return true;}
    @Override public void m_8041_(){if(loading!=null && RechargeItem.isFireGun(loading)){RechargeItem.setFire(loading,false);RechargeItem.setReCharge(loading,false);}loading=null;due=0;r.m_5810_();r.m_21561_(false);}
    @Override public void m_8037_(){
        long now=r.m_20193_().m_46467_();if(Math.floorMod(now+r.m_19879_(),20)!=0)return;
        LivingEntity target=r.m_5448_();if(target==null || !target.m_6084_() || !r.m_6779_(target))return;
        ItemStack stack=r.m_21205_();if(!gun(stack))return;
        double dist=r.m_20280_(target);
        r.m_21563_().m_148051_(target);
        boolean see=r.m_21574_().m_148306_(target);
        if(dist>48*48 || !see){
            if(!r.getShouldHoldPos() && !r.getShouldFollow())r.m_21573_().m_5624_(target,1.1);
            return;
        }
        r.m_21573_().m_26573_();
        if(loading!=stack){loading=stack;due=now+duration(stack);if(RechargeItem.isFireGun(stack)){RechargeItem.setFire(stack,false);RechargeItem.setReCharge(stack,true);}r.m_6672_(InteractionHand.MAIN_HAND);return;}
        if(now<due){if(RechargeItem.isFireGun(stack)){RechargeItem.setFire(stack,false);RechargeItem.setReCharge(stack,true);}return;}
        // Charge only after the complete loading interval; a lost target cannot create free ammo.
        if(!takeAmmo(stack)){if(RechargeItem.isFireGun(stack)){RechargeItem.setFire(stack,false);RechargeItem.setReCharge(stack,false);}r.m_5810_();due=now+40;return;}
        r.m_5810_();r.m_21561_(true);
        if(stack.m_41720_() instanceof GunItem g){
            GunItem.setLoadingStage(stack,0);GunItem.setLoaded(stack,true);
            g.mobUse(r,InteractionHand.MAIN_HAND,GunItem.addUniformSpread(g.aimAt(r,target),r.m_217043_(),2.0f));
        }else{
            boolean fail=r.m_217043_().m_188501_()<com.TBK.medieval_boomsticks.Config.probabilityFail/100.0;
            int pellets=stack.m_41720_() instanceof SpikedHandGonneItem?3:1;
            for(int i=0;i<pellets;i++){
                RoundBallProjectile bullet=new RoundBallProjectile(r.m_20193_(),r,stack);bullet.f_36705_=AbstractArrow.Pickup.DISALLOWED;
                double dx=target.m_20185_()-bullet.m_20185_(), dz=target.m_20189_()-bullet.m_20189_();
                double dy=target.m_20227_(0.5)-bullet.m_20186_()+Math.sqrt(dx*dx+dz*dz)*0.006;
                if(fail)bullet.m_6686_(0,-1,0,0.2f,1);else bullet.m_6686_(dx,dy,dz,8.0f,pellets>1?5f:1f);
                r.m_20193_().m_7967_(bullet);
            }
            SoundEvent sound=(SoundEvent)(stack.m_41720_() instanceof ArquebusItem?MBSounds.ARQUEBUS_SHOOT.get():MBSounds.HANDGONNE_SHOOT.get());
            r.m_5496_(sound,3f,1f);
            ((ServerLevel)r.m_20193_()).m_8767_(ParticleTypes.f_123762_,r.m_20185_(),r.m_20188_(),r.m_20189_(),10,0.2,0.2,0.2,0.01);
            RechargeItem.setFire(stack,true);RechargeItem.setReCharge(stack,false);
        }
        stack.m_41622_(1,r,e->e.m_21190_(InteractionHand.MAIN_HAND));
        due=now+duration(stack);r.m_6672_(InteractionHand.MAIN_HAND);
    }
    int duration(ItemStack s){return Math.max(20,s.m_41720_() instanceof GunItem?GunItem.reloadDuration(s):RechargeItem.m_40939_(s));}
    boolean takeAmmo(ItemStack weapon){
        String id=ammo(weapon);
        for(int i=6;i<r.getInventory().m_6643_();i++){
            ItemStack s=r.getInventory().m_8020_(i);
            if(!s.m_41619_() && id.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.m_41720_()).toString())){s.m_41774_(1);r.getInventory().m_6596_();return true;}
        }
        return false;
    }
}
