package ua.empirecraft.workshop.mixin;

import me.mss1r.siegeworks.menu.CastingMenu;
import me.mss1r.siegeworks.item.crafting.CastingRecipe;
import me.mss1r.siegeworks.registry.SiegeworksRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.workshop.*;

@Mixin(value=CastingMenu.class,remap=false)
public abstract class CastingMenuMixin extends AbstractContainerMenu implements CastAccess {
    @Shadow @Final private Inventory inventory;
    @Shadow @Final private BlockPos pos;
    @Shadow @Final private DataSlot caliber;
    @Unique private DataSlot workshop$progress,workshop$duration,workshop$status;
    protected CastingMenuMixin(MenuType<?> type,int id){super(type,id);}
    @Inject(method="<init>",at=@At("RETURN"))
    private void init(int id,Inventory inventory,BlockPos pos,CallbackInfo ci){
        workshop$progress=m_38895_(DataSlot.m_39401_());workshop$duration=m_38895_(DataSlot.m_39401_());workshop$status=m_38895_(DataSlot.m_39401_());
    }
    @Override public BlockPos workshop$position(){return pos;}
    @Override public int workshop$progress(){return workshop$progress.m_6501_();}
    @Override public int workshop$duration(){return workshop$duration.m_6501_();}
    @Override public int workshop$status(){return workshop$status.m_6501_();}
    @Override public void workshop$diameter(int value){if(inventory.f_35978_.m_9236_() instanceof ServerLevel level&&CastJobs.get(level).jobs.containsKey(pos))return;if(CastRules.validDiameter(value)&&workshop$status()==0)caliber.m_6422_(value);}
    @Override public void m_38946_(){workshop$sync();super.m_38946_();}
    @Unique private void workshop$sync(){
        if(workshop$status==null||!(inventory.f_35978_.m_9236_() instanceof ServerLevel level))return;
        var job=CastJobs.get(level).jobs.get(pos);
        if(job!=null)caliber.m_6422_(job.diameter);
        workshop$status.m_6422_(job==null?0:job.ready()?2:1);workshop$progress.m_6422_(job==null?0:job.progress);workshop$duration.m_6422_(job==null?0:job.duration);
    }
    @Inject(method="canCast",at=@At("HEAD"),cancellable=true)
    private void canCast(CallbackInfoReturnable<Boolean> result){
        if(inventory.f_35978_.m_9236_() instanceof ServerLevel level){var data=CastJobs.get(level);var job=data.jobs.get(pos);result.setReturnValue(job!=null?job.ready():data.canStart(inventory.f_35978_,((CastingMenu)(Object)this).recipe(),caliber.m_6501_()));}
        else if(workshop$status()!=0)result.setReturnValue(workshop$status()==2);
    }
    @Inject(method="m_6366_",at=@At("HEAD"),cancellable=true)
    private void click(Player player,int button,CallbackInfoReturnable<Boolean> result){
        if(player!=inventory.f_35978_||player.m_5833_()||!(player.m_9236_() instanceof ServerLevel level)||!m_6875_(player)){result.setReturnValue(false);return;}
        workshop$sync();
        if(button!=2){if(workshop$status()!=0)result.setReturnValue(false);return;}
        var data=CastJobs.get(level);var job=data.jobs.get(pos);boolean accepted;
        if(job!=null)accepted=data.collect(pos,player);
        else{
            accepted=data.start(level,pos,player,((CastingMenu)(Object)this).recipe(),caliber.m_6501_());
            if(!accepted)player.m_5661_(Component.m_237115_("workshop.casting.need_fuel"),true);
        }
        workshop$sync();m_38946_();result.setReturnValue(accepted);
    }
}
