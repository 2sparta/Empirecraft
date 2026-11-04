package net.devra.accuratehitboxes.empirecraft.mixin;

import net.devra.accuratehitboxes.empirecraft.WhaleCandidates;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;

/** Override the inherited default so both Forge and Mohist canReachRaw use model reach. */
@Mixin(Player.class)
public abstract class ModelReachMixin implements IForgePlayer {
    @Override public boolean isCloseEnough(Entity target,double distance){
        Player player=(Player)(Object)this;
        Boolean model=WhaleCandidates.inReach(player,target,distance);
        if(model!=null)return model;
        // Exact Forge 47.4.23 default. Mixin 0.8.5 rewrites an interface-super
        // invocation here into an invalid class Methodref, so keep its expression.
        return target.getBoundingBox().inflate(target.getPickRadius()).distanceToSqr(player.getEyePosition())<distance*distance;
    }
}
