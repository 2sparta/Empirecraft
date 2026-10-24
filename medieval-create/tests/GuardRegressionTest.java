import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Preserve every native instruction used as an injection anchor, while keeping machines inert. */
public class GuardRegressionTest implements Opcodes {
    static List<String> code(AbstractInsnNode start) {
        var result=new ArrayList<String>();
        for(var n=start;n!=null;n=n.getNext()) {
            if(n.getOpcode()<0)continue;
            String value=Integer.toString(n.getOpcode());
            if(n instanceof MethodInsnNode m)value+=" "+m.owner+"#"+m.name+m.desc+" "+m.itf;
            if(n instanceof FieldInsnNode f)value+=" "+f.owner+"#"+f.name+f.desc;
            if(n instanceof VarInsnNode v)value+=" "+v.var;
            if(n instanceof TypeInsnNode t)value+=" "+t.desc;
            if(n instanceof LdcInsnNode c)value+=" "+c.cst;
            if(n instanceof IntInsnNode i)value+=" "+i.operand;
            if(n instanceof IincInsnNode i)value+=" "+i.var+":"+i.incr;
            if(n instanceof InvokeDynamicInsnNode i)value+=" "+i.name+i.desc+":"+i.bsm+Arrays.toString(i.bsmArgs);
            result.add(value);
        }
        return result;
    }
    static ClassNode read(byte[] bytes) {
        var node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void anchors(Path source,Path target) throws Exception {
        int methods=0;
        try(var original=new ZipFile(source.toFile());var patched=new ZipFile(target.toFile())) {
            for(var entry:Collections.list(original.entries())) {
                if(!entry.getName().endsWith(".class") || !entry.getName().startsWith("com/simibubi/create/"))continue;
                var old=read(original.getInputStream(entry).readAllBytes());
                var current=read(patched.getInputStream(patched.getEntry(entry.getName())).readAllBytes());
                for(var method:current.methods) {
                    var first=method.instructions.getFirst();
                    if(!(first instanceof MethodInsnNode call) || !call.owner.equals(PatchCreate.POLICY) || !call.name.equals("industrialDisabled"))continue;
                    var existing=old.methods.stream().filter(m->m.name.equals(method.name)&&m.desc.equals(method.desc)).findFirst();
                    if(existing.isEmpty())continue; // New zero-speed overrides have no upstream body.
                    var before=existing.get();
                    check(first.getNext() instanceof JumpInsnNode,"Guard must branch to original body");
                    var body=((JumpInsnNode)first.getNext()).label;
                    check(code(before.instructions.getFirst()).equals(code(body)),"Original instructions lost: "+old.name+"#"+method.name);
                    check(before.tryCatchBlocks.size()==method.tryCatchBlocks.size(),"Exception handlers lost");
                    methods++;
                }
            }
        }
        check(methods>100,"Expected guards across the disabled industrial systems");
        System.out.println("PASS native injection anchors and exception handlers preserved in "+methods+" guarded methods");
    }
    static void runtime(Path target) throws Exception {
        var node=new ClassNode();node.version=V17;node.access=ACC_PUBLIC;node.name="IndustrialGuardProbe";node.superName="java/lang/Object";
        String[] names={"tick","enabled","speed","process"};
        String[] descs={"()V","()Z","()F","(Ljava/lang/Object;)Ljava/util/List;"};
        for(int i=0;i<names.length;i++) {
            var method=new MethodNode(ACC_PUBLIC|ACC_STATIC,names[i],descs[i],null,null);
            // The original body represents a forbidden machine effect: it must never execute.
            method.instructions.add(new TypeInsnNode(NEW,"java/lang/AssertionError"));
            method.instructions.add(new InsnNode(DUP));
            method.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/AssertionError","<init>","()V",false));
            method.instructions.add(new InsnNode(ATHROW));
            InsnList disabled=new InsnList();
            if(i==0)disabled.add(new InsnNode(RETURN));
            if(i==1){disabled.add(new InsnNode(ICONST_0));disabled.add(new InsnNode(IRETURN));}
            if(i==2){disabled.add(new InsnNode(FCONST_0));disabled.add(new InsnNode(FRETURN));}
            if(i==3){disabled.add(new VarInsnNode(ALOAD,0));disabled.add(new MethodInsnNode(INVOKESTATIC,"java/util/Collections","singletonList","(Ljava/lang/Object;)Ljava/util/List;",false));disabled.add(new InsnNode(ARETURN));}
            PatchCreate.guard(method,disabled);node.methods.add(method);
        }
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);
        try(var dependencies=new URLClassLoader(new URL[]{target.toUri().toURL()},GuardRegressionTest.class.getClassLoader())) {
            class ProbeLoader extends ClassLoader {
                ProbeLoader(){super(dependencies);}
                Class<?> define(byte[] code){return defineClass("IndustrialGuardProbe",code,0,code.length);}
            }
            Class<?> probe=new ProbeLoader().define(writer.toByteArray());
            probe.getMethod("tick").invoke(null);
            check(Boolean.FALSE.equals(probe.getMethod("enabled").invoke(null)),"Processing must remain disabled");
            check(Float.valueOf(0).equals(probe.getMethod("speed").invoke(null)),"Disabled power source must produce zero speed");
            Object input=new Object();
            check(((List<?>)probe.getMethod("process",Object.class).invoke(null,input)).get(0)==input,"Disabled processing must return the original item");
        }
        System.out.println("PASS JVM-verified guards prevent side effects and preserve disabled return values");
    }
    public static void main(String[] args) throws Exception { anchors(Path.of(args[0]),Path.of(args[1]));runtime(Path.of(args[1])); }
}
