package net.devra.accuratehitboxes.empirecraft.mixin;

import java.util.*;
import java.util.function.Predicate;
import net.devra.accuratehitboxes.empirecraft.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.bettercombat.api.WeaponAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.bettercombat.client.collision.TargetFinder",remap=false)
public abstract class BetterCombatTargetsMixin {
    @Redirect(method="getInitialTargets",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;m_6249_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),remap=false)
    private static List<Entity> empirecraft$modelCandidates(Level level,Entity player,AABB box,Predicate<? super Entity> filter){return WhaleCandidates.query(level,player,box,filter);}
    @Inject(method="findAttackTargetResult",at=@At("HEAD"),remap=false)
    private static void empirecraft$begin(Player player,Entity targeted,WeaponAttributes.Attack attack,double range,CallbackInfoReturnable<?> ci){BetterCombatGeometry.begin(player);}
    @Inject(method="findAttackTargetResult",at=@At("RETURN"),remap=false)
    private static void empirecraft$end(CallbackInfoReturnable<?> ci){BetterCombatGeometry.clear();}
}
