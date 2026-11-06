package ua.empirecraft.engagement.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.engagement.*;

@Mixin(value=Level.class,remap=false)
public abstract class BlockDamageMixin {
    @Inject(method="m_6933_",at=@At("HEAD"),cancellable=true)
    private void guarded(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> ci){
        if((Object)this instanceof ServerLevel level){NavalFlags.invalidate(level,pos);if(!CombatEvents.block(level,pos))ci.setReturnValue(false);}
    }
}
