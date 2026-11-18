import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
public class Patch {
 public static void main(String[] args) throws Exception {
  var cls=new ClassNode(); new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(cls,0);
  int changes=0;
  for(var m:cls.methods){
   if(m.name.equals("applyKnockedEffects")) {m.access=(m.access & ~Opcodes.ACC_PRIVATE)|Opcodes.ACC_PUBLIC; changes++;}
   if(m.name.equals("getKnockedDuration")) {
    m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
    m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/fretux/knockedback/BleedoutPolicy","duration","()I",false));
    m.instructions.add(new InsnNode(Opcodes.IRETURN));changes++;
   }
   if(m.name.equals("tickKnockedStates")) {
    m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
    m.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,cls.name,"knockedEntities","Ljava/util/Map;"));
    m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/fretux/knockedback/EmpireBleedout","tick","(Ljava/util/Map;)V",false));
    m.instructions.add(new InsnNode(Opcodes.RETURN));changes++;
   }
   if(m.name.equals("onLivingHurt")) for(var insn:m.instructions.toArray())
    if(insn instanceof MethodInsnNode call && call.owner.equals("java/util/Map") && call.name.equals("put")) {
     var replacement=new InsnList();replacement.add(new InsnNode(Opcodes.POP2));replacement.add(new InsnNode(Opcodes.POP));replacement.add(new InsnNode(Opcodes.ACONST_NULL));
     m.instructions.insertBefore(call,replacement);m.instructions.remove(call);changes++;
    }
  }
  if(changes!=4)throw new AssertionError("Unexpected input shape: "+changes);
  var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);cls.accept(out);Files.write(Path.of(args[1]),out.toByteArray());
 }
}
