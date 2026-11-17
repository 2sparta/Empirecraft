package ua.empirecraft.siegeworks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
public final class Blocks {
 private static final TownyGate GATE=new TownyGate();
 public static boolean allowed(Level level,BlockPos pos){return level.isClientSide||GATE.allows(level,pos.getX(),pos.getY(),pos.getZ(),TownyGate.Target.BLOCK,null);}
}
