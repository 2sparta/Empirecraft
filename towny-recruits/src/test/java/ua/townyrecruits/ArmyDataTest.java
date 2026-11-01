package ua.townyrecruits;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import net.minecraft.nbt.*;
public final class ArmyDataTest {
 public static void main(String[] args)throws Exception{
  UUID owner=UUID.randomUUID(),nation=UUID.randomUUID(),unit=UUID.randomUUID();
  ArmyData d=new ArmyData();d.put(unit,new ArmyData.Unit(owner,nation,false));d.overrides.put(nation,0);
  var n=new Policy.Nation(nation,"N",owner,"P",1,Map.of(owner,"P"),Set.of(),Set.of());
  var snapshot=new Policy.Snapshot(Map.of(nation,n),Map.of(owner,nation));d.reconcile(snapshot);assert d.counts.get(nation)==1;
  ArmyData restored=ArmyData.load(d.m_7176_(new CompoundTag()));assert restored.counts.get(nation)==1;assert restored.overrides.get(nation)==0;
  // Leaving the nation retires even an unloaded unit. Restart cannot restore its old allegiance.
  restored.reconcile(new Policy.Snapshot(Map.of(nation,n),Map.of()));assert restored.units.get(unit).retired();assert restored.counts.isEmpty();
  restored=ArmyData.load(restored.m_7176_(new CompoundTag()));assert restored.units.get(unit).retired();
  d.reconcile(Policy.Snapshot.empty());assert d.units.get(unit).retired();assert d.counts.isEmpty();
  System.out.println("PASS real NBT persistence, unloaded counts, player exit and nation-deletion tombstones");
  Path root=Files.createTempDirectory("townyrecruits-census-");Path dir=Files.createDirectories(root.resolve("dimensions/example/dim/entities"));Path region=dir.resolve("r.-1.0.mca");
  CompoundTag entity=new CompoundTag();entity.m_128359_("id","recruits:recruit");entity.m_128362_("UUID",unit);entity.m_128362_("OwnerUUID",owner);entity.m_128379_("isOwned",true);entity.m_128350_("Health",20f);
  ListTag entities=new ListTag();entities.add(entity);CompoundTag chunk=new CompoundTag();chunk.m_128365_("Entities",entities);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(new DeflaterOutputStream(bytes))){NbtIo.m_128941_(chunk,out);}
  try(RandomAccessFile file=new RandomAccessFile(region.toFile(),"rw")){file.setLength(12288);file.writeInt((2<<8)|1);file.seek(8192);file.writeInt(bytes.size()+1);file.writeByte(2);file.write(bytes.toByteArray());}
  byte[] before=Files.readAllBytes(region);ArmyData fresh=new ArmyData();fresh.indexExisting(root);assert fresh.indexed;assert fresh.units.size()==1;assert Arrays.equals(before,Files.readAllBytes(region));
  fresh.reconcile(snapshot);assert fresh.counts.get(nation)==1;
  // Re-indexing is idempotent, as is discovering the same unit loaded in another dimension.
  fresh.indexExisting(root);fresh.put(unit,new ArmyData.Unit(owner,nation,false));assert fresh.counts.get(nation)==1;
  Files.delete(region);Files.delete(dir);try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
  System.out.println("PASS first-install census of compressed entity regions without modifying world files; no double counting");
 }
}
