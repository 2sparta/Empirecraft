import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
/** Make the three static interface injection handlers compatible with Mixin 0.8.5. */
public class PatchVS {
    public static void main(String[] args) throws Exception {
        int[] changed = {0};
        try (ZipFile input = new ZipFile(args[0]); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                byte[] bytes = input.getInputStream(entry).readAllBytes();
                if (entry.getName().equals("org/valkyrienskies/mod/mixin/feature/sculk/MixinVibrationSystemTicker.class")) {
                    ClassWriter writer = new ClassWriter(0);
                    new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if (name.equals("destWorldPos") || name.equals("destSourcePos") || name.equals("destReloadSourcePos")) {
                                if ((access & Opcodes.ACC_STATIC) == 0 || (access & Opcodes.ACC_PRIVATE) == 0)
                                    throw new IllegalStateException("Unexpected upstream handler: " + name);
                                access = (access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
                                changed[0]++;
                            }
                            return super.visitMethod(access, name, descriptor, signature, exceptions);
                        }
                    }, 0);
                    bytes = writer.toByteArray();
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTimeLocal(java.time.LocalDateTime.of(2026, 10, 24, 0, 0));
                out.putNextEntry(copy);out.write(bytes);out.closeEntry();
            }
        }
        if (changed[0] != 3) throw new IllegalStateException("Expected exactly 3 handlers, got " + changed[0]);
        System.out.println("Patched VS interface handlers: " + changed[0]);
    }
}
