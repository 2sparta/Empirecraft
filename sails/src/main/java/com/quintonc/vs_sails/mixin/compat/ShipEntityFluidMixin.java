package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.FloodQueryScope;
import com.quintonc.vs_sails.ship.FloodEntityWater;
import com.quintonc.vs_sails.ship.FloodEntityAccess;
import com.quintonc.vs_sails.ship.FloodImmersion;
import com.quintonc.vs_sails.ship.FloodZones;
import net.minecraft.world.entity.Entity;
import net.minecraft.tags.*;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(value=Entity.class,priority=800)
public abstract class ShipEntityFluidMixin implements FloodEntityAccess {
    @Shadow(remap=false) protected Object2DoubleMap<TagKey<Fluid>> f_19799_;
    @Shadow(remap=false) @Final private Set<TagKey<Fluid>> f_19801_;
    @Shadow(remap=false) protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Shadow(remap=false) private FluidType forgeFluidTypeOnEyes;
    @Override public void empirecraft$waterHeight(double height){
        if(height>0){f_19799_.put(FluidTags.WATER,height);forgeFluidTypeHeight.put(ForgeMod.WATER_TYPE.get(),height);}
        else{f_19799_.removeDouble(FluidTags.WATER);forgeFluidTypeHeight.removeDouble(ForgeMod.WATER_TYPE.get());}
    }
    @Override public void empirecraft$waterEyes(boolean wet){
        f_19801_.remove(FluidTags.WATER);if(wet)f_19801_.add(FluidTags.WATER);
        if(wet || forgeFluidTypeOnEyes==ForgeMod.WATER_TYPE.get())forgeFluidTypeOnEyes=wet?ForgeMod.WATER_TYPE.get():ForgeMod.EMPTY_TYPE.get();
    }
    @Inject(method="m_20323_",at=@At("TAIL"),remap=false)
    private void empirecraft$levelEyes(CallbackInfo ci){
        Entity entity=(Entity)(Object)this;double y=entity.getEyeY()-.11111111;
        var ship=FloodZones.shipAt(entity.level(),entity.getX(),y,entity.getZ());
        if(ship!=null)empirecraft$waterEyes(FloodImmersion.wet(entity.level(),ship,entity.getX(),y,entity.getZ()));
    }
    @WrapMethod(method="updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V",remap=false)
    private void empirecraft$finiteFluidHeight(java.util.function.Predicate<net.minecraft.world.level.material.FluidState> predicate,Operation<Void> original){
        FloodEntityWater.push((Entity)(Object)this,predicate,original);
    }
    @WrapMethod(method="m_6075_",remap=false)
    private void empirecraft$localWater(Operation<Void> original){
        Entity entity=(Entity)(Object)this;
        try(var scope=FloodQueryScope.enter(entity.level(),entity.getX(),entity.getY(),entity.getZ())){original.call();}
    }
}
