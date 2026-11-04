package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.FloodQueryScope;
import com.quintonc.vs_sails.ship.FloodEntityWater;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(value=Entity.class,priority=800)
public abstract class ShipEntityFluidMixin {
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
