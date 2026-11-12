import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Compile-only equivalent of Forge's public BlockEntitySupplier access transformer. */
public class CompileAccess {
    public static void main(String[] args)throws Exception{
        try(ZipFile in=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
            for(var entry:java.util.Collections.list(in.entries())){
                if(!entry.getName().startsWith("net/minecraft/world/level/block/entity/BlockEntityType")||!entry.getName().endsWith(".class"))continue;
                var node=new ClassNode();new ClassReader(in.getInputStream(entry)).accept(node,0);
                if(node.name.endsWith("$BlockEntitySupplier"))node.access=(node.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;
                for(var inner:node.innerClasses)if(inner.name.endsWith("$BlockEntitySupplier"))inner.access=(inner.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;
                var writer=new ClassWriter(0);node.accept(writer);out.putNextEntry(new ZipEntry(entry.getName()));out.write(writer.toByteArray());out.closeEntry();
            }
        }
    }
}
