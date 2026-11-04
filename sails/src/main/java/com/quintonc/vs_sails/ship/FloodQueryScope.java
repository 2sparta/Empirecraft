package com.quintonc.vs_sails.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Suppress external ocean only during entity/camera queries. Never alter ocean simulation. */
public final class FloodQueryScope implements AutoCloseable {
    private static final ThreadLocal<Level> CURRENT=new ThreadLocal<>();
    private final Level previous;
    private FloodQueryScope(Level level,double x,double y,double z){
        previous=CURRENT.get();CURRENT.set(FloodZones.containsWorld(level,x,y,z)?level:null);
    }
    public static FloodQueryScope enter(Level level,double x,double y,double z){return new FloodQueryScope(level,x,y,z);}
    public static FluidState filter(Level level,BlockPos pos,FluidState actual){
        if(!actual.is(FluidTags.WATER))return actual;
        var owner=VSGameUtilsKt.getShipManagingPos(level,pos);
        if(owner!=null){
            int q=actual.getAmount();
            if(level instanceof net.minecraft.server.level.ServerLevel server)q=ShipFlooding.knownVolume(server,owner.getId(),pos,q);
            return q==0?Fluids.EMPTY.defaultFluidState():Fluids.FLOWING_WATER.getFlowing(q,q==8);
        }
        return CURRENT.get()==level?Fluids.EMPTY.defaultFluidState():actual;
    }
    @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
