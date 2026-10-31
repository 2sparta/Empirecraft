package ua.posters;
import java.util.*;import net.minecraft.core.registries.BuiltInRegistries;import net.minecraft.nbt.*;import net.minecraft.world.phys.AABB;import net.minecraftforge.fml.ModList;
/** Visual models first, saved material/shape data for copycats, neighbour-aware shape fallback. */
public final class BlockVisuals {
    public record Visual(List<Projection.Box> boxes,List<Mesh.Triangle> mesh){}
    private record Key(String name,Map<String,String> properties,int color){}
    private final JsonModels models;private final SupplementariesModels supplementaries;
    private final Map<Key,List<Mesh.Triangle>> cache=new LinkedHashMap<>(256,.75f,true){protected boolean removeEldestEntry(Map.Entry<Key,List<Mesh.Triangle>> e){return size()>256;}};
    public BlockVisuals(ModelAssets assets){models=new JsonModels(assets);supplementaries=new SupplementariesModels(assets);}
    public Visual get(SchematicSnapshot world,SchematicSnapshot.Cell cell){
        String name=cell.name();if(Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air","minecraft:structure_void","minecraft:water","minecraft:lava").contains(name))return new Visual(List.of(),List.of());
        int color=color(world,cell,cell.stateTag());Map<String,String> properties=cell.properties();
        if(name.equals("supplementaries:cannon")){
            var mesh=supplementaries.cannon(properties.getOrDefault("facing","up"),cell.data().m_128457_("yaw"),cell.data().m_128457_("pitch"));if(!mesh.isEmpty())return new Visual(List.of(),mesh);
        }
        boolean copycat=name.startsWith("copycats:")||name.startsWith("create:copycat_");Map<String,CompoundTag> materials=copycat?CopycatMaterials.read(cell.data()):Map.of();
        if(!name.startsWith("minecraft:")&&!copycat){
            Key key=new Key(name,properties,color);List<Mesh.Triangle> mesh=cache.computeIfAbsent(key,k->models.block(name,properties,k.color));if(!mesh.isEmpty())return new Visual(List.of(),mesh);
        }
        List<AABB> shape;try{shape=cell.state().m_60808_(world,cell.pos()).m_83299_();}catch(RuntimeException|LinkageError ex){shape=List.of(new AABB(0,0,0,1,1,1));}
        if(shape.isEmpty())shape=List.of(new AABB(.125,.125,.125,.875,.875,.875));if(shape.size()>256)throw new IllegalArgumentException("complex");
        List<Projection.Box> boxes=new ArrayList<>();for(AABB a:shape)boxes.add(new Projection.Box(a.f_82288_,a.f_82289_,a.f_82290_,a.f_82291_,a.f_82292_,a.f_82293_,cell.part(),color));
        if(copycat){
            if(name.startsWith("copycats:")&&ModList.get()!=null&&ModList.get().isLoaded("copycats")){
                try{return new Visual(CopycatBridge.parts(cell.state(),boxes,materials,t->color(world,cell,t),color),List.of());}catch(LinkageError|RuntimeException ignored){}
            }
            CompoundTag material=materials.get("default");if(material==null&&!materials.isEmpty())material=materials.values().iterator().next();
            if(material!=null){int rgb=color(world,cell,material);boxes=boxes.stream().map(b->new Projection.Box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1(),b.part(),rgb)).toList();}
        }
        return new Visual(boxes,List.of());
    }
    private static int color(SchematicSnapshot world,SchematicSnapshot.Cell cell,CompoundTag tag){
        try{var state=NbtUtils.m_247651_(BuiltInRegistries.f_256975_.m_255303_(),tag);int color=state.m_284242_(world,cell.pos()).f_283871_;return color==0?0x898989:color;}catch(RuntimeException ex){return 0x898989;}
    }
}
