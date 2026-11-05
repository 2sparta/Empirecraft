import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Remove only Reefer registration, creation and its client layer; retain native frames. */
public final class Cut implements Opcodes {
    static final String PREFIX="galena/nirvana/";
    static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    static void remove(MethodNode method,AbstractInsnNode first,AbstractInsnNode last) {
        var end=last.getNext();
        for(var node=first;node!=end;) { var next=node.getNext();method.instructions.remove(node);node=next; }
    }
    static AbstractInsnNode previousCode(AbstractInsnNode node) {
        do { node=node.getPrevious(); } while(node!=null && node.getOpcode()<0);
        return node;
    }
    static void fieldInitializer(ClassNode type,String field) {
        var method=type.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
        FieldInsnNode store=null;
        for(var node:method.instructions.toArray())
            if(node instanceof FieldInsnNode f && f.getOpcode()==PUTSTATIC && f.owner.equals(type.name) && f.name.equals(field)) {
                require(store==null,"Duplicate initializer "+field);store=f;
            }
        require(store!=null,"Missing initializer "+field);
        var start=store.getPrevious();
        while(start.getPrevious()!=null && !(start.getPrevious() instanceof FieldInsnNode f && f.getOpcode()==PUTSTATIC && f.owner.equals(type.name))) start=start.getPrevious();
        remove(method,start,store);
        require(type.fields.removeIf(f->f.name.equals(field)),"Missing field "+field);
    }
    static void handle(Handle value,String owner,Set<String> names) {
        if(value.getOwner().equals(owner)) names.add(value.getName()+value.getDesc());
    }
    static void pruneLambdas(ClassNode type) {
        Set<String> reachable=new HashSet<>();
        for(var method:type.methods) if((method.access&(ACC_PRIVATE|ACC_SYNTHETIC))!=(ACC_PRIVATE|ACC_SYNTHETIC)) reachable.add(method.name+method.desc);
        boolean changed;
        do {
            int before=reachable.size();
            for(var method:type.methods) if(reachable.contains(method.name+method.desc)) for(var node:method.instructions.toArray()) {
                if(node instanceof MethodInsnNode call && call.owner.equals(type.name)) reachable.add(call.name+call.desc);
                if(node instanceof InvokeDynamicInsnNode dynamic) for(Object arg:dynamic.bsmArgs) if(arg instanceof Handle h) handle(h,type.name,reachable);
                if(node instanceof LdcInsnNode constant && constant.cst instanceof Handle h) handle(h,type.name,reachable);
            }
            changed=before!=reachable.size();
        } while(changed);
        type.methods.removeIf(m->(m.access&(ACC_PRIVATE|ACC_SYNTHETIC))==(ACC_PRIVATE|ACC_SYNTHETIC) && !reachable.contains(m.name+m.desc));
    }
    static void peace(ClassNode type) {
        var method=type.methods.stream().filter(m->m.name.equals("onIncreasedTo")).findFirst().orElseThrow();
        MethodInsnNode threshold=null;
        for(var node:method.instructions.toArray()) if(node instanceof MethodInsnNode call && call.name.equals("reeferAfterHits")) threshold=call;
        require(threshold!=null,"Missing Reefer threshold");
        var start=threshold.getPrevious();
        while(start!=null && start.getOpcode()!=ILOAD) start=start.getPrevious();
        require(start!=null,"Missing hits argument");
        var node=threshold.getNext();while(node!=null && !(node instanceof JumpInsnNode))node=node.getNext();
        require(node instanceof JumpInsnNode jump && jump.getOpcode()==IFEQ,"Unexpected Reefer condition");
        var jump=(JumpInsnNode)node;int spawnCalls=0;
        for(var at=jump.getNext();at!=jump.label;at=at.getNext()) {
            require(at!=null,"Invalid Reefer branch");
            if(at instanceof MethodInsnNode call && Set.of("spawnReefers","transformCreepers").contains(call.name))spawnCalls++;
        }
        require(spawnCalls==2,"Expected both Reefer creation paths");
        remove(method,start,jump.label.getPrevious());
        require(type.methods.removeIf(m->m.name.equals("spawnReefers")),"Missing spawn method");
        require(type.methods.removeIf(m->m.name.equals("transformCreepers")),"Missing conversion method");
        type.fields.removeIf(f->f.name.startsWith("REEFER_"));
    }
    static void client(ClassNode type) {
        var method=type.methods.stream().filter(m->m.name.equals("registerLayers")).findFirst().orElseThrow();
        FieldInsnNode field=null;
        for(var node:method.instructions.toArray())if(node instanceof FieldInsnNode f && f.owner.equals(PREFIX+"world/entity/renderer/ReeferRenderer"))field=f;
        require(field!=null,"Missing Reefer render layer");
        var start=previousCode(field);require(start.getOpcode()==ALOAD,"Unexpected layer event argument");
        var end=field.getNext();
        while(end!=null && !(end instanceof MethodInsnNode call && call.name.equals("registerLayerDefinition")))end=end.getNext();
        require(end!=null,"Missing layer registration");remove(method,start,end);
    }
    public static void main(String[] args) throws Exception {
        Map<String,java.util.function.Consumer<ClassNode>> edits=new LinkedHashMap<>();
        edits.put(PREFIX+"index/NirvanaEntities",c->fieldInitializer(c,"REEFER"));
        edits.put(PREFIX+"index/NirvanaItems",c->fieldInitializer(c,"REEFER_SPAWN_EGG"));
        edits.put(PREFIX+"index/NirvanaTags",c->fieldInitializer(c,"CREEPER_LIKE"));
        edits.put(PREFIX+"world/effects/PeaceEffect",Cut::peace);
        edits.put(PREFIX+"forge/client/ForgeClientEntrypoint",Cut::client);
        try(var jar=new ZipFile(args[0])) {
            for(var edit:edits.entrySet()) {
                var name=edit.getKey()+".class";var entry=jar.getEntry(name);require(entry!=null,"Missing actual class "+name);
                var type=new ClassNode();new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(type,0);
                edit.getValue().accept(type);pruneLambdas(type);
                for(var method:type.methods)if((method.access&(ACC_ABSTRACT|ACC_NATIVE))==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(type.name,method);
                var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);
                var path=Path.of(args[1]).resolve(name);Files.createDirectories(path.getParent());Files.write(path,writer.toByteArray());
            }
        }
        System.out.println("PASS five actual Nirvana classes: registration, egg, tag, spawn/conversion and client layer removed; native frames verified");
    }
}
