import java.nio.file.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Replace only the unsafe iterable backing set; packet scanning and locks stay intact. */
public final class Patch {
 public static void main(String[] args)throws Exception {
  String name="cc/sighs/oelib/network/api/NetworkAutoRegistration.class";var type=new ClassNode();
  try(var jar=new ZipFile(args[0])){new ClassReader(jar.getInputStream(jar.getEntry(name))).accept(type,0);}
  int changes=0;
  for(var method:type.methods)if(method.name.equals("<clinit>"))for(var insn:method.instructions.toArray()) {
   if(insn instanceof MethodInsnNode call && call.owner.equals("java/util/Collections") && call.name.equals("synchronizedSet")) {
    var constructor=(MethodInsnNode)call.getPrevious();var allocation=(TypeInsnNode)constructor.getPrevious().getPrevious();
    if(!constructor.owner.equals("java/util/LinkedHashSet")||!allocation.desc.equals(constructor.owner))throw new AssertionError("Upstream set initialization changed");
    allocation.desc="java/util/concurrent/CopyOnWriteArraySet";constructor.owner=allocation.desc;method.instructions.remove(call);changes++;
   }
  }
  if(changes!=1)throw new AssertionError("Expected exactly one backing set");
  var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(out);Files.write(Path.of(args[1]),out.toByteArray());
 }
}
