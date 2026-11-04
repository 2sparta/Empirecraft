import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Inspect the actual upstream getter and the shipped patch, without a fabricated Minecraft font. */
public final class ChatWidthTests implements Opcodes {
    static MethodNode width(Path jar)throws Exception {
        try(var zip=new JarFile(jar.toFile())){
            var node=new ClassNode();new ClassReader(zip.getInputStream(zip.getJarEntry("com/ebicep/chatplus/features/chattabs/ChatTab.class"))).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            return node.methods.stream().filter(m->m.name.equals("getWidth")&&m.desc.equals("()I")).findFirst().orElseThrow();
        }
    }
    static byte[] body(MethodNode method){
        var writer=new ClassWriter(0);writer.visit(V17,ACC_PUBLIC,"WidthBody",null,"java/lang/Object",null);
        method.maxStack=0;method.maxLocals=0;method.accept(writer);writer.visitEnd();return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        var original=width(Path.of(args[0]));var patched=width(Path.of(args[1]));
        var instructions=Arrays.stream(patched.instructions.toArray()).filter(n->n.getOpcode()>=0).toList();
        if(!(instructions.get(0) instanceof VarInsnNode v&&v.getOpcode()==ALOAD&&v.var==0))throw new AssertionError("Tab receiver missing");
        if(instructions.get(1).getOpcode()!=ICONST_M1)throw new AssertionError("Language/font reload must invalidate the cached width");
        if(!(instructions.get(2) instanceof FieldInsnNode f&&f.getOpcode()==PUTFIELD&&f.name.equals("width")&&f.desc.equals("I")))throw new AssertionError("Native width cache not invalidated");
        for(int i=0;i<3;i++)patched.instructions.remove(instructions.get(i));
        if(!Arrays.equals(body(original),body(patched)))throw new AssertionError("Native font measurement, padding or hitbox update changed");
        boolean font=false,hitbox=false,cache=false;
        for(var instruction:original.instructions){
            if(instruction instanceof MethodInsnNode m&&m.owner.equals("net/minecraft/client/gui/Font"))font=true;
            if(instruction instanceof FieldInsnNode field){hitbox|=field.getOpcode()==PUTFIELD&&field.name.equals("xEnd");cache|=field.getOpcode()==GETFIELD&&field.name.equals("width");}
        }
        if(!font||!hitbox||!cache)throw new AssertionError("Unexpected upstream layout getter");
        System.out.println("PASS language/font width invalidation; native font measurement, padding and xEnd hitbox code preserved exactly");
    }
}
