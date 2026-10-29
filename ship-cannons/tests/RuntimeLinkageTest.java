package ua.empirecraft.shipcannons.test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Resolve shipped SRG method references against the actual CBC/Create/Forge hierarchy, without booting a world. */
public final class RuntimeLinkageTest {
    private final List<JarFile> jars=new ArrayList<>();
    private final Map<String,ClassNode> cache=new HashMap<>();
    private ClassNode node(String name) throws IOException {
        if(cache.containsKey(name))return cache.get(name);
        byte[] bytes=null;
        for(var jar:jars) {var entry=jar.getJarEntry(name+".class");if(entry!=null){bytes=jar.getInputStream(entry).readAllBytes();break;}}
        if(bytes==null && name.startsWith("java/"))try(var in=ClassLoader.getSystemResourceAsStream(name+".class")){if(in!=null)bytes=in.readAllBytes();}
        if(bytes==null){cache.put(name,null);return null;}
        var result=new ClassNode();new ClassReader(bytes).accept(result,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);cache.put(name,result);return result;
    }
    private boolean method(String owner,String name,String desc,Set<String> visited) throws IOException {
        if(owner==null || !visited.add(owner))return false;
        var c=node(owner);if(c==null)return false;
        if(c.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc)))return true;
        if(name.equals("<init>"))return false;
        if(method(c.superName,name,desc,visited))return true;
        for(String i:c.interfaces)if(method(i,name,desc,visited))return true;
        return false;
    }
    private boolean field(String owner,String name,String desc,Set<String> visited) throws IOException {
        if(owner==null || !visited.add(owner))return false;
        var c=node(owner);if(c==null)return false;
        if(c.fields.stream().anyMatch(f->f.name.equals(name)&&f.desc.equals(desc)))return true;
        if(field(c.superName,name,desc,visited))return true;
        for(String i:c.interfaces)if(field(i,name,desc,visited))return true;
        return false;
    }
    private static boolean optionalOwner(String name) {return name.startsWith("rbasamoyai/createbigcannons/") || name.startsWith("rbasamoyai/ritchiesprojectilelib/") || name.startsWith("net/mehvahdjukaar/supplementaries/");}
    public static void main(String[] args) throws Exception {
        var test=new RuntimeLinkageTest();boolean negative=args[0].equals("broken");Path production=Path.of(args[1]),variant=Path.of(args[2]);
        test.jars.add(new JarFile(variant.toFile()));
        // Match the patched Forge server definitions ahead of vanilla SRG classes.
        var runtime=new TreeSet<Path>();for(int i=3;i<args.length;i++)try(var paths=Files.walk(Path.of(args[i]))){paths.filter(p->p.toString().endsWith(".jar")&&!p.getFileName().toString().startsWith("createbigcannons-")).forEach(runtime::add);}
        var ordered=new ArrayList<>(runtime);ordered.sort(Comparator.comparing((Path p)->!p.getFileName().toString().equals("forge-1.20.1-47.4.23-server.jar")).thenComparing(Path::toString));
        for(var p:ordered)test.jars.add(new JarFile(p.toFile()));
        var missing=new TreeSet<String>();int checked=0;
        try(var jar=new JarFile(production.toFile())) {
            for(var entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class")) {
                var c=new ClassNode();new ClassReader(jar.getInputStream(entry)).accept(c,0);
                for(var m:c.methods)for(var insn:m.instructions) {
                    if(insn instanceof MethodInsnNode call && optionalOwner(call.owner)) {
                        checked++;if(!test.method(call.owner,call.name,call.desc,new HashSet<>()))missing.add(call.owner+"."+call.name+call.desc);
                    }
                    if(insn instanceof FieldInsnNode call && optionalOwner(call.owner)) {
                        checked++;if(!test.field(call.owner,call.name,call.desc,new HashSet<>()))missing.add(call.owner+"."+call.name+":"+call.desc);
                    }
                    if(insn instanceof InvokeDynamicInsnNode dynamic)for(Object arg:dynamic.bsmArgs)if(arg instanceof Handle h && optionalOwner(h.getOwner()) && h.getTag()>=Opcodes.H_INVOKEVIRTUAL) {
                        checked++;if(!test.method(h.getOwner(),h.getName(),h.getDesc(),new HashSet<>()))missing.add(h.getOwner()+"."+h.getName()+h.getDesc());
                    }
                }
            }
        }
        for(var jar:test.jars)jar.close();
        if(checked==0)throw new AssertionError("No optional compatibility calls audited");
        if(negative) {
            if(missing.stream().noneMatch(s->s.contains("PitchOrientedContraptionEntity.getVehicle")))throw new AssertionError("Did not reproduce uploaded getVehicle linkage failure: "+missing);
            System.out.println("SRG_LINKAGE_REGRESSION_PASS original_1.2.0_getVehicle_failure "+variant.getFileName());
        } else {
            if(!missing.isEmpty())throw new AssertionError("Unresolved production SRG compatibility calls: "+missing);
            System.out.println("SRG_LINKAGE_PASS calls="+checked+" runtime="+variant.getFileName());
        }
    }
}
