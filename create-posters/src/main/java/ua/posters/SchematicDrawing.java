package ua.posters;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.simibubi.create.foundation.utility.CreatePaths;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class SchematicDrawing {
    private static final class Visuals {static final BlockVisuals INSTANCE=new BlockVisuals(ModelAssets.installed());}
    public static final int MAX_BLOCKS=65_536,MAX_DIMENSION=256;
    private record Key(Path path,long size,long modified,int mask,int resolution){}
    private static final Map<Key,CompoundTag> CACHE=new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Key,CompoundTag> e){return size()>8;}
    };
    public static final class Failure extends Exception {public final String code;public Failure(String code){super(code);this.code=code;}}
    public static CompoundTag create(ItemStack schematic,int mask,String title) throws Failure {return create(schematic,mask,title,PosterOptions.DEFAULT);}
    public static CompoundTag create(ItemStack schematic,int mask,String title,PosterOptions options) throws Failure {
        if(mask==0||(mask&~63)!=0)throw new Failure("views");
        CompoundTag item=schematic.m_41784_();String owner=item.m_128461_("Owner"),file=item.m_128461_("File");
        Path root=CreatePaths.UPLOADED_SCHEMATICS_DIR.toAbsolutePath().normalize(),path;
        try{
            if(owner.isBlank()||file.isBlank()||!file.endsWith(".nbt"))throw new Failure("schematic");
            path=root.resolve(owner).resolve(file).normalize();
            if(!path.startsWith(root)||!Files.isRegularFile(path)||!path.toRealPath().startsWith(root.toRealPath()))throw new Failure("schematic");
            long size=Files.size(path);if(size>8*1024*1024)throw new Failure("large");
            Key key=new Key(path,size,Files.getLastModifiedTime(path).toMillis(),mask,options.resolution());CompoundTag cached=CACHE.get(key);
            if(cached!=null){CompoundTag copy=cached.m_6426_();copy.m_128359_("Title",title);DrawingData.setOptions(copy,options);return copy;}
            CompoundTag nbt;
            try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))){nbt=NbtIo.m_128934_(in,new NbtAccounter(32L*1024*1024));}
            ListTag sizes=nbt.m_128437_("size",3);if(sizes.size()!=3)throw new Failure("schematic");
            int sx=sizes.m_128763_(0),sy=sizes.m_128763_(1),sz=sizes.m_128763_(2);
            if(sx<1||sy<1||sz<1||sx>MAX_DIMENSION||sy>MAX_DIMENSION||sz>MAX_DIMENSION)throw new Failure("large");
            ListTag raw=nbt.m_128437_("blocks",10);if(raw.size()>MAX_BLOCKS)throw new Failure("large");
            ListTag palette=nbt.m_128437_("palette",10);
            if(palette.isEmpty()){ListTag palettes=nbt.m_128437_("palettes",9);if(!palettes.isEmpty())palette=palettes.m_128744_(0);}
            if(palette.isEmpty()||palette.size()>MAX_BLOCKS)throw new Failure("schematic");
            List<BlockState> states=new ArrayList<>();
            for(int i=0;i<palette.size();i++)states.add(NbtUtils.m_247651_(BuiltInRegistries.f_256975_.m_255303_(),palette.m_128728_(i)));
            SchematicSnapshot world=new SchematicSnapshot(sx,sy,sz,states,palette,raw);
            List<Projection.Box> geometry=new ArrayList<>();List<Mesh.Triangle> mesh=new ArrayList<>();
            for(var cell:world.cells()){
                var visual=Visuals.INSTANCE.get(world,cell);var pos=cell.pos();int x=pos.m_123341_(),y=pos.m_123342_(),z=pos.m_123343_();
                for(var b:visual.boxes())geometry.add(new Projection.Box(x+b.x0(),y+b.y0(),z+b.z0(),x+b.x1(),y+b.y1(),z+b.z1(),cell.part(),b.rgb()));
                if(!visual.mesh().isEmpty())mesh.addAll(Mesh.transform(visual.mesh(),new org.joml.Matrix4d().translate(x,y,z),cell.part(),Integer.MIN_VALUE));
                if(geometry.size()>262_144||mesh.size()>1_048_576)throw new Failure("complex");
            }
            if(geometry.isEmpty()&&mesh.isEmpty())throw new Failure("empty");
            List<Projection.View> views=new ArrayList<>();for(int d=0;d<6;d++)if((mask&(1<<d))!=0)views.add(Projection.project(geometry,mesh,sx,sy,sz,d,options.resolution()));
            CompoundTag result=DrawingData.make(title,sx,sy,sz,views,options);CACHE.put(key,result.m_6426_());return result;
        }catch(Failure ex){throw ex;}catch(IllegalArgumentException ex){throw new Failure("complex");}
        catch(IOException|RuntimeException ex){throw new Failure("schematic");}
    }
}
