package ua.empirecraft.spawn.test;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;


/** Disposable standard Forge fixture, compiled directly against the shipped SRG runtime. */
@Mod("empirecraft_spawn_smoketest")
public final class SpawnSmokeMod {
    static final Set<String> IDS;
    static {try(var in=SpawnSmokeMod.class.getResourceAsStream("/fixture-ids.txt")){IDS=new HashSet<>(Arrays.asList(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).strip().split("\\n")));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    MinecraftServer server;ServerLevel level;int ticks,checks;
    final boolean baseline=Boolean.getBoolean("empirecraft.spawn.baseline");
    final boolean resume=Boolean.getBoolean("empirecraft.spawn.resume");
    final File stackFile=new File("old-spawn-stacks.dat");
    public SpawnSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent e){server=e.getServer();level=server.m_129783_();}
    void check(boolean yes,String what){checks++;if(!yes)throw new AssertionError(what);}
    BlockPos pos(int i){return new BlockPos(i%8,65,i/8);}
    void seed()throws Exception{
        CompoundTag root=new CompoundTag();ListTag stacks=new ListTag();ListTag blocks=new ListTag();
        int n=0;
        for(String id:new TreeSet<>(IDS)){
            ResourceLocation key=new ResourceLocation(id);
            if(ForgeRegistries.ITEMS.containsKey(key)){CompoundTag row=new ItemStack(ForgeRegistries.ITEMS.getValue(key)).m_41739_(new CompoundTag());stacks.add(row);}
            if(ForgeRegistries.BLOCKS.containsKey(key)){level.m_7731_(pos(n++),ForgeRegistries.BLOCKS.getValue(key).m_49966_(),3);}
        }
        check(stacks.size()==31,"31 original items");check(n==23,"23 original blocks");
        root.m_128365_("items",stacks);NbtIo.m_128944_(root,stackFile);
        Entity retained=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("spawn:sea_cow")).m_20615_(level);
        check(retained!=null,"retained sea cow creation");retained.m_6034_(12,70,12);retained.getPersistentData().m_128379_("SpawnKeepFixture",true);level.m_7967_(retained);
        CompoundTag removedEntity=new CompoundTag();removedEntity.m_128359_("id","spawn:stranded");
        Entity removed=net.minecraft.world.entity.EntityType.m_20642_(removedEntity,level).orElseThrow();removed.m_6034_(10,70,10);level.m_7967_(removed);
        Files.writeString(Path.of("retained-uuid.txt"),retained.m_20148_().toString());
    }
    void verify()throws Exception{
        for(String id:IDS){ResourceLocation key=new ResourceLocation(id);
            check(!ForgeRegistries.ITEMS.containsKey(key),"removed item registered: "+id);
            check(!ForgeRegistries.BLOCKS.containsKey(key),"removed block registered: "+id);
            check(!ForgeRegistries.ENTITY_TYPES.containsKey(key),"removed entity registered: "+id);
        }
        for(String id:List.of("sea_cow","dodo","booby","spider_crab","clam","sunfish")){
            var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("spawn:"+id));check(type!=null,"retained type "+id);check(type.m_20615_(level)!=null,"retained entity constructs "+id);
        }
        for(String id:List.of("date_seed","booby_egg_shard","anthill","tropical_wildflowers"))check(ForgeRegistries.ITEMS.containsKey(new ResourceLocation("spawn:"+id)),"retained item "+id);
        if(resume){
            var saved=NbtIo.m_128937_(stackFile).m_128437_("items",10);check(saved.size()==31,"old item fixture count");
            for(int i=0;i<saved.size();i++)check(ItemStack.m_41712_(saved.m_128728_(i)).m_41619_(),"old deleted stack becomes empty");
            for(int i=0;i<23;i++)check(level.m_8055_(pos(i)).m_60795_(),"old deleted block becomes air at "+i);
            Entity retained=level.m_8791_(UUID.fromString(Files.readString(Path.of("retained-uuid.txt"))));check(retained!=null&&retained.getPersistentData().m_128471_("SpawnKeepFixture"),"retained saved animal");
        }
        // Exercise both previously dangerous worldgen hooks without a removed registry object.
        com.ninni.spawn.server.block.RedAlgaeBlock.populateAbove(level,new BlockPos(0,63,0),level.f_46441_);
        System.out.println("SPAWN_SMOKE_PASS checks="+checks+" resume="+resume);
    }
    void tick(TickEvent.ServerTickEvent event){
        if(server==null||event.phase!=TickEvent.Phase.END)return;
        try{
            ticks++;
            if(ticks==1){level.m_46745_(pos(0));level.m_46745_(new BlockPos(12,70,12));if(baseline)seed();}
            if(ticks==80){if(baseline)System.out.println("SPAWN_SMOKE_BASELINE checks="+checks);else verify();server.m_7570_(false);}
        }catch(Throwable e){e.printStackTrace();System.out.println("SPAWN_SMOKE_FAIL "+e);server.m_7570_(false);}
    }
}
