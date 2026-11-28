import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Replace ItemStack.EMPTY padding with a count-1 AIR stack; everything else is retained byte for byte. */
public final class PatchFts implements Opcodes {
 static final String TARGET = "net/mcexpanded/fancytabsections/FTSInternal";
 static int changes;

 static void patch(ClassNode c) {
  for (MethodNode m : c.methods) {
   if (!m.name.equals("applyItems")) continue;
   if (!m.desc.equals("(Lnet/minecraft/world/item/CreativeModeTab;)V")) continue;
   for (AbstractInsnNode n : m.instructions.toArray()) {
    if (!(n instanceof FieldInsnNode f)) continue;
    if (f.getOpcode() != GETSTATIC) continue;
    if (!f.owner.equals("net/minecraft/world/item/ItemStack")) continue;
    if (!f.name.equals("f_41583_")) continue;
    InsnList code = new InsnList();
    code.add(new TypeInsnNode(NEW, "net/minecraft/world/item/ItemStack"));
    code.add(new InsnNode(DUP));
    code.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/item/Items", "f_41852_", "Lnet/minecraft/world/item/Item;"));
    code.add(new InsnNode(ICONST_1));
    code.add(new MethodInsnNode(INVOKESPECIAL, "net/minecraft/world/item/ItemStack", "<init>", "(Lnet/minecraft/world/level/ItemLike;I)V", false));
    m.instructions.insertBefore(f, code);
    m.instructions.remove(f);
    changes++;
   }
  }
 }

 public static void main(String[] args) throws Exception {
  Path in = Path.of(args[0]);
  Path out = Path.of(args[1]);
  try (ZipFile zip = new ZipFile(in.toFile());
       ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
   Enumeration<? extends ZipEntry> it = zip.entries();
   while (it.hasMoreElements()) {
    ZipEntry e = it.nextElement();
    byte[] data;
    try (InputStream is = zip.getInputStream(e)) { data = is.readAllBytes(); }
    if (e.getName().equals(TARGET + ".class")) {
     ClassReader cr = new ClassReader(data);
     ClassNode cn = new ClassNode();
     cr.accept(cn, 0);
     patch(cn);
     ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
     cn.accept(cw);
     data = cw.toByteArray();
    }
    ZipEntry ne = new ZipEntry(e.getName());
    if (e.getTime() != -1) ne.setTime(e.getTime());
    zos.putNextEntry(ne);
    zos.write(data);
    zos.closeEntry();
   }
  }
  if (changes == 0) { System.err.println("FAIL no padding site rewritten"); System.exit(1); }
  System.out.println("OK rewrote " + changes + " padding site(s)");
 }
}
