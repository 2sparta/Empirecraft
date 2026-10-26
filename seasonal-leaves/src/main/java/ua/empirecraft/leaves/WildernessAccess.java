package ua.empirecraft.leaves;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
/** Reuse the established fail-closed Towny policy; never infer that missing Towny means wilderness. */
final class WildernessAccess {
    private Object checker; private Method allowed;
    boolean allowed(ServerLevel level,BlockPos pos) {
        if(!VegetationConfig.WILDERNESS.get())return true;
        try {
            if(checker==null) {
                Class<?> type=Class.forName("ua.empirecraft.worldrules.TownyWilderness");
                checker=type.getConstructor().newInstance();allowed=type.getMethod("allowed",Object.class,BlockPos.class);
            }
            return Boolean.TRUE.equals(allowed.invoke(checker,level,pos));
        } catch(ReflectiveOperationException | LinkageError | RuntimeException error) {return false;}
    }
}
