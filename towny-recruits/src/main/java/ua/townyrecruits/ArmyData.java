package ua.townyrecruits;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Units remain counted while unloaded. Retired records also apply to entities loaded later. */
public final class ArmyData extends SavedData {
    public record Unit(UUID owner,UUID nation,boolean retired) {}
    public final Map<UUID,Unit> units=new HashMap<>();
    public final Map<UUID,Integer> overrides=new HashMap<>();
    public final Map<UUID,Integer> counts=new HashMap<>();
    public boolean indexed;
    public static ArmyData get(ServerLevel level) {return level.m_8895_().m_164861_(ArmyData::load,ArmyData::new,"townyrecruits");}
    public static ArmyData load(CompoundTag tag) {
        ArmyData d=new ArmyData(); d.indexed=tag.m_128471_("indexed");
        ListTag list=tag.m_128437_("units",10);
        for(int i=0;i<list.size();i++){CompoundTag t=list.m_128728_(i);d.units.put(t.m_128342_("id"),new Unit(t.m_128342_("owner"),t.m_128403_("nation")?t.m_128342_("nation"):null,t.m_128471_("retired")));}
        CompoundTag limits=tag.m_128469_("limits");for(String k:limits.m_128431_())d.overrides.put(UUID.fromString(k),limits.m_128451_(k));
        d.recount();return d;
    }
    @Override public CompoundTag m_7176_(CompoundTag tag) {
        tag.m_128379_("indexed",indexed);ListTag list=new ListTag();
        units.forEach((id,u)->{CompoundTag t=new CompoundTag();t.m_128362_("id",id);t.m_128362_("owner",u.owner);if(u.nation!=null)t.m_128362_("nation",u.nation);t.m_128379_("retired",u.retired);list.add(t);});
        tag.m_128365_("units",list);CompoundTag limits=new CompoundTag();overrides.forEach((id,n)->limits.m_128405_(id.toString(),n));tag.m_128365_("limits",limits);return tag;
    }
    void put(UUID id,Unit u){if(!u.equals(units.put(id,u))){recount();m_77762_();}}
    void remove(UUID id){if(units.remove(id)!=null){recount();m_77762_();}}
    void recount(){counts.clear();for(Unit u:units.values())if(!u.retired && u.nation!=null)counts.merge(u.nation,1,Integer::sum);}
    void reconcile(Policy.Snapshot s) {
        boolean changed=false;
        for(var e:units.entrySet()) {
            Unit u=e.getValue();if(u.retired)continue;
            UUID current=s.residents().get(u.owner);
            Unit next=u.nation==null?new Unit(u.owner,current,current==null):(!s.nations().containsKey(u.nation)||!u.nation.equals(current)?new Unit(u.owner,u.nation,true):u);
            if(!next.equals(u)){e.setValue(next);changed=true;}
        }
        if(changed){recount();m_77762_();}
    }
    // First-install migration reads region files without opening RegionFile (which can write headers).
    // Run during server startup, before ticks. No world chunks are loaded.
    void indexExisting(Path worldRoot) throws IOException {indexWorlds(Set.of(worldRoot));}
    void indexWorlds(Set<Path> roots) throws IOException {
        if(indexed)return;
        for(Path worldRoot:roots)try(var files=Files.walk(worldRoot)) {
            for(Path p:files.filter(x->x.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca") && x.getParent().getFileName().toString().equals("entities")).toList()) {
                try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"r")) {
                    if(f.length()<8192)throw new IOException("Truncated entity region: "+p);
                    int[] offsets=new int[1024];for(int i=0;i<1024;i++)offsets[i]=f.readInt();
                    String[] coords=p.getFileName().toString().split("\\.");int rx=Integer.parseInt(coords[1]),rz=Integer.parseInt(coords[2]);
                    for(int i=0;i<1024;i++){
                        int sector=offsets[i]>>>8;if(sector==0)continue;
                        f.seek(sector*4096L);int length=f.readInt();int compression=f.readUnsignedByte();
                        InputStream in;
                        if((compression&128)!=0){Path ext=p.getParent().resolve("c."+(rx*32+i%32)+"."+(rz*32+i/32)+".mcc");in=Files.newInputStream(ext);compression&=127;}
                        else {if(length<1||length>16*1024*1024||sector*4096L+4+length>f.length())throw new IOException("Invalid entity region record: "+p);byte[] bytes=new byte[length-1];f.readFully(bytes);in=new ByteArrayInputStream(bytes);}
                        try(InputStream raw=in;InputStream decoded=switch(compression){case 1->new GZIPInputStream(raw);case 2->new InflaterInputStream(raw);case 3->raw;default->throw new IOException("Unknown region compression "+compression);};DataInputStream input=new DataInputStream(decoded)) {
                            CompoundTag root=NbtIo.m_128934_(input,new NbtAccounter(64L*1024*1024));ListTag entities=root.m_128437_("Entities",10);for(int j=0;j<entities.size();j++)indexEntity(entities.m_128728_(j));
                        }
                    }
                }
            }
        }
        indexed=true;recount();m_77762_();
    }
    void indexEntity(CompoundTag t) {
        if(t.m_128461_("id").startsWith("recruits:") && t.m_128471_("isOwned") && t.m_128403_("OwnerUUID") && t.m_128403_("UUID") && (!t.m_128441_("Health") || t.m_128457_("Health")>0))
            units.putIfAbsent(t.m_128342_("UUID"),new Unit(t.m_128342_("OwnerUUID"),null,false));
        ListTag passengers=t.m_128437_("Passengers",10);for(int i=0;i<passengers.size();i++)indexEntity(passengers.m_128728_(i));
    }
}
