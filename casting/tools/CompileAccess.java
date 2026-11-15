import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Compile-only Forge AT equivalents; these Minecraft classes are never packaged. */
public final class CompileAccess {
 public static void main(String[] args)throws Exception{
  try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[2])))){
   for(int i=0;i<2;i++)try(ZipFile in=new ZipFile(args[i])){
    for(var entry:java.util.Collections.list(in.entries())){
     String name=entry.getName();if(!name.endsWith(".class")||!(i==0&&name.startsWith("net/minecraft/world/level/block/entity/BlockEntityType")||i==1&&name.startsWith("net/minecraft/client/gui/screens/MenuScreens")))continue;
     var node=new ClassNode();new ClassReader(in.getInputStream(entry)).accept(node,0);
     if(node.name.endsWith("$BlockEntitySupplier")||node.name.endsWith("$ScreenConstructor"))node.access=(node.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;
     for(var inner:node.innerClasses)if(inner.name.endsWith("$BlockEntitySupplier")||inner.name.endsWith("$ScreenConstructor"))inner.access=(inner.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;
     if(node.name.endsWith("/MenuScreens"))for(var method:node.methods)if(method.name.equals("register"))method.access=(method.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;
     var writer=new ClassWriter(0);node.accept(writer);out.putNextEntry(new ZipEntry(name));out.write(writer.toByteArray());out.closeEntry();
    }
   }
  }
 }
}
