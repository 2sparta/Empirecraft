import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class Audit implements Opcodes {
    public static void main(String[] args)throws Exception {
        int missing=0,classes=0;
        try(ZipFile z=new ZipFile(args[0])) {
            for(var entry:Collections.list(z.entries())) {
                if(!entry.getName().endsWith(".class")||entry.getName().contains("/datagen/"))continue;
                ClassNode c=new ClassNode();new ClassReader(z.getInputStream(entry).readAllBytes()).accept(c,0);
                for(var m:c.methods)for(var n:m.instructions.toArray()) {
                    if(n instanceof FieldInsnNode f&&Patch.blocked(f)) {
                        if(f.getOpcode()==PUTSTATIC) {
                            if(!m.name.equals("<clinit>")||Patch.prev(f).getOpcode()!=ACONST_NULL)throw new AssertionError("Still registers "+f.name);
                            missing++;
                        } else throw new AssertionError("Live removed-field access "+c.name+"."+m.name+" "+f.name);
                    }
                }
                classes++;
            }
        }
        if(missing!=12)throw new AssertionError("Expected 12 deleted registration holders, got "+missing);
        System.out.println("PASS independent bytecode audit: "+classes+" runtime classes; no removed-field reads, 12 empty registration holders");
    }
}
