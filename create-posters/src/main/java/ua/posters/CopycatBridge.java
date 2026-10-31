package ua.posters;
import java.util.*;import java.util.function.ToIntFunction;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import net.minecraft.nbt.CompoundTag;import net.minecraft.world.level.block.state.BlockState;
/** Optional Copycats+ API linkage is isolated here; the addon does not require Copycats+. */
public final class CopycatBridge {
    public static List<Projection.Box> parts(BlockState state,List<Projection.Box> boxes,Map<String,CompoundTag> materials,ToIntFunction<CompoundTag> colors,int fallback){
        if(!(state.m_60734_() instanceof IMultiStateCopycatBlock block))return recolor(boxes,materials.get("default"),colors,fallback);
        var scale=block.vectorScale(state);int sx=scale.m_123341_(),sy=scale.m_123342_(),sz=scale.m_123343_();
        if(sx<1||sy<1||sz<1||sx>16||sy>16||sz>16)return recolor(boxes,materials.get("default"),colors,fallback);
        List<Projection.Box> out=new ArrayList<>();var properties=block.storageProperties();if(properties.size()>64)throw new IllegalArgumentException("copycat parts");
        for(String key:properties){if(!block.partExists(state,key))continue;var v=block.getVectorFromProperty(state,key);int x=v.m_123341_(),y=v.m_123342_(),z=v.m_123343_();if(x<0||x>=sx||y<0||y>=sy||z<0||z>=sz)continue;
            CompoundTag material=materials.getOrDefault(key,materials.get("default"));int color=material==null?fallback:colors.applyAsInt(material);
            out.addAll(Mesh.clip(boxes,x/(double)sx,y/(double)sy,z/(double)sz,(x+1)/(double)sx,(y+1)/(double)sy,(z+1)/(double)sz,color));
        }
        return out.isEmpty()?recolor(boxes,materials.get("default"),colors,fallback):out;
    }
    private static List<Projection.Box> recolor(List<Projection.Box> boxes,CompoundTag material,ToIntFunction<CompoundTag> colors,int fallback){int color=material==null?fallback:colors.applyAsInt(material);return boxes.stream().map(b->new Projection.Box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1(),b.part(),color)).toList();}
}
