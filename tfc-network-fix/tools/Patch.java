import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class Patch {
 public static void main(String[] args)throws Exception{
  var c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[1]))).accept(c,0);int count=0;
  if(args[0].equals("protocol")){for(var m:c.methods)if(m.name.equals("<clinit>")){
   var instructions=m.instructions.toArray();for(int i=0;i<instructions.length;i++)if(instructions[i] instanceof MethodInsnNode call&&call.owner.equals("net/minecraftforge/forgespi/language/IModFileInfo")&&call.name.equals("versionString")){
    // Replace only the file-version lookup with the pinned upstream protocol. Registration and equality predicates remain intact.
    int end=i;int begin=i-3;if(!(instructions[begin] instanceof MethodInsnNode start)||!start.owner.equals("net/minecraftforge/fml/ModList")||!start.name.equals("get"))throw new IllegalStateException("Unexpected TFC initializer");
    var out=new InsnList();out.add(new LdcInsnNode("3.2.30"));m.instructions.insertBefore(instructions[begin],out);for(int j=begin;j<=end;j++)m.instructions.remove(instructions[j]);count++;
   }
  }
  }else if(args[0].equals("simulate"))for(var m:c.methods)if(m.name.equals("versionString")&&m.desc.equals("()Ljava/lang/String;")){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.instructions.add(new LdcInsnNode("0.0NONE"));m.instructions.add(new InsnNode(Opcodes.ARETURN));count++;}
  if(count!=1)throw new IllegalStateException("Expected exactly one patch; got "+count);
  var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Path.of(args[2]),w.toByteArray());System.out.println("PATCH_PASS "+args[0]);
 }
}
