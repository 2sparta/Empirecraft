package ua.empirecraft.packfixes;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

/** Palettes allocate dense IDs; avoid Guava's scan over unused backing slots on every save. */
public final class PaletteEntries {
    public static <T> List<T> dense(CrudeIncrementalIntIdentityHashBiMap<T> values) {
        int size=values.size();var result=new ArrayList<T>(size);
        for(int id=0;id<size;id++) {
            T value=values.byId(id);
            // An unusual externally supplied sparse map keeps vanilla's traversal.
            if(value==null)return null;
            result.add(value);
        }
        return result;
    }
}
