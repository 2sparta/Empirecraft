import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Pinned, fail-closed removal of Spawn registrations and their executable entry points. */
public final class PatchSpawn implements Opcodes {
    static final String ROOT="com/ninni/spawn/", REG=ROOT+"registry/";
    static final Set<String> ITEMS=new HashSet<>(), BLOCKS=new HashSet<>();
    static final Set<String> MOBS=Set.of("STRANDED","FLUKESHROOM","BARBED","BARB","STICKBUG","FIREKEEPER");
    static final Map<String,Set<String>> CUT=new HashMap<>();
    static final Map<String,byte[]> classes=new HashMap<>();
    static final List<String> report=new ArrayList<>();
    static {
        for(String name:("sunstone moonstone heart_of_the_sun heliostone heliostone_wall heliostone_slab heliostone_stairs unstable_heliostone magmatic_splatter blasted_sand blasted_sandstone chiseled_blasted_sandstone blasted_sandstone_wall blasted_sandstone_slab blasted_sandstone_stairs cut_blasted_sandstone cut_blasted_sandstone_slab smooth_blasted_sandstone smooth_blasted_sandstone_slab smooth_blasted_sandstone_stairs zombified_flower barnacle barnacle_cluster").split(" "))BLOCKS.add(name.toUpperCase(Locale.ROOT));
        ITEMS.addAll(BLOCKS);
        for(String name:"blue_footed_boots firekeeper_spawn_egg stranded_spawn_egg flukeshroom_spawn_egg barbed_spawn_egg stickbug_spawn_egg baby_flukeshroom_bucket suspicious_stick".split(" "))ITEMS.add(name.toUpperCase(Locale.ROOT));
        CUT.put(REG+"SpawnItems",ITEMS);CUT.put(REG+"SpawnBlocks",BLOCKS);CUT.put(REG+"SpawnEntityType",MOBS);
        CUT.put(REG+"SpawnBlockEntityTypes",Set.of("HEART_OF_THE_SUN"));
        CUT.put(REG+"SpawnFeatures",Set.of("ZOMBIFIED_FLOWER","STRANDED_SPAWNS"));
        CUT.put(REG+"SpawnStructureTypes",Set.of("VOLCANIC_ISLAND"));
        CUT.put(REG+"SpawnStructurePieceTypes",Set.of("VOLCANIC_ISLAND_PIECE"));
        CUT.put(REG+"SpawnBiomes",Set.of("VOLCANIC_ISLAND"));
        CUT.put(REG+"SpawnCriteriaTriggers",Set.of("DISCOVER_STICKBUG"));
    }
    static boolean cut(AbstractInsnNode n){return n instanceof FieldInsnNode f && f.getOpcode()==GETSTATIC && CUT.getOrDefault(f.owner,Set.of()).contains(f.name);}
    static AbstractInsnNode next(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
    static void erase(MethodNode m,AbstractInsnNode start,AbstractInsnNode end){
        for(AbstractInsnNode n=start;n!=end;){AbstractInsnNode following=n.getNext();if(n.getOpcode()>=0)m.instructions.remove(n);n=following;}
    }
    static void empty(MethodNode m){
        if(Type.getReturnType(m.desc).getSort()!=Type.VOID)throw new IllegalStateException("Not void: "+m.name);
        m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
        m.instructions.add(new InsnNode(RETURN));report.add("disable "+m.name+m.desc);
    }
    static Frame<BasicValue>[] frames(ClassNode c,MethodNode m)throws Exception{return new Analyzer<BasicValue>(new BasicInterpreter()).analyze(c.name,m);}
    static void statements(ClassNode c,MethodNode m)throws Exception {
        // Every selected call is an independent straight-line Java statement. Keep labels/frames.
        boolean again=true;
        while(again){again=false;AbstractInsnNode[] a=m.instructions.toArray();Frame<BasicValue>[] f=frames(c,m);
            for(int i=0;i<a.length;i++)if(cut(a[i])){
                int s=i;while(s>0&&(f[s]==null||f[s].getStackSize()!=0))s--;
                int e=i+1;while(e<a.length&&(f[e]==null||f[e].getStackSize()!=0))e++;
                if(e==a.length)throw new IllegalStateException("Unclosed statement "+c.name+"."+m.name);
                for(int j=s;j<e;j++)if(a[j] instanceof JumpInsnNode||a[j] instanceof TableSwitchInsnNode||a[j] instanceof LookupSwitchInsnNode)throw new IllegalStateException("Nonlinear statement "+c.name+"."+m.name);
                report.add("statement "+c.name+"."+m.name+" / "+((FieldInsnNode)a[i]).name);
                erase(m,a[s],a[e]);again=true;break;
            }
        }
    }
    static void registrations(ClassNode c,MethodNode m)throws Exception {
        AbstractInsnNode[] a=m.instructions.toArray();Frame<BasicValue>[] f=frames(c,m);
        for(int i=a.length-1;i>=0;i--)if(a[i] instanceof FieldInsnNode field&&field.getOpcode()==PUTSTATIC&&CUT.getOrDefault(c.name,Set.of()).contains(field.name)){
            int s=i;while(s>0&&(f[s]==null||f[s].getStackSize()!=0))s--;
            erase(m,a[s],a[i].getNext());report.add("unregister "+c.name+"."+field.name);
        }
    }
    static void skipBranch(ClassNode c,MethodNode m,String field)throws Exception {
        AbstractInsnNode[] a=m.instructions.toArray();int target=-1;
        for(int i=0;i<a.length;i++)if(a[i] instanceof FieldInsnNode f&&f.name.equals(field)&&cut(f)){target=i;break;}
        if(target<0)throw new IllegalStateException("Missing branch "+c.name+"."+m.name+"/"+field);
        for(int i=target-1;i>=0;i--)if(a[i] instanceof JumpInsnNode j&&j.getOpcode()!=GOTO&&j.getOpcode()!=JSR&&m.instructions.indexOf(j.label)>target){
            InsnList replacement=new InsnList();int op=j.getOpcode();replacement.add(new InsnNode(op>=IF_ICMPEQ&&op<=IF_ACMPNE?POP2:POP));replacement.add(new JumpInsnNode(GOTO,j.label));m.instructions.insertBefore(j,replacement);m.instructions.remove(j);
            report.add("skip branch "+c.name+"."+m.name+" / "+field);return;
        }
        throw new IllegalStateException("No enclosing branch "+c.name+"."+m.name);
    }
    static boolean match(ClassNode c,String suffix){return c.name.equals(ROOT+suffix);}
    static byte[] patch(byte[] input)throws Exception{
        ClassNode c=new ClassNode();new ClassReader(input).accept(c,0);
        for(MethodNode m:c.methods){
            if(m.name.equals("<clinit>")&&CUT.containsKey(c.name))registrations(c,m);
            if(match(c,"server/events/ModEvents")&&Set.of("registerEntityAttribute","registerSpawnPlacements","registerDispenserBehaviors").contains(m.name))statements(c,m);
            if(match(c,"server/events/ModEvents")&&Set.of("itemToss","openInventory").contains(m.name))empty(m);
            if(match(c,"mixin/OverworldBiomeBuilderMixin")&&m.name.equals("S$redirectAddSurfaceBiome"))statements(c,m);
            if(match(c,"server/CommonProxy")&&m.name.equals("registerFluidInteractions"))statements(c,m);
            if(match(c,"client/ClientProxy")&&m.name.equals("registerItemModelPredicates"))statements(c,m);
            if(match(c,"client/events/ClientEvents")&&m.name.equals("registerEntityRenderers"))statements(c,m);
            if(match(c,"compat/jei/JEIPlugin")&&m.name.equals("registerRecipeCatalysts"))statements(c,m);
            if(match(c,"server/level/feature/SmallBoulderFeature")&&m.name.equals("populateBarnacles"))empty(m);
            if(match(c,"server/level/feature/DeepReefRockFeature")&&m.name.equals("scatterBarnacles"))empty(m);
            if(match(c,"server/level/feature/DeepReefRockFeature")&&m.name.equals("createStoneBoulder"))skipBranch(c,m,"BARNACLE_CLUSTER");
            if(match(c,"server/block/RedAlgaeBlock")&&m.name.equals("populateAbove"))skipBranch(c,m,"MOONSTONE");
            if(match(c,"server/entity/mob/SpiderCrab")&&m.name.equals("m_6518_"))skipBranch(c,m,"BARBED");
            if(match(c,"mixin/EntityMixin")&&m.name.equals("S$walkingStepSound"))empty(m);
            if(match(c,"mixin/LivingEntityMixin")&&m.name.equals("S$jumpFromGround"))empty(m);
            if(match(c,"mixin/MobMixin")&&Set.of("S$Sunlight","S$Moonlight").contains(m.name))empty(m);
            // Creative varargs retain the order of surviving neighbours; nulls are filtered below.
            if(match(c,"client/events/CreativeModeTabEvents")&&Set.of("addAfter","addBefore").contains(m.name)&&m.desc.endsWith("Lnet/minecraft/world/level/ItemLike;)V")){
                InsnList guard=new InsnList();LabelNode ok=new LabelNode();guard.add(new VarInsnNode(ALOAD,2));guard.add(new JumpInsnNode(IFNONNULL,ok));guard.add(new InsnNode(RETURN));guard.add(ok);m.instructions.insert(guard);
            }
            for(AbstractInsnNode n:m.instructions.toArray())if(cut(n)){
                FieldInsnNode field=(FieldInsnNode)n;
                if(match(c,"registry/SpawnCreativeModeTab")&&field.name.equals("ZOMBIFIED_FLOWER")){field.name="SUNFLOWER";continue;}
                // Remaining checks compare ItemStack/BlockState/type identity with an absent object.
                // No registry aliases or substitute items/blocks are introduced.
                AbstractInsnNode get=next(n);
                if(get instanceof MethodInsnNode call&&call.owner.equals("net/minecraftforge/registries/RegistryObject")&&call.name.equals("get")){
                    m.instructions.set(n,new InsnNode(ACONST_NULL));m.instructions.remove(get);report.add("absent check "+c.name+"."+m.name+" / "+field.name);
                }
            }
        }
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){
            protected String getCommonSuperClass(String a,String b){return common(a,b);}
        };c.accept(out);return out.toByteArray();
    }
    static final Map<String,ClassNode> hierarchy=new HashMap<>();
    static ClassNode info(String name){return hierarchy.computeIfAbsent(name,n->{
        byte[] data=classes.get(n+".class");
        if(data==null)try(InputStream in=ClassLoader.getSystemResourceAsStream(n+".class")){if(in!=null)data=in.readAllBytes();}catch(IOException e){throw new UncheckedIOException(e);}
        if(data==null)throw new IllegalStateException("Missing hierarchy class "+n);
        ClassNode c=new ClassNode();new ClassReader(data).accept(c,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return c;
    });}
    static boolean assign(String a,String b){if(a.equals(b)||a.equals("java/lang/Object"))return true;ClassNode c=info(b);if(c.superName!=null&&assign(a,c.superName))return true;for(String i:c.interfaces)if(assign(a,i))return true;return false;}
    static String common(String a,String b){if(a.equals(b))return a;if(a.startsWith("[")||b.startsWith("["))return "java/lang/Object";if(assign(a,b))return a;if(assign(b,a))return b;if((info(a).access&ACC_INTERFACE)!=0||(info(b).access&ACC_INTERFACE)!=0)return "java/lang/Object";do{a=info(a).superName;}while(a!=null&&!assign(a,b));return a==null?"java/lang/Object":a;}
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]),output=Path.of(args[1]);
        for(String line:Files.readAllLines(Path.of(args[2])))if(!line.isBlank())try(ZipFile z=new ZipFile(line)){for(ZipEntry e:Collections.list(z.entries()))if(e.getName().endsWith(".class"))try(InputStream in=z.getInputStream(e)){classes.putIfAbsent(e.getName(),in.readAllBytes());}}
        try(ZipFile z=new ZipFile(input.toFile())){for(ZipEntry e:Collections.list(z.entries()))if(e.getName().endsWith(".class"))try(InputStream in=z.getInputStream(e)){classes.put(e.getName(),in.readAllBytes());}}
        // Only classes with registrations or live references are rewritten, preserving all other bytes.
        for(var entry:classes.entrySet())if(entry.getKey().startsWith(ROOT)&&!entry.getKey().contains("/server/entity/mob/Flukeshroom")&&!entry.getKey().contains("/server/entity/mob/Stickbug")){
            String name=entry.getKey();boolean selected=CUT.containsKey(name.replace(".class",""))||Set.of("server/events/ModEvents","server/CommonProxy","client/ClientProxy","client/events/ClientEvents","client/events/ClientForgeEvents","client/events/CreativeModeTabEvents","server/events/ForgeEvents","compat/jei/JEIPlugin","registry/SpawnCreativeModeTab","server/level/feature/SmallBoulderFeature","server/level/feature/DeepReefRockFeature","server/block/RedAlgaeBlock","server/entity/mob/SpiderCrab","server/network/ChargedJumpPacket","mixin/EntityMixin","mixin/LivingEntityMixin","mixin/MobMixin","mixin/OverworldBiomeBuilderMixin","mixin/client/HumanoidModelMixin").contains(name.substring(ROOT.length()).replace(".class",""));
            if(selected){Path path=output.resolve(name);Files.createDirectories(path.getParent());Files.write(path,patch(entry.getValue()));}
        }
        Collections.sort(report);Files.write(output.resolve("patch-report.txt"),report);System.out.println("Native Spawn bytecode changes: "+report.size());
    }
}
