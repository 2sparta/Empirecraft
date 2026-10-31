package ua.posters;
import java.util.*;import net.minecraft.nbt.CompoundTag;
/** Actual Create/Copycats+ NBT keys, including separate materials in one multistate block. */
public final class CopycatMaterials {
    public static Map<String,CompoundTag> read(CompoundTag data){
        Map<String,CompoundTag> out=new LinkedHashMap<>();
        for(String key:List.of("Material","material")){CompoundTag state=data.m_128469_(key);if(state.m_128425_("Name",8)){out.put("default",state);break;}}
        CompoundTag multi=data.m_128469_("material_data");if(multi.m_128431_().size()>64)throw new IllegalArgumentException("copycat materials");
        for(String key:new TreeSet<>(multi.m_128431_())){CompoundTag state=multi.m_128469_(key).m_128469_("material");if(state.m_128425_("Name",8))out.put(key,state);}
        return out;
    }
}
