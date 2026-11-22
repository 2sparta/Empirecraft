package ua.empirecraft.siegeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
public final class Blocks {
 private static final TownyGate GATE=new TownyGate();
 public static boolean allowed(Level level,BlockPos pos){
  if(level.isClientSide)return true;
  var world=VSGameUtilsKt.toWorldCoordinates(level,pos.getCenter());var actual=BlockPos.containing(world);
  return GATE.allows(level,actual.getX(),actual.getY(),actual.getZ(),TownyGate.Target.BLOCK,null);
 }
}
