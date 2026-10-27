import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Version-pinned native registration exclusions, retaining non-null compatibility fields. */
public final class NativePatch implements Opcodes {
    static final String PALETTE="earth/terrarium/chipped/common/registry/base/ChippedPaletteRegistry";
    static void chipped(ClassNode cls,Set<String> mixed){
        if(cls.methods.stream().noneMatch(m->m.name.equals("empirecraft$empty")))throw new IllegalStateException("Expected the installed Nether trim first");
        MethodNode init=cls.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
        AbstractInsnNode previous=null;int palettes=0,filters=0;
        for(AbstractInsnNode node:init.instructions.toArray()){
            if(!(node instanceof FieldInsnNode field) || field.getOpcode()!=PUTSTATIC || !field.owner.equals(cls.name))continue;
            if(Set.of("END_STONE","PURPUR_BLOCK").contains(field.name)){
                if(previous==null || !field.desc.equals("L"+PALETTE+";"))throw new IllegalStateException(field.name);
                for(AbstractInsnNode p=previous.getNext();p!=node;p=p.getNext())if(p instanceof JumpInsnNode)throw new IllegalStateException("Nonlinear palette initializer");
                for(AbstractInsnNode p=previous.getNext();p!=node;){var next=p.getNext();init.instructions.remove(p);p=next;}
                init.instructions.insertBefore(node,new MethodInsnNode(INVOKESTATIC,cls.name,"empirecraft$empty","()L"+PALETTE+";",false));palettes++;
            }previous=node;
        }
        for(MethodNode method:cls.methods){
            if(!method.name.equals("createTorchRegistry") && !(method.name.equals("createRegistry") && Type.getArgumentTypes(method.desc).length==4))continue;
            for(AbstractInsnNode node:method.instructions.toArray())if(node instanceof LdcInsnNode ldc && ldc.cst instanceof String text && text.startsWith("(?:")){
                ldc.cst="(?:"+text+"|"+String.join("|",mixed)+")";filters++;
            }
        }
        if(palettes!=2 || filters!=2)throw new IllegalStateException("Unexpected Chipped registration layout: "+palettes+"/"+filters);
    }
    static void contact(ClassNode cls){
        MethodNode method=cls.methods.stream().filter(m->m.name.equals("register") && m.desc.startsWith("(Ljava/lang/String;Ljava/util/function/Supplier;" )).findFirst().orElseThrow();
        String supplier="cc/sighs/oelib/registry/RegisterSupplier",registry="cc/sighs/oelib/registry/DeferredRegister";
        LabelNode retired=new LabelNode(),normal=new LabelNode();InsnList code=new InsnList();
        for(String name:List.of("ender_parcel","ender_postcard","ender_wrapping_paper")){
            code.add(new LdcInsnNode(name));code.add(new VarInsnNode(ALOAD,0));code.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));code.add(new JumpInsnNode(IFNE,retired));
        }
        code.add(new JumpInsnNode(GOTO,normal));code.add(retired);code.add(new FrameNode(F_SAME,0,null,0,null));
        // Construct a detached supplier, bound to air, without adding it to DeferredRegister.entries.
        // Ender-specific comparisons consequently cannot accidentally match ordinary mail.
        code.add(new TypeInsnNode(NEW,supplier));code.add(new InsnNode(DUP));
        code.add(new FieldInsnNode(GETSTATIC,cls.name,"ITEMS","L"+registry+";"));
        code.add(new MethodInsnNode(INVOKEVIRTUAL,registry,"registryKey","()Lnet/minecraft/resources/ResourceKey;",false));
        code.add(new TypeInsnNode(NEW,"net/minecraft/resources/ResourceLocation"));code.add(new InsnNode(DUP));code.add(new LdcInsnNode("contact"));code.add(new VarInsnNode(ALOAD,0));
        code.add(new MethodInsnNode(INVOKESPECIAL,"net/minecraft/resources/ResourceLocation","<init>","(Ljava/lang/String;Ljava/lang/String;)V",false));code.add(new VarInsnNode(ALOAD,1));
        code.add(new MethodInsnNode(INVOKESPECIAL,supplier,"<init>","(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;)V",false));
        code.add(new InsnNode(DUP));code.add(new FieldInsnNode(GETSTATIC,"net/minecraft/world/item/Items","f_41852_","Lnet/minecraft/world/item/Item;"));
        code.add(new MethodInsnNode(INVOKEVIRTUAL,supplier,"bindInstance","(Ljava/lang/Object;)V",false));code.add(new InsnNode(ARETURN));
        code.add(normal);code.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(code);
        // The second postcard-style loop creates Ender variants. Keep only ordinary styles.
        MethodNode styles=cls.methods.stream().filter(m->m.name.equals("initPostcardStyles")).findFirst().orElseThrow();
        var calls=Arrays.stream(styles.instructions.toArray()).filter(n->n instanceof MethodInsnNode call && call.owner.equals("com/flechazo/contact/data/PostcardDataManager") && call.name.equals("getPostcards")).toList();
        if(calls.size()!=2 || !styles.tryCatchBlocks.isEmpty())throw new IllegalStateException("Unexpected postcard style layout");
        AbstractInsnNode end=styles.instructions.getLast();while(end.getOpcode()!=RETURN)end=end.getPrevious();
        for(AbstractInsnNode node=calls.get(1);node!=end;){var next=node.getNext();styles.instructions.remove(node);node=next;}
        styles.localVariables.clear();
    }
    static void contactTab(ClassNode cls){
        int removed=0;
        for(MethodNode method:cls.methods)for(AbstractInsnNode node:method.instructions.toArray()){
            if(!(node instanceof FieldInsnNode field) || field.getOpcode()!=GETSTATIC || !field.owner.equals("com/flechazo/contact/common/registry/ItemRegistry") || !Set.of("ENDER_PARCEL","ENDER_WRAPPING_PAPER").contains(field.name))continue;
            AbstractInsnNode start=node.getPrevious(),get=node.getNext(),cast=get.getNext(),accept=cast.getNext();
            if(!(start instanceof VarInsnNode load) || load.getOpcode()!=ALOAD || !(get instanceof MethodInsnNode call) || !call.name.equals("get") || !(cast instanceof TypeInsnNode) || !(accept instanceof MethodInsnNode output) || !output.name.equals("m_246326_"))throw new IllegalStateException("Unexpected Contact tab expression");
            for(AbstractInsnNode part=start;part!=accept;){var next=part.getNext();method.instructions.remove(part);part=next;}
            method.instructions.remove(accept);removed++;
        }
        if(removed!=2)throw new IllegalStateException("Expected two Ender tab entries, got "+removed);
    }
    static void farmer(ClassNode cls){
        MethodNode method=cls.methods.stream().filter(m->m.name.equals("registerWithTab")).findFirst().orElseThrow();
        LabelNode retired=new LabelNode(),normal=new LabelNode();InsnList code=new InsnList();
        for(String name:List.of("diamond_knife","netherite_knife")){
            code.add(new LdcInsnNode(name));code.add(new VarInsnNode(ALOAD,0));code.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));code.add(new JumpInsnNode(IFNE,retired));
        }
        code.add(new JumpInsnNode(GOTO,normal));code.add(retired);code.add(new FrameNode(F_SAME,0,null,0,null));
        code.add(new TypeInsnNode(NEW,"net/minecraft/resources/ResourceLocation"));code.add(new InsnNode(DUP));code.add(new LdcInsnNode("minecraft:air"));
        code.add(new MethodInsnNode(INVOKESPECIAL,"net/minecraft/resources/ResourceLocation","<init>","(Ljava/lang/String;)V",false));
        code.add(new FieldInsnNode(GETSTATIC,"net/minecraftforge/registries/ForgeRegistries","ITEMS","Lnet/minecraftforge/registries/IForgeRegistry;"));
        code.add(new MethodInsnNode(INVOKESTATIC,"net/minecraftforge/registries/RegistryObject","create","(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraftforge/registries/IForgeRegistry;)Lnet/minecraftforge/registries/RegistryObject;",false));
        code.add(new InsnNode(ARETURN));code.add(normal);code.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(code);
    }
    public static void main(String[] args)throws Exception{
        ClassNode cls=new ClassNode();try(var zip=new ZipFile(args[0])){new ClassReader(zip.getInputStream(zip.getEntry(args[1])).readAllBytes()).accept(cls,0);}
        switch(args[2]){case "chipped"->chipped(cls,new TreeSet<>(Files.readAllLines(Path.of(args[4]))));case "contact"->contact(cls);case "contact-tab"->contactTab(cls);case "farmer"->farmer(cls);default->throw new IllegalArgumentException(args[2]);}
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);cls.accept(writer);byte[] result=writer.toByteArray();
        ClassNode checked=new ClassNode();new ClassReader(result).accept(checked,0);
        for(MethodNode method:checked.methods)if((method.access&(ACC_NATIVE|ACC_ABSTRACT))==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(checked.name,method);
        Files.write(Path.of(args[3]),result);System.out.println("PASS ASM native exclusion: "+cls.name);
    }
}
