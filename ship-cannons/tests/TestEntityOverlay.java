package ua.empirecraft.shipcannons.test;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;
/** Bootstrap uses vanilla client classes; add Forge's persistent-data API for the adapter fixtures. */
public final class TestEntityOverlay {
    public static void main(String[] args)throws Exception {
        byte[] original;try(var z=new ZipFile(args[0])) {original=z.getInputStream(z.getEntry("net/minecraft/world/entity/Entity.class")).readAllBytes();}
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(original).accept(writer,0);
        var m=writer.visitMethod(Opcodes.ACC_PUBLIC,"getPersistentData","()Lnet/minecraft/nbt/CompoundTag;",null,null);
        m.visitCode();m.visitTypeInsn(Opcodes.NEW,"net/minecraft/nbt/CompoundTag");m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/nbt/CompoundTag","<init>","()V",false);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();
        try(var z=new JarOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            z.putNextEntry(new ZipEntry("net/minecraft/world/entity/Entity.class"));z.write(writer.toByteArray());z.closeEntry();
        }
    }
}
