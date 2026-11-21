package ua.townyrecruits;
import java.util.*;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.item.ItemStack;
/** Expiry is wall-clock based and remains authoritative through unloads, ownership changes and restarts. */
final class Militia {
    static boolean member(AbstractRecruitEntity r){return r.getPersistentData().m_128454_("TRMilitiaUntil")>0;}
    static void install(AbstractRecruitEntity r,long until){
        r.getPersistentData().m_128356_("TRMilitiaUntil",until);
        // No issued items: temporary levies cannot manufacture equipment through dismissal/death.
        for(int i=0;i<r.getInventorySize();i++)r.getInventory().m_6836_(i,ItemStack.f_41583_);
        for(var slot:net.minecraft.world.entity.EquipmentSlot.values())r.m_8061_(slot,ItemStack.f_41583_);
        r.setMoral(30);r.applyMoralEffects();
    }
    static void tick(Recruitment recruitment){
        long now=System.currentTimeMillis();
        for(var r:List.copyOf(recruitment.bridge.loaded)){
            var purchase=recruitment.state.purchases.get(r.m_20148_());
            long until=purchase!=null?purchase.militiaUntil:r.getPersistentData().m_128454_("TRMilitiaUntil");
            if(until<=0)continue;
            if(!member(r))install(r,until);
            if(now>=until){
                recruitment.state.change(()->{recruitment.state.dismissed.add(r.m_20148_());if(purchase!=null)purchase.phase="VOID";});
                var old=recruitment.bridge.data.units.get(r.m_20148_());
                if(old!=null)recruitment.bridge.data.put(r.m_20148_(),new ArmyData.Unit(old.owner(),old.nation(),true));
                recruitment.bridge.military.forget(r);r.m_146870_();
            }else{r.setMoral(Math.min(30,r.getMorale()));r.applyMoralEffects();}
        }
    }
}
