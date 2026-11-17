package ua.empirecraft.menuart;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Resolve production renderer instructions against real class bytes without booting client registries. */
public final class RenderLinkageTests {
    record Api(String parent,List<String> interfaces,Set<String> methods,Set<String> fields) {}
    static final Map<String,Api> CACHE=new HashMap<>();
    static int references;
    static Api api(String owner) {
        if(CACHE.containsKey(owner))return CACHE.get(owner);
        try(var in=RenderLinkageTests.class.getClassLoader().getResourceAsStream(owner+".class")){
            if(in==null){CACHE.put(owner,null);return null;}
            var reader=new ClassReader(in);var methods=new HashSet<String>();var fields=new HashSet<String>();
            reader.accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){methods.add(n+d);return null;}
                @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){fields.add(n+d);return null;}
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            var result=new Api(reader.getSuperName(),List.of(reader.getInterfaces()),methods,fields);CACHE.put(owner,result);return result;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    static boolean exists(String owner,String member,boolean method,Set<String> seen){
        if(owner==null||!seen.add(owner))return false;
        Api info=api(owner);if(info==null)return false;
        if((method?info.methods:info.fields).contains(member))return true;
        if(exists(info.parent,member,method,seen))return true;
        return info.interfaces.stream().anyMatch(i->exists(i,member,method,seen));
    }
    static void require(String owner,String member,boolean method){
        if(!exists(owner,member,method,new HashSet<>()))throw new AssertionError("Unresolved runtime API: "+owner+"."+member);
        references++;
    }
    public static void main(String[] args)throws Exception {
        try(var jar=new JarFile(args[0])){
            for(var entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class")){
                new ClassReader(jar.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                        return new MethodVisitor(Opcodes.ASM9){
                            boolean target(String o){return o.startsWith("net/minecraft/")||o.startsWith("net/minecraftforge/")||o.startsWith("com/mojang/");}
                            @Override public void visitMethodInsn(int op,String o,String n,String d,boolean itf){if(target(o))require(o,n+d,true);}
                            @Override public void visitFieldInsn(int op,String o,String n,String d){if(target(o))require(o,n+d,false);}
                        };
                    }
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
        }
        String screen="net/minecraft/client/gui/screens/inventory/AbstractContainerScreen";
        require(screen,"m_6262_()Lnet/minecraft/world/inventory/AbstractContainerMenu;",true);
        require(screen,"getGuiLeft()I",true);require(screen,"getGuiTop()I",true);
        System.out.println("RENDER_LINKAGE_PASS: "+references+" field/method references resolved against installed Forge/Minecraft bytes");
    }
}
