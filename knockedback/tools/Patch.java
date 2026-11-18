import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
public class Patch {
 public static void main(String[] args) throws Exception {
  var cls=new ClassNode(); new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(cls,0);
  int changes=0;
  boolean manager=cls.name.endsWith("/KnockedManager");
  if(cls.name.endsWith("/NetworkHandler"))for(var f:cls.fields)if(f.name.equals("PROTOCOL_VERSION"))f.value="5";
  for(var m:cls.methods){
   if(!manager){
    if(cls.name.endsWith("/NetworkHandler")){
     for(var n:m.instructions.toArray())if(n instanceof LdcInsnNode l&&"4".equals(l.cst))l.cst="5";
     if(m.name.equals("register")){for(var n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){var x=new InsnList();x.add(new FieldInsnNode(Opcodes.GETSTATIC,cls.name,"CHANNEL","Lnet/minecraftforge/network/simple/SimpleChannel;"));x.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/fretux/knockedback/Triage","register","(Lnet/minecraftforge/network/simple/SimpleChannel;)V",false));m.instructions.insertBefore(n,x);changes++;}}
    }else if(cls.name.endsWith("/HelpUpHandler")){
     if(m.name.equals("onPlayerTick")){
      m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
      m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/fretux/knockedback/RescueSafety","tick",m.desc,false));m.instructions.add(new InsnNode(Opcodes.RETURN));changes++;
     }
     if(m.name.equals("onServerTick")){m.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/fretux/knockedback/RescueSafety","validate","()V",false));changes++;}
     if(m.name.equals("startHelpUp"))for(var n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){var x=new InsnList();x.add(new VarInsnNode(Opcodes.ALOAD,0));x.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/fretux/knockedback/RescueSafety","started","(Lnet/minecraft/server/level/ServerPlayer;)V",false));m.instructions.insertBefore(n,x);}
    }
    boolean rescue=(cls.name.endsWith("/HelpUpHandler")&&m.name.equals("completeHelpUp"))||(cls.name.endsWith("/NpcHelpUpHandler")&&m.name.equals("complete"));
    if(rescue){for(var n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){var x=new InsnList();x.add(new VarInsnNode(Opcodes.ALOAD,1));x.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/fretux/knockedback/Triage","recovered","(Lnet/minecraft/server/level/ServerPlayer;)V",false));m.instructions.insertBefore(n,x);changes++;}}
    continue;
   }
   if(m.name.equals("applyKnockedState")||m.name.equals("removeKnockedState"))for(var n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){var x=new InsnList();x.add(new VarInsnNode(Opcodes.ALOAD,0));x.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/fretux/knockedback/Triage","sync","(Lnet/minecraft/world/entity/LivingEntity;)V",false));m.instructions.insertBefore(n,x);}

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
  int expected=manager?4:cls.name.endsWith("/HelpUpHandler")?3:1;
  if(changes!=expected)throw new AssertionError("Unexpected input shape: "+cls.name+" "+changes);
  var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);cls.accept(out);Files.write(Path.of(args[1]),out.toByteArray());
 }
}
