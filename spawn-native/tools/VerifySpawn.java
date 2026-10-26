import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
public final class VerifySpawn {
    public static void main(String[] args)throws Exception{
        int count=0;
        try(ZipFile jar=new ZipFile(args[0])){
            for(ZipEntry e:Collections.list(jar.entries()))if(e.getName().endsWith(".class")){
                if(e.getName().contains("SmokeMod"))throw new AssertionError("Test fixture in production JAR");
                ClassNode c=new ClassNode();new ClassReader(jar.getInputStream(e)).accept(c,0);
                if(!e.getName().startsWith("com/ninni/spawn/"))continue;
                for(MethodNode m:c.methods)if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m);count++;
                    if(m.name.equals("<clinit>")&&PatchSpawn.CUT.containsKey(c.name))for(AbstractInsnNode n:m.instructions)if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTSTATIC&&PatchSpawn.CUT.get(c.name).contains(f.name))throw new AssertionError("Removed registration: "+c.name+"."+f.name);
                }
            }
        }
        System.out.println("PASS ASM verifies "+count+" native Spawn methods; removed registry initializers absent");
    }
}
