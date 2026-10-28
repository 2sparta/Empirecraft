import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Rewrite native registration boundaries; all unrelated bytecode is retained. */
public final class Patch implements Opcodes {
 static String hooks;
 static int changes;
 static void prefix(MethodNode m,InsnList code){
  LabelNode normal=new LabelNode();
  code.add(new InsnNode(DUP));code.add(new JumpInsnNode(IFNULL,normal));code.add(new InsnNode(ARETURN));
  code.add(normal);code.add(new FrameNode(F_SAME1,0,null,1,new Object[]{"java/lang/Object"}));code.add(new InsnNode(POP));
  m.instructions.insert(code);changes++;
 }
 static void patch(ClassNode c){
  for(MethodNode m:c.methods){
   if(c.name.equals("ua/empirecraft/noend/NoEnd")&&m.name.equals("travel")){
    for(var n:m.instructions.toArray()){
     if(n instanceof LdcInsnNode l&&"minecraft:the_end".equals(l.cst)){l.cst="minecraft:(the_end|the_nether)";changes++;}
     if(n instanceof MethodInsnNode call&&call.owner.equals("java/lang/String")&&call.name.equals("equals")){call.name="matches";call.desc="(Ljava/lang/String;)Z";}
    }
   }
   if(c.name.equals("dev/architectury/registry/registries/DeferredRegister")&&m.name.equals("register")&&Type.getArgumentTypes(m.desc).length==2){
    var code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(new VarInsnNode(ALOAD,1));
    code.add(new MethodInsnNode(INVOKESTATIC,hooks,"architecturyAlias","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
    // The verifier must see the native method's declared return type on the early-return path.
    code.add(new TypeInsnNode(CHECKCAST,Type.getReturnType(m.desc).getInternalName()));prefix(m,code);
   }
   if(c.name.equals("com/tterrag/registrate/builders/AbstractBuilder")&&m.name.equals("register")&&m.desc.startsWith("()")){
    var code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(new MethodInsnNode(INVOKESTATIC,hooks,"registrateAlias","(Ljava/lang/Object;)Ljava/lang/Object;",false));
    code.add(new TypeInsnNode(CHECKCAST,Type.getReturnType(m.desc).getInternalName()));prefix(m,code);
   }
   for(var node:m.instructions.toArray())if(node instanceof MethodInsnNode call&&call.owner.equals("net/minecraftforge/registries/DeferredRegister")&&call.name.equals("register")&&call.desc.equals("(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;")){
    call.setOpcode(INVOKESTATIC);call.owner=hooks;call.name="forge";call.desc="(Ljava/lang/Object;Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/lang/Object;";call.itf=false;
    m.instructions.insert(call,new TypeInsnNode(CHECKCAST,"net/minecraftforge/registries/RegistryObject"));changes++;
   }
   for(var node:m.instructions.toArray())if(node instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/item/CreativeModeTab$Output")&&Type.getReturnType(call.desc).equals(Type.VOID_TYPE)&&Set.of(1,2).contains(Type.getArgumentTypes(call.desc).length)){
    var args=Type.getArgumentTypes(call.desc);
    boolean collection=args[0].getDescriptor().equals("Ljava/util/Collection;");
    if(!collection&&!args[0].getDescriptor().contains("Item"))continue;
    m.instructions.insertBefore(call,new LdcInsnNode(call.name));
    call.setOpcode(INVOKESTATIC);call.owner=hooks;call.name=collection?"creativeAll":"creative";call.itf=false;
    call.desc=args.length==1?"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V":"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V";changes++;
    if(collection)call.desc=call.desc.replaceFirst("Ljava/lang/Object;Ljava/lang/Object;","Ljava/lang/Object;Ljava/util/Collection;");
   }
  }
 }
 public static void main(String[] args)throws Exception{
  hooks=args[2];Path out=Path.of(args[1]);
  try(var z=new ZipFile(args[0])){for(var entry:Collections.list(z.entries())){
   if(!entry.getName().endsWith(".class"))continue;
   byte[] original=z.getInputStream(entry).readAllBytes();
   // Multi-release dependencies ship Java 21 implementations that JDK 17 never loads.
   if(((original[6]&255)<<8|(original[7]&255))>61)continue;
   var c=new ClassNode();new ClassReader(original).accept(c,0);
   int before=changes;patch(c);if(before==changes)continue;
   var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);byte[] raw=writer.toByteArray();
   var check=new ClassNode();new ClassReader(raw).accept(check,0);
   for(var m:check.methods)if((m.access&(ACC_ABSTRACT|ACC_NATIVE))==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name,m);
   Path p=out.resolve(entry.getName());Files.createDirectories(p.getParent());Files.write(p,raw);
  }}
  System.out.println("PASS ASM native registration boundaries: "+args[0]+" ("+changes+")");
 }
}
