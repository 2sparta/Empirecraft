package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.FloodQueryScope;
import com.quintonc.vs_sails.ship.FloodZones;
import com.quintonc.vs_sails.ship.FloodImmersion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.level.material.FogType;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(targets="net.minecraft.client.Camera",priority=800,remap=false)
public abstract class ShipCameraFluidMixin {
    @Shadow(remap=false) public abstract Entity m_90592_();
    @Shadow(remap=false) public abstract Vec3 m_90583_();
    @WrapMethod(method="m_167685_",remap=false)
    private FogType empirecraft$localWater(Operation<FogType> original){
        var entity=m_90592_();var p=m_90583_();
        if(entity==null)return original.call();
        try(var scope=FloodQueryScope.enter(entity.level(),p.x,p.y,p.z)){
            FogType result=original.call();var ship=FloodZones.shipAt(entity.level(),p.x,p.y,p.z);
            if(ship!=null && (result==FogType.NONE || result==FogType.WATER))return FloodImmersion.wet(entity.level(),ship,p.x,p.y,p.z)?FogType.WATER:FogType.NONE;
            return result;
        }
    }
}
