import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/** Compile-only view: Mohist omits the unused native-window callback interface.
 * All executable client methods come from the real installed Minecraft class.
 * Neither this view nor any Minecraft class is bundled into the mod.
 */
public final class CompileClientApi {
    public static void main(String[] args)throws Exception {
        String name="net/minecraft/client/Minecraft.class";
        try(ZipFile input=new ZipFile(args[0]);ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
            ClassWriter writer=new ClassWriter(0);
            new ClassReader(input.getInputStream(input.getEntry(name))).accept(new ClassVisitor(Opcodes.ASM9,writer){
                @Override public void visit(int version,int access,String name,String signature,String parent,String[] interfaces){
                    super.visit(version,access,name,signature==null?null:signature.replace("Lcom/mojang/blaze3d/platform/WindowEventHandler;",""),parent,Arrays.stream(interfaces)
                            .filter(i->!i.equals("com/mojang/blaze3d/platform/WindowEventHandler")).toArray(String[]::new));
                }
            },0);
            output.putNextEntry(new ZipEntry(name));output.write(writer.toByteArray());output.closeEntry();
        }
    }
}
