package ua.empirecraft.siegeworks.vs.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import me.mss1r.axiomata.ballistics.ImpactResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.empirecraft.siegeworks.vs.Frames;
@Mixin(value=ImpactResolver.class,remap=false)public abstract class LoadedImpactReads {
 @WrapOperation(method="*",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
 private BlockState state(ServerLevel level,BlockPos pos,Operation<BlockState> original){
  if(Frames.current()==null)return original.call(level,pos);var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk==null?Blocks.BEDROCK.defaultBlockState():chunk.getBlockState(pos);
 }
 @WrapOperation(method="*",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;m_8767_(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
 private int particles(ServerLevel level,ParticleOptions type,double x,double y,double z,int count,double dx,double dy,double dz,double speed,Operation<Integer> original){
  var at=Frames.world(Frames.current(),new Vec3(x,y,z));return original.call(level,type,at.x,at.y,at.z,count,dx,dy,dz,speed);
 }
}
