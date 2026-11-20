import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class PatchWater {
 static final String STATE="net/minecraft/world/level/block/state/BlockState";
 static final String HELPER="ua/empirecraft/shipphysics/ShipWaterMass";
 static void load(InsnList x,int op,int index){x.add(new VarInsnNode(op,index));}
 static void branch(InsnList x,LabelNode original){x.add(new JumpInsnNode(Opcodes.IFEQ,original));}
 static void finish(InsnList x,LabelNode original){x.add(original);x.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));}
 public static void main(String[] args)throws Exception{
  var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(n,0);int changed=0;
  for(var m:n.methods){var code=new InsnList();var original=new LabelNode();
   if(n.name.endsWith("/BlockProperties")&&m.name.equals("stateMass")&&m.desc.equals("(L"+STATE+";D)D")){
    load(code,Opcodes.ALOAD,0);code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"water","(L"+STATE+";)Z",false));branch(code,original);code.add(new InsnNode(Opcodes.DCONST_0));code.add(new InsnNode(Opcodes.DRETURN));finish(code,original);
   }else if(n.name.endsWith("/MassSync")&&m.name.equals("beforeSetBlock")){
    if(!m.desc.startsWith("(Lnet/minecraft/world/level/Level;IIIL"+STATE+";L"+STATE+";DD"))throw new AssertionError(m.desc);
    load(code,Opcodes.ALOAD,4);load(code,Opcodes.ALOAD,5);load(code,Opcodes.DLOAD,6);load(code,Opcodes.DLOAD,8);
    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"masslessChange","(L"+STATE+";L"+STATE+";DD)Z",false));branch(code,original);
    String record="ua/empirecraft/shipphysics/MassSync$BlockMasses",type="Lorg/valkyrienskies/core/internal/world/chunks/VsiBlockType;";
    code.add(new TypeInsnNode(Opcodes.NEW,record));code.add(new InsnNode(Opcodes.DUP));load(code,Opcodes.DLOAD,6);load(code,Opcodes.DLOAD,8);load(code,Opcodes.ALOAD,10);load(code,Opcodes.ALOAD,11);
    code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,record,"<init>","(DD"+type+type+")V",false));code.add(new InsnNode(Opcodes.ARETURN));finish(code,original);
   }else if(n.name.endsWith("/RiggingEvents")&&m.name.equals("neighbours")){
    load(code,Opcodes.ALOAD,0);code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraftforge/event/level/BlockEvent$NeighborNotifyEvent","getState","()L"+STATE+";",false));
    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"com/quintonc/vs_sails/compat/RiggingNoise","irrelevant","(L"+STATE+";)Z",false));branch(code,original);code.add(new InsnNode(Opcodes.RETURN));finish(code,original);
   }else continue;
   m.instructions.insert(code);changed++;
  }
  if(changed!=1)throw new AssertionError(n.name+" changed="+changed);
  var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);Files.write(Path.of(args[1]),w.toByteArray());
 }
}
