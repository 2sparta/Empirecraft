package ua.townyrecruits;

import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.tactics.TacticalMath;

/** Optional installed VS API: never send internal shipyard coordinates as a world-map position. */
public final class ArmyMapCoordinates {
    public record Pose(Vec3 position,float yaw) {}
    private static final Method WORLD=find();
    private ArmyMapCoordinates() {}
    private static Method find(){try{return Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt",false,ArmyMapCoordinates.class.getClassLoader()).getMethod("toWorldCoordinates",Level.class,Vec3.class);}catch(ReflectiveOperationException|LinkageError absent){return null;}}
    public static Pose world(Level level,Vec3 position,float yaw) {
        try {
            Vec3 p=WORLD==null?position:(Vec3)WORLD.invoke(null,level,position);
            if(!valid(p,yaw))return null;
            if(!p.equals(position)) {
                double[] forward=TacticalMath.rotate(0,1,yaw);Vec3 tip=(Vec3)WORLD.invoke(null,level,position.m_82520_(forward[0],0,forward[1]));
                yaw=(float)Math.toDegrees(Math.atan2(p.f_82479_-tip.f_82479_,tip.f_82481_-p.f_82481_));
            }
            return valid(p,yaw)?new Pose(p,yaw):null;
        }catch(ReflectiveOperationException|LinkageError error){TownyRecruits.LOG.debug("Army map waits for ship coordinate transform",error);return null;}
    }
    static boolean valid(Vec3 p,float yaw){return p!=null&&Double.isFinite(p.f_82479_+p.f_82480_+p.f_82481_)&&Math.abs(p.f_82479_)<=30000000&&Math.abs(p.f_82481_)<=30000000&&Math.abs(p.f_82480_)<=4096&&Float.isFinite(yaw);}
}
