import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
public class Fields {
 public static void main(String[] args)throws Exception{
  for(var path:Files.walk(Path.of(args[0])).filter(p->p.toString().endsWith(".jar")).sorted().toList())try(var z=new ZipFile(path.toFile())){
   for(var e:Collections.list(z.entries()))if(e.getName().endsWith(".class")){
    byte[] raw=z.getInputStream(e).readAllBytes();if(((raw[6]&255)<<8|(raw[7]&255))>61)continue;
    var c=new ClassNode();new ClassReader(raw).accept(c,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    for(var f:c.fields)if(f.signature!=null&&(f.signature.contains("RegistryObject<")||f.signature.contains("RegistrySupplier<")||f.signature.contains("BlockEntry<")||f.signature.contains("ItemEntry<")))
     System.out.println(path.getFileName()+"\t"+f.name.toLowerCase(Locale.ROOT)+"\t"+f.desc+f.signature);
   }
  }
 }
}
