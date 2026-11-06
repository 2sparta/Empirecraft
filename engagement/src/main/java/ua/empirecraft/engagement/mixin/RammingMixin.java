package ua.empirecraft.engagement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.valkyrienskies.core.api.events.CollisionEvent;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.engagement.RamCombat;
import ua.empirecraft.shipphysics.CollisionDamage;

@Pseudo @Mixin(targets="ua.empirecraft.shipphysics.CollisionDamage",remap=false)
public abstract class RammingMixin {
    @WrapMethod(method="capture") private static void capture(CollisionEvent event,boolean ended,Operation<Void> original){try(var ignored=RamCombat.capture(event)){original.call(event,ended);}}
    @WrapMethod(method="impact") private static void impact(MinecraftServer server,CollisionDamage.Frame frame,double speed,Operation<Void> original){try(var ignored=RamCombat.frame(server,frame)){original.call(server,frame,speed);}}
    @Inject(method="plan",at=@At("RETURN")) private static void planned(ServerLevel level,LoadedServerShip target,@Coerce Object cell,double energy,CallbackInfoReturnable<Object> ci){RamCombat.planned(ci.getReturnValue(),level,target,cell);}
    @WrapMethod(method="drain") private static void drain(MinecraftServer server,Operation<Void> original){try{original.call(server);}finally{RamCombat.clearSelected();}}
    @WrapOperation(method="drain",at=@At(value="INVOKE",target="Ljava/util/ArrayDeque;removeFirst()Ljava/lang/Object;"))
    private static Object work(ArrayDeque<?> queue,Operation<Object> original){Object work=original.call(queue);RamCombat.select(work);return work;}
    @Inject(method="canDamage",at=@At("HEAD"),cancellable=true) private static void permit(ServerLevel level,LoadedServerShip ship,BlockPos pos,CallbackInfoReturnable<Boolean> ci){Boolean allow=RamCombat.permitted(level,ship);if(Boolean.FALSE.equals(allow))ci.setReturnValue(false);}
    @WrapOperation(method="drain",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;m_46961_(Lnet/minecraft/core/BlockPos;Z)Z"))
    private static boolean broken(ServerLevel level,BlockPos pos,boolean drops,Operation<Boolean> original){var before=level.getBlockState(pos);boolean changed=original.call(level,pos,drops);if(changed&&!before.isAir()&&level.getBlockState(pos).getBlock()!=before.getBlock())RamCombat.broken(level,pos);return changed;}
    @Inject(method="finish",at=@At("HEAD")) private static void finished(@Coerce Object work,CallbackInfo ci){RamCombat.finished(work);}
}
