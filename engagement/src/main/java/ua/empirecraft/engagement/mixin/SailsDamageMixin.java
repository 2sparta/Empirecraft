package ua.empirecraft.engagement.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.*;
import ua.empirecraft.engagement.*;

@Mixin(targets="com.quintonc.vs_sails.protection.ProtectionEvents",remap=false)
public abstract class SailsDamageMixin {
    @Inject(method="attack",at=@At("HEAD"),cancellable=true) private static void melee(AttackEntityEvent event,CallbackInfo ci){ci.cancel();}
    @Inject(method="explosion",at=@At("HEAD"),cancellable=true) private static void blast(ExplosionEvent.Detonate event,CallbackInfo ci){CombatEvents.explosion(event);ci.cancel();}
    @Inject(method="breaking",at=@At("HEAD"),cancellable=true) private static void breaking(BlockEvent.BreakEvent event,CallbackInfo ci){if(CombatEvents.breakBlock(event))ci.cancel();}
}
