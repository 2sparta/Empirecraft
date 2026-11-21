package ua.empirecraft.siegeworks.vs.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.mss1r.axiomata.ballistics.*;
import me.mss1r.axiomata.ballistics.impact.Fragmentation;
import me.mss1r.axiomata.ballistics.profile.ImpactMaterialProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import ua.empirecraft.siegeworks.vs.Frames;
@Mixin(value=FragmentFlight.class,remap=false)public abstract class FragmentFrames {
 @WrapMethod(method="emit")private static void emit(ServerLevel level,ImpactResolver impacts,ImpactSource source,Vec3 at,Vec3 axis,Fragmentation.Cloud cloud,ImpactMaterialProfile material,ResourceLocation visual,double hardness,Player breaker,Operation<Void> original){
  var ship=Frames.current();Vec3 world=Frames.world(ship,at),direction=Frames.direction(ship,axis,false);try(var scope=Frames.enter(null)){original.call(level,impacts,source,world,direction,cloud,material,visual,hardness,breaker);}
 }
}
