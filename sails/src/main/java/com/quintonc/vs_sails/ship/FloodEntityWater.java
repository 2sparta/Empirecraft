package com.quintonc.vs_sails.ship;

import java.util.function.Predicate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Run Forge's actual fluid-height/current calculation in the managed compartment. */
public final class FloodEntityWater {
    private FloodEntityWater(){}
    public static void push(Entity entity,Predicate<FluidState> predicate,Operation<Void> original){
        var level=entity.level();var ship=FloodZones.shipAt(level,entity.getX(),entity.getY(),entity.getZ());
        if(ship==null || VSGameUtilsKt.getShipManagingPos(level,entity.blockPosition())!=null){original.call(predicate);return;}
        // VS 2.4.11 scans ship fluid only after finding world fluid. Once the
        // exterior ocean is excluded, Forge returns early with a null work map.
        // Query the ship-space bounds directly so that a dry ocean query cannot
        // skip real cabin water. Position and world bounds are always preserved.
        AABB world=entity.getBoundingBox();var velocity=entity.getDeltaMovement();
        AABBd local=new AABBd(world.minX,world.minY,world.minZ,world.maxX,world.maxY,world.maxZ).transform(ship.getWorldToShip());
        try{
            entity.setBoundingBox(new AABB(local.minX,local.minY,local.minZ,local.maxX,local.maxY,local.maxZ));
            original.call(predicate);
        }finally{entity.setBoundingBox(world);}
        double height=FloodImmersion.height(level,ship,world);
        ((FloodEntityAccess)entity).empirecraft$waterHeight(height);
        if(height<=0){entity.setDeltaMovement(velocity);return;}
        var delta=entity.getDeltaMovement().subtract(velocity);
        Vector3d pushed=ship.getShipToWorld().transformDirection(new Vector3d(delta.x,delta.y,delta.z));
        if(pushed.isFinite())entity.setDeltaMovement(velocity.add(pushed.x,pushed.y,pushed.z));
    }
}
