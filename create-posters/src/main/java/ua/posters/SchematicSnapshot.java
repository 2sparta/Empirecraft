package ua.posters;
import java.util.*;import net.minecraft.core.BlockPos;import net.minecraft.nbt.*;import net.minecraft.world.level.BlockGetter;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.entity.BlockEntity;import net.minecraft.world.level.block.state.BlockState;import net.minecraft.world.level.material.FluidState;
/** Read-only block/BE neighbourhood from the schematic. Nothing is placed or ticked in the world. */
public final class SchematicSnapshot implements BlockGetter {
    public record Cell(BlockPos pos,int part,BlockState state,CompoundTag stateTag,CompoundTag data) {
        public String name(){return stateTag.m_128461_("Name");}
        public Map<String,String> properties(){Map<String,String> out=new HashMap<>();CompoundTag props=stateTag.m_128469_("Properties");for(String k:props.m_128431_())out.put(k,props.m_128461_(k));return Map.copyOf(out);}
    }
    private final Map<BlockPos,Cell> cells=new LinkedHashMap<>();private final Map<BlockPos,Optional<BlockEntity>> entities=new HashMap<>();private final int height;
    public SchematicSnapshot(int sx,int sy,int sz,List<BlockState> palette,ListTag stateTags,ListTag entries){
        height=Math.max(16,sy);
        for(int i=0;i<entries.size();i++){
            CompoundTag entry=entries.m_128728_(i);int state=entry.m_128451_("state");ListTag p=entry.m_128437_("pos",3);
            if(state<0||state>=palette.size()||p.size()!=3)throw new IllegalArgumentException("schematic");int x=p.m_128763_(0),y=p.m_128763_(1),z=p.m_128763_(2);
            if(x<0||x>=sx||y<0||y>=sy||z<0||z>=sz)throw new IllegalArgumentException("schematic");BlockPos pos=new BlockPos(x,y,z);
            Cell cell=new Cell(pos,i,palette.get(state),stateTags.m_128728_(state),entry.m_128469_("nbt"));if(cells.put(pos,cell)!=null)throw new IllegalArgumentException("duplicate block");
        }
    }
    public Collection<Cell> cells(){return cells.values();}
    @Override public BlockState m_8055_(BlockPos pos){Cell cell=cells.get(pos);return cell==null?Blocks.f_50016_.m_49966_():cell.state;}
    @Override public FluidState m_6425_(BlockPos pos){return m_8055_(pos).m_60819_();}
    @Override public int m_141928_(){return height;}
    @Override public int m_141937_(){return 0;}
    @Override public BlockEntity m_7702_(BlockPos pos){
        Cell cell=cells.get(pos);if(cell==null)return null;return entities.computeIfAbsent(cell.pos,p->{
            try{
                CompoundTag tag=cell.data.m_6426_();tag.m_128405_("x",p.m_123341_());tag.m_128405_("y",p.m_123342_());tag.m_128405_("z",p.m_123343_());
                BlockEntity be=null;if(tag.m_128441_("id"))be=BlockEntity.m_155241_(p,cell.state,tag);
                if(be==null&&cell.state.m_60734_() instanceof EntityBlock block){be=block.m_142194_(p,cell.state);if(be!=null)be.m_142466_(tag);}
                return Optional.ofNullable(be);
            }catch(RuntimeException|LinkageError ex){return Optional.empty();}
        }).orElse(null);
    }
}
