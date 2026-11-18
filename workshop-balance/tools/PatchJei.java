import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Update only the native category's blueprint catalyst; all rendering bytecode is retained. */
public final class PatchJei {
 public static void main(String[] args)throws Exception{
  var node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node,0);int changes=0;
  for(var method:node.methods)if(method.name.equals("setRecipe")&&method.desc.contains("GunsmithManual;"))for(var insn:method.instructions.toArray())if(insn instanceof LdcInsnNode literal&&"flintlock_blueprint".equals(literal.cst)){
   var call=(MethodInsnNode)insn.getNext();if(!call.name.equals("item"))throw new IllegalStateException("Unexpected JEI catalyst bytecode");
   method.instructions.set(insn,new VarInsnNode(Opcodes.ALOAD,2));method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"ua/empirecraft/armory/GunsmithManual","blueprint","()Lnet/minecraft/world/item/ItemStack;",false));changes++;
  }
  if(changes!=1)throw new IllegalStateException("Expected one JEI catalyst, got "+changes);
  var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);Files.write(Path.of(args[1]),writer.toByteArray());
 }
}
