package com.pockyl.handson_delight.station;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
public final class RecipeBases {
    private RecipeBases() { }
    public static boolean visible(Fluid current, Fluid base) {
        return base.isSame(current == Fluids.EMPTY ? Fluids.WATER : current);
    }
}
