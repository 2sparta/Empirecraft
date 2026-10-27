import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Pinned, stack-checked removal of native registration and all live consumers. */
public final class Patch implements Opcodes {
    static final String BASE = "net/stirdrem/overgeared/";
    static final String RO = "net/minecraftforge/registries/RegistryObject";
    static final String ITEMS = BASE+"item/ModItems";
    static final Map<String,Set<String>> FIELDS = Map.of(
        ITEMS, Set.of("NETHERITE_ALLOY","HEATED_NETHERITE_ALLOY","DIAMOND_SHARD","DIAMOND_UPGRADE_ARROW","NETHER_TOOL_CAST","DIAMOND_UPGRADE_SMITHING_TEMPLATE"),
        BASE+"block/ModBlocks", Set.of("NETHER_ALLOY_FURNACE","CAST_FURNACE"),
        BASE+"block/entity/ModBlockEntities", Set.of("NETHER_ALLOY_FURNACE_BE","CAST_FURNACE_BE"),
        BASE+"screen/ModMenuTypes", Set.of("NETHER_ALLOY_SMELTER_MENU","CAST_FURNACE")
    );
    static final Set<String> CATEGORIES = Set.of(BASE+"compat/jei/CastingRecipeCategory", BASE+"compat/jei/NetherAlloySmeltingRecipeCategory");
    static final Set<String> SCREENS = Set.of(BASE+"screen/CastFurnaceScreen",BASE+"screen/NetherAlloySmelterScreen", BASE+"screen/CastFurnaceMenu",BASE+"screen/NetherAlloySmelterMenu");
    static int registrations, consumers, statements, arrays, loops;
    static boolean blocked(FieldInsnNode f) {return FIELDS.getOrDefault(f.owner,Set.of()).contains(f.name);}
    static AbstractInsnNode next(AbstractInsnNode n) { do { n=n.getNext(); } while(n!=null && n.getOpcode()<0); return n; }
    static AbstractInsnNode prev(AbstractInsnNode n) { do { n=n.getPrevious(); } while(n!=null && n.getOpcode()<0); return n; }
    static void erase(MethodNode m,AbstractInsnNode start,AbstractInsnNode end) {
        for(AbstractInsnNode n=start;n!=end;){AbstractInsnNode p=n.getNext();m.instructions.remove(n);n=p;}
    }
    static Frame<BasicValue>[] frames(ClassNode c,MethodNode m) throws Exception {return new Analyzer<BasicValue>(new BasicInterpreter()).analyze(c.name,m);}
    static void statement(ClassNode c,MethodNode m,AbstractInsnNode marker) throws Exception {
        var ins=m.instructions;var f=frames(c,m);int at=ins.indexOf(marker),start=at,end=at+1;
        while(start>0 && (f[start]==null||f[start].getStackSize()!=0)) start--;
        while(end<ins.size() && (f[end]==null||f[end].getStackSize()!=0)) end++;
        if(f[start]==null||f[start].getStackSize()!=0||end>=ins.size())throw new IllegalStateException("No expression boundary "+c.name+"."+m.name);
        // Only a complete, straight-line statement can be removed this way.
        for(int i=start;i<end;i++) if(ins.get(i) instanceof JumpInsnNode || ins.get(i) instanceof TableSwitchInsnNode || ins.get(i) instanceof LookupSwitchInsnNode)
            throw new IllegalStateException("Branch inside removed statement "+c.name+"."+m.name);
        erase(m,ins.get(start),ins.get(end));statements++;
    }
    static boolean classMarker(AbstractInsnNode n) {
        if(n instanceof TypeInsnNode t) return CATEGORIES.contains(t.desc)||SCREENS.contains(t.desc);
        if(n instanceof LdcInsnNode l && l.cst instanceof Type t) return CATEGORIES.contains(t.getInternalName())||SCREENS.contains(t.getInternalName());
        return n instanceof FieldInsnNode f && CATEGORIES.contains(f.owner);
    }
    static int integer(AbstractInsnNode n) {
        int op=n.getOpcode();if(op>=ICONST_M1&&op<=ICONST_5)return op-ICONST_0;
        if(n instanceof IntInsnNode i)return i.operand;
        if(n instanceof LdcInsnNode l && l.cst instanceof Integer i)return i;
        throw new IllegalStateException("Not an integer constant");
    }
    static AbstractInsnNode constant(int i) {return i>=-1&&i<=5?new InsnNode(ICONST_0+i):new LdcInsnNode(i);}
    static boolean arrayElement(MethodNode m,FieldInsnNode field) {
        // The reviewed arrays end with the removed Nether cast / diamond arrow.
        AbstractInsnNode store=field;
        while(store!=null&&store.getOpcode()!=AASTORE){
            if(store instanceof JumpInsnNode||store.getOpcode()==RETURN||store.getOpcode()==ARETURN)return false;
            if(store.getOpcode()==ASTORE||store.getOpcode()==PUTSTATIC)return false;
            store=next(store);
        }
        if(store==null)return false;
        AbstractInsnNode array=field;
        while(array!=null&&array.getOpcode()!=ANEWARRAY){
            if(array.getOpcode()==ASTORE||array instanceof JumpInsnNode)return false;
            array=prev(array);
        }
        if(!(array instanceof TypeInsnNode a)||!Set.of("net/minecraft/world/level/ItemLike","net/minecraft/world/item/ItemStack").contains(a.desc))return false;
        var length=prev(array);int size=integer(length);
        AbstractInsnNode index=null;
        // Find the array DUP + final index; an ItemStack NEW/DUP may follow it.
        for(var n=next(array);n!=field;n=next(n))if(n.getOpcode()==DUP){var i=next(n);try{if(integer(i)==size-1)index=i;}catch(IllegalStateException ignored){}}
        if(index==null)throw new IllegalStateException("Only reviewed final array entries may be removed");
        var begin=prev(index);var after=store.getNext();
        erase(m,begin,after);m.instructions.set(length,constant(size-1));arrays++;return true;
    }
    static void disablePotionLoop(MethodNode m,FieldInsnNode field) {
        int at=m.instructions.indexOf(field);
        for(var n=field.getPrevious();n!=null;n=n.getPrevious()){
            if(n instanceof MethodInsnNode call && call.owner.equals("java/util/Iterator") && call.name.equals("hasNext")){
                if(!(next(call) instanceof JumpInsnNode exit)||exit.getOpcode()!=IFEQ||m.instructions.indexOf(exit.label)<=at)continue;
                // Keep the original control-flow frames and iterator initialization valid.
                m.instructions.insertBefore(call,new InsnNode(POP));m.instructions.set(call,new InsnNode(ICONST_0));loops++;return;
            }
        }
        // Another reference in the same already-disabled loop needs no additional change.
        for(var n=field.getPrevious();n!=null;n=n.getPrevious()) if(n.getOpcode()==ICONST_0 && next(n) instanceof JumpInsnNode j && j.getOpcode()==IFEQ && m.instructions.indexOf(j.label)>at)return;
        throw new IllegalStateException("Unreviewed potion loop "+m.name);
    }
    static void registry(ClassNode c,MethodNode m) throws Exception {
        for(var n:m.instructions.toArray()) if(n instanceof FieldInsnNode field&&field.getOpcode()==PUTSTATIC&&blocked(field)){
            var f=frames(c,m);int start=m.instructions.indexOf(field);
            while(start>0&&(f[start]==null||f[start].getStackSize()!=0))start--;
            var begin=m.instructions.get(start);
            m.instructions.insertBefore(begin,new InsnNode(ACONST_NULL));erase(m,begin,field);registrations++;
        }
    }
    static boolean patch(ClassNode c) throws Exception {
        // Forge's optional development-time data generator is never loaded in a game/server.
        if(c.name.startsWith(BASE+"datagen/"))return false;
        int before=registrations+consumers+statements+arrays+loops;
        for(MethodNode m:c.methods){
            if(m.name.equals("<clinit>") && FIELDS.containsKey(c.name))registry(c,m);
            boolean creative=c.name.equals(BASE+"item/ModCreativeModeTabs")||c.name.equals(BASE+"OvergearedMod")&&m.name.equals("addCreative");
            boolean startup=c.name.equals(BASE+"OvergearedMod$ClientModEvents")||c.name.equals(BASE+"OvergearedMod$CommonModEvents");
            boolean jei=c.name.equals(BASE+"compat/jei/JEIOvergearedModPlugin");
            // Remove entire registration/catalyst/transfer/category expressions, not just their icons.
            if(jei) for(var n:m.instructions.toArray())if(m.instructions.indexOf(n)>=0&&classMarker(n))statement(c,m,n);
            for(var n:m.instructions.toArray()){
                if(!(n instanceof FieldInsnNode field)||field.getOpcode()!=GETSTATIC||!blocked(field)||m.instructions.indexOf(field)<0)continue;
                if(creative){
                    var f=frames(c,m);var frame=f[m.instructions.indexOf(field)];
                    // NEW ItemStack within a potion loop is stored locally before tab insertion.
                    var a=next(next(next(field)));
                    boolean newStack=prev(field)!=null&&prev(field).getOpcode()==DUP&&prev(prev(field)) instanceof TypeInsnNode t&&t.desc.equals("net/minecraft/world/item/ItemStack");
                    var after=a==null?null:next(a);
                    if(newStack&&after!=null&&after.getOpcode()==ASTORE){disablePotionLoop(m,field);}
                    else {statement(c,m,field);continue;}
                }
                if(startup||jei&&m.name.equals("registerItemSubtypes")){statement(c,m,field);continue;}
                if((jei&&m.name.equals("generatePotionConversions"))||c.name.contains("/recipe/castcooking/"))
                    if(arrayElement(m,field))continue;
                var get=next(field);var cast=next(get);
                if(!(get instanceof MethodInsnNode call)||!call.owner.equals(RO)||!call.name.equals("get")||!(cast instanceof TypeInsnNode type)||type.getOpcode()!=CHECKCAST)
                    throw new IllegalStateException("Unreviewed registry consumer "+c.name+"."+m.name+" "+field.name);
                var use=next(cast);
                if(use instanceof MethodInsnNode is&&is.owner.equals("net/minecraft/world/item/ItemStack")&&is.name.equals("m_150930_")){
                    m.instructions.insertBefore(field,new InsnNode(POP));m.instructions.insertBefore(field,new InsnNode(ICONST_0));
                    erase(m,field,use.getNext());
                }else{
                    // Unreachable removed outputs are empty new ItemStacks, never aliases to a retained item.
                    // Datagen and the removed machines remain loadable without dereferencing missing RegistryObjects.
                    String owner,fieldName,desc;
                    if(type.desc.equals("net/minecraft/world/level/block/Block")){owner="net/minecraft/world/level/block/Blocks";fieldName="f_50016_";desc="Lnet/minecraft/world/level/block/Block;";}
                    else if(type.desc.equals("net/minecraft/world/item/Item")||type.desc.equals("net/minecraft/world/level/ItemLike")){owner="net/minecraft/world/item/Items";fieldName="f_41852_";desc="Lnet/minecraft/world/item/Item;";}
                    else if(Set.of("net/minecraft/world/level/block/entity/BlockEntityType","net/minecraft/world/inventory/MenuType").contains(type.desc)){
                        m.instructions.insertBefore(field,new InsnNode(ACONST_NULL));erase(m,field,cast.getNext());consumers++;continue;
                    }
                    else throw new IllegalStateException("Unhandled removed consumer type "+c.name+"."+m.name+" "+type.desc);
                    m.instructions.insertBefore(field,new FieldInsnNode(GETSTATIC,owner,fieldName,desc));erase(m,field,cast.getNext());
                }
                consumers++;
            }
        }
        return before!=registrations+consumers+statements+arrays+loops;
    }
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[1]);int classes=0;
        try(ZipFile z=new ZipFile(args[0])){
            for(var e:Collections.list(z.entries())){
                if(!e.getName().endsWith(".class"))continue;
                ClassNode c=new ClassNode();new ClassReader(z.getInputStream(e).readAllBytes()).accept(c,ClassReader.EXPAND_FRAMES);
                if(!patch(c))continue;
                ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);byte[] raw=w.toByteArray();
                ClassNode check=new ClassNode();new ClassReader(raw).accept(check,0);
                for(var m:check.methods)if((m.access&(ACC_ABSTRACT|ACC_NATIVE))==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name,m);
                Path p=output.resolve(e.getName());Files.createDirectories(p.getParent());Files.write(p,raw);classes++;
            }
        }
        if(registrations!=12)throw new IllegalStateException("Expected 8 item, 2 block, 2 block-entity and 2 menu paths (12 explicit holders), got "+registrations);
        System.out.println("PASS native removal: "+registrations+" registrations, "+consumers+" consumers, "+statements+" whole statements, "+arrays+" arrays, "+loops+" disabled potion loops, "+classes+" verified classes");
    }
}
