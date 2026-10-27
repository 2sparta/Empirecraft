import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Offline native patches, refusing unexpected bytecode rather than silently skipping. */
public class NativePatch implements Opcodes {
    static final String BASE="net/satisfy/vinery/", REG=BASE+"core/registry/ObjectRegistry";
    static final String YEARS=BASE+"core/util/WineYears", STORAGE=BASE+"core/util/WineStorage";
    static final String STACK="Lnet/minecraft/world/item/ItemStack;", LEVEL="Lnet/minecraft/world/level/Level;";
    static boolean removed(String name) {return Set.of("CHORUS_WINE","CHORUS_WINE_ITEM","CLARK_WINE","CLARK_WINE_ITEM").contains(name);}
    static AbstractInsnNode previous(AbstractInsnNode i) {do{i=i.getPrevious();}while(i!=null && i.getOpcode()<0);return i;}
    static void erase(MethodNode m,AbstractInsnNode start,AbstractInsnNode end) {
        for(var n=start;n!=end;){var next=n.getNext();m.instructions.remove(n);n=next;}
    }
    static InsnList touch(int local) {
        var l=new InsnList();l.add(new VarInsnNode(ALOAD,local));l.add(new VarInsnNode(ALOAD,0));
        l.add(new FieldInsnNode(GETFIELD,"net/minecraft/world/level/block/entity/BlockEntity","f_58857_",LEVEL));
        l.add(new MethodInsnNode(INVOKESTATIC,STORAGE,"touch","("+STACK+LEVEL+")V",false));return l;
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[1]);Files.createDirectories(out);
        try(var zip=new ZipFile(args[0])) {
            for(String simple:List.of("core/registry/ObjectRegistry","core/registry/TabRegistry","core/item/DrinkBlockItem","core/block/entity/StorageBlockEntity","core/block/entity/FermentationBarrelBlockEntity")) {
                String path=BASE+simple+".class";var c=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(path))).accept(c,0);
                int edits=0;
                for(var m:c.methods) {
                    if(simple.endsWith("ObjectRegistry") && m.name.equals("<clinit>")) {
                        for(var i:m.instructions.toArray()) if(i instanceof FieldInsnNode f && f.getOpcode()==PUTSTATIC && removed(f.name)) {
                            var start=i.getPrevious();String id=f.name.replace("_ITEM","").toLowerCase(Locale.ROOT);
                            while(!(start instanceof LdcInsnNode l && id.equals(l.cst)))start=start.getPrevious();
                            m.instructions.insertBefore(start,new InsnNode(ACONST_NULL));erase(m,start,i);edits++;
                        }
                        for(var i:m.instructions.toArray())if(i.getOpcode()==RETURN) {
                            var aliases=new InsnList();
                            for(String field:List.of("CHORUS_WINE","CLARK_WINE","CHORUS_WINE_ITEM","CLARK_WINE_ITEM")) {
                                String desc="Ldev/architectury/registry/registries/RegistrySupplier;";
                                aliases.add(new FieldInsnNode(GETSTATIC,REG,field.endsWith("_ITEM")?"RED_WINE_ITEM":"RED_WINE",desc));
                                aliases.add(new FieldInsnNode(PUTSTATIC,REG,field,desc));
                            }
                            m.instructions.insertBefore(i,aliases);
                        }
                    }
                    if(simple.endsWith("TabRegistry"))for(var i:m.instructions.toArray())
                        if(i instanceof FieldInsnNode f && f.owner.equals(REG) && removed(f.name)) {
                            var start=previous(i);var end=i;
                            while(!(end instanceof MethodInsnNode call && call.name.equals("m_246326_")))end=end.getNext();
                            erase(m,start,end.getNext());edits++;
                        }
                    if(simple.endsWith("DrinkBlockItem") && m.name.equals("m_7373_")) {
                        for(var i:m.instructions)if(i instanceof MethodInsnNode call && call.owner.equals(YEARS) && call.name.equals("getWineAge")){call.name="getWineAgeDays";edits++;}
                    }
                    if(simple.endsWith("StorageBlockEntity") && m.name.equals("setStack")) {m.instructions.insert(touch(2));edits++;}
                    if(simple.endsWith("StorageBlockEntity") && m.name.equals("removeStack"))for(var i:m.instructions.toArray())if(i.getOpcode()==ARETURN) {
                        var l=new InsnList();l.add(new InsnNode(DUP));l.add(new VarInsnNode(ALOAD,0));
                        l.add(new FieldInsnNode(GETFIELD,"net/minecraft/world/level/block/entity/BlockEntity","f_58857_",LEVEL));
                        l.add(new MethodInsnNode(INVOKESTATIC,STORAGE,"touch","("+STACK+LEVEL+")V",false));m.instructions.insertBefore(i,l);edits++;
                    }
                    if(simple.endsWith("FermentationBarrelBlockEntity")) {
                        var calls=new ArrayList<MethodInsnNode>();
                        for(var i:m.instructions)if(i instanceof MethodInsnNode call && call.owner.equals(YEARS)&&call.name.equals("setWineYear"))calls.add(call);
                        for(var call:calls) {
                            // Move bottling date before storing/dropping the result; never stamp only a discarded temporary copy.
                            var start=previous(previous(previous(call)));erase(m,start,call.getNext());
                            AbstractInsnNode store=null;for(var i:m.instructions)if(i instanceof VarInsnNode v&&v.getOpcode()==ASTORE&&v.var==3){store=i;break;}
                            if(store==null)throw new IllegalStateException("Missing bottled result local");
                            m.instructions.insert(store,touch(3));edits++;
                            // Distinct vintages must not merge into an older output stack.
                            int comparisons=0;
                            for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode v && v.name.equals("m_150930_")) {
                                var getItem=previous(v);
                                if(getItem instanceof MethodInsnNode getter && getter.name.equals("m_41720_")) {
                                    m.instructions.remove(getItem);v.setOpcode(INVOKESTATIC);v.name="m_150942_";v.owner="net/minecraft/world/item/ItemStack";
                                    v.desc="("+STACK+STACK+")Z";v.itf=false;comparisons++;
                                }
                            }
                            if(comparisons!=1)throw new IllegalStateException("Unexpected output comparison count "+comparisons);
                        }
                    }
                }
                int expected=simple.endsWith("ObjectRegistry")?4:simple.endsWith("TabRegistry")?2:simple.endsWith("StorageBlockEntity")?2:1;
                if(edits!=expected)throw new IllegalStateException(simple+": expected "+expected+" patches, got "+edits);
                for(var m:c.methods)if(m.instructions.size()>0)new Analyzer<>(new BasicVerifier()).analyze(c.name,m);
                var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);Path target=out.resolve(path);Files.createDirectories(target.getParent());Files.write(target,writer.toByteArray());
                System.out.println("PASS native "+simple+" ("+edits+")");
            }
        }
    }
}
