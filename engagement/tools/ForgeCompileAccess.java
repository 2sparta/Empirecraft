import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/** Compile with exactly the supplier visibility granted by Forge's installed access transformer. */
public final class ForgeCompileAccess {
    public static void main(String[] args)throws Exception {
        String supplier="net/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier";
        try(var input=new ZipFile(args[0]);var output=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
            for(String name:List.of(supplier+".class","net/minecraft/world/level/block/entity/BlockEntityType.class")){
                byte[] bytes=input.getInputStream(input.getEntry(name)).readAllBytes();var writer=new ClassWriter(0);
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9,writer){
                    int visible(int flags){return (flags&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC;}
                    @Override public void visit(int version,int flags,String name,String signature,String parent,String[] interfaces){super.visit(version,name.equals(supplier)?visible(flags):flags,name,signature,parent,interfaces);}
                    @Override public void visitInnerClass(String name,String outer,String inner,int flags){super.visitInnerClass(name,outer,inner,name.equals(supplier)?visible(flags):flags);}
                },0);
                output.putNextEntry(new ZipEntry(name));output.write(writer.toByteArray());output.closeEntry();
            }
        }
    }
}
