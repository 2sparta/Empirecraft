package ua.townyrecruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.registries.ForgeRegistries;

final class Supplies {
    static void tick(Bridge b,AbstractRecruitEntity r){
        b.recruitment.feed(r);
        if(!GunGoal.gun(r.m_21205_()))return;
        if(!r.needsToGetFood() && !GunGoal.gun(r.m_21205_()))return;
        Container inv=null;BlockPos p=r.getUpkeepPos();Entity carrier=null;Level l=r.m_20193_();
        if(p!=null){
            if(!l.m_46805_(p) || !b.allowed(r,p))return;
            BlockEntity be=l.m_7702_(p);BlockState state=l.m_8055_(p);
            if(state.m_60734_() instanceof ChestBlock chest){
                // A double chest can straddle a Towny border: both halves must pass.
                if(state.m_61143_(ChestBlock.f_51479_)!=ChestType.SINGLE){
                    BlockPos other=p.m_121945_(ChestBlock.m_51584_(state));
                    if(!l.m_46805_(other) || !b.allowed(r,other))return;
                }
                inv=ChestBlock.m_51511_(chest,state,l,p,false);
            }else if(be instanceof Container c)inv=c;
        }else if(r.getUpkeepUUID()!=null && l instanceof net.minecraft.server.level.ServerLevel sl){
            carrier=sl.m_8791_(r.getUpkeepUUID());if(carrier==null)return;p=carrier.m_20097_();
            if(!b.allowed(r,p))return;
            if(carrier instanceof Container c)inv=c;
            else if(carrier instanceof InventoryCarrier c)inv=c.m_35311_();
            else if(carrier instanceof AbstractHorse h)inv=h.f_30520_;
        }
        if(inv==null || p==null)return;
        if(!p.m_123314_(r.m_20097_(),3)){
            if(r.needsToGetFood() && r.m_5448_()==null)r.m_21573_().m_26519_(p.m_123341_()+0.5,p.m_123342_(),p.m_123343_()+0.5,1.1);
            return;
        }
        int food=0,ammo=0;boolean gun=GunGoal.gun(r.m_21205_());String ammoId=gun?GunGoal.ammo(r.m_21205_()):"";
        for(int i=6;i<r.getInventory().m_6643_();i++){
            ItemStack s=r.getInventory().m_8020_(i);if(r.canEatItemStack(s))food+=s.m_41613_();
            if(ammoId.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(s.m_41720_()))))ammo+=s.m_41613_();
        }
        int foodNeed=0,ammoNeed=gun?Math.max(0,16-ammo):0;
        for(int slot=0;slot<inv.m_6643_() && (foodNeed>0||ammoNeed>0);slot++){
            ItemStack src=inv.m_8020_(slot);if(src.m_41619_())continue;
            boolean isFood=r.canEatItemStack(src),isAmmo=ammoId.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(src.m_41720_())));
            int need=isFood?foodNeed:isAmmo?ammoNeed:0;if(need<=0)continue;
            int moved=transfer(r,src,Math.min(need,src.m_41613_()));
            if(isFood)foodNeed-=moved;else ammoNeed-=moved;
            if(moved>0)inv.m_6596_();
        }
    }
    static int transfer(AbstractRecruitEntity r,ItemStack src,int amount){
        int left=amount;
        for(int i=6;i<r.getInventory().m_6643_() && left>0;i++){
            ItemStack dest=r.getInventory().m_8020_(i);
            if(!dest.m_41619_() && !ItemStack.m_150942_(src,dest))continue;
            int n=Math.min(left,dest.m_41619_()?src.m_41741_():dest.m_41741_()-dest.m_41613_());if(n<=0)continue;
            if(dest.m_41619_()){ItemStack copy=src.m_41777_();copy.m_41764_(n);r.getInventory().m_6836_(i,copy);}else dest.m_41769_(n);
            src.m_41774_(n);left-=n;
        }
        if(left!=amount)r.getInventory().m_6596_();return amount-left;
    }
}
