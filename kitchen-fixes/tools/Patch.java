import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

/** Small overlays on the exact installed binaries; all existing native exclusions survive. */
public final class Patch {
    static final String HEAT="ua/empirecraft/kitchen/heat/HeatRules";
    static final String KETTLE="umpaz/farmersrespite/common/block/entity/KettleBlockEntity";
    static final String RL="Lnet/minecraft/resources/ResourceLocation;";
    static int changes;
    static MethodInsnNode call(String owner,String name,String desc){return new MethodInsnNode(INVOKESTATIC,owner,name,desc,false);}
    static AbstractInsnNode last(MethodNode m,int opcode){for(var n=m.instructions.getLast();n!=null;n=n.getPrevious())if(n.getOpcode()==opcode)return n;throw new IllegalStateException(m.name);}
    static void patch(ClassNode c) {
        if(c.name.equals(KETTLE)) {
            c.fields.add(new FieldNode(ACC_PRIVATE,"empirecraft$pouringRecipe",RL,null,null));
            var changed=new MethodNode(ACC_PUBLIC|ACC_SYNTHETIC,"empirecraft$fluidChanged","()V",null,null);
            changed.instructions.add(new VarInsnNode(ALOAD,0));changed.instructions.add(new InsnNode(ICONST_1));changed.instructions.add(new FieldInsnNode(PUTFIELD,KETTLE,"checkNewRecipe","Z"));
            changed.instructions.add(new VarInsnNode(ALOAD,0));changed.instructions.add(new InsnNode(ACONST_NULL));changed.instructions.add(new FieldInsnNode(PUTFIELD,KETTLE,"lastRecipeID",RL));
            changed.instructions.add(new VarInsnNode(ALOAD,0));changed.instructions.add(new InsnNode(ICONST_0));changed.instructions.add(new FieldInsnNode(PUTFIELD,KETTLE,"brewTime","I"));
            changed.instructions.add(new InsnNode(RETURN));c.methods.add(changed);
            for(var m:c.methods)if(m.name.equals("getPouringRecipe"))for(var n:m.instructions.toArray())
                if(n instanceof FieldInsnNode f&&f.owner.equals(KETTLE)&&f.name.equals("lastRecipeID")){f.name="empirecraft$pouringRecipe";changes++;}
        }
        if(c.name.equals(KETTLE+"$2"))for(var m:c.methods)if(m.name.equals("onContentsChanged")) {
            var x=new InsnList();
            x.add(new VarInsnNode(ALOAD,0));x.add(new FieldInsnNode(GETFIELD,c.name,"this$0","L"+KETTLE+";"));
            x.add(new MethodInsnNode(INVOKEVIRTUAL,KETTLE,"empirecraft$fluidChanged","()V",false));
            m.instructions.insertBefore(last(m,RETURN),x);changes++;
        }
        if(c.name.equals("sfiomn/legendarysurvivaloverhaul/common/temperature/BlockModifier"))for(var m:c.methods) {
            if(m.name.equals("getTemperatureFromSpreadPoint")) {
                int state=-1;
                for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode a&&a.name.equals("blockState")) {
                    var next=n.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
                    if(next instanceof VarInsnNode v&&v.getOpcode()==ASTORE)state=v.var;
                }
                if(state<0)throw new IllegalStateException("Block state local not found");
                var x=new InsnList();x.add(new VarInsnNode(ALOAD,state));x.add(call(HEAT,"source","(FLnet/minecraft/world/level/block/state/BlockState;)F"));
                m.instructions.insertBefore(last(m,FRETURN),x);changes++;
            }
            if(m.name.equals("getWorldInfluence")) {
                for(var n:m.instructions.toArray())if(n.getOpcode()==FRETURN) {
                    var x=new InsnList();
                    x.add(new VarInsnNode(ALOAD,0));x.add(new FieldInsnNode(GETFIELD,c.name,"coldestValue","F"));
                    x.add(new VarInsnNode(ALOAD,0));x.add(new FieldInsnNode(GETFIELD,c.name,"coldTotal","F"));
                    x.add(call(HEAT,"finish","(FFF)F"));m.instructions.insertBefore(n,x);
                }
                var x=new InsnList();var normal=new LabelNode();
                x.add(call(HEAT,"baseline","()Z"));x.add(new JumpInsnNode(IFEQ,normal));x.add(call(HEAT,"cold","()F"));x.add(new InsnNode(FRETURN));
                x.add(normal);x.add(new FrameNode(F_SAME,0,null,0,null));x.add(call(HEAT,"begin","()V"));m.instructions.insert(x);changes++;
            }
        }
        if(c.name.equals("sfiomn/legendarysurvivaloverhaul/util/internal/TemperatureUtilInternal"))for(var m:c.methods) {
            if(m.name.equals("getPlayerTargetTemperature"))for(var n:m.instructions.toArray())if(n.getOpcode()==FRETURN) {
                var x=new InsnList();x.add(new VarInsnNode(ALOAD,1));x.add(call(HEAT,"playerTarget","(FLnet/minecraft/world/entity/player/Player;)F"));m.instructions.insertBefore(n,x);changes++;
            }
            if(m.name.equals("getWorldTemperature"))for(var n:m.instructions.toArray())if(n.getOpcode()==FRETURN) {
                var x=new InsnList();x.add(new VarInsnNode(ALOAD,1));x.add(new VarInsnNode(ALOAD,2));x.add(call(HEAT,"worldTarget","(FLnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)F"));m.instructions.insertBefore(n,x);changes++;
            }
        }
        if(c.name.equals("net/dries007/tfc/common/items/TFCItems"))for(var m:c.methods)
            if(m.name.equals("register")&&m.desc.startsWith("(Ljava/lang/String;")&&m.desc.endsWith("Lnet/minecraftforge/registries/RegistryObject;")) {
                var x=new InsnList();var normal=new LabelNode();
                x.add(new LdcInsnNode("food/cooked_egg"));x.add(new VarInsnNode(ALOAD,0));
                x.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","equalsIgnoreCase","(Ljava/lang/String;)Z",false));x.add(new JumpInsnNode(IFEQ,normal));
                x.add(call("ua/empirecraft/kitchen/egg/EggHooks","alias","()Lnet/minecraftforge/registries/RegistryObject;"));x.add(new InsnNode(ARETURN));
                x.add(normal);x.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(x);changes++;
            }
    }
    public static void main(String[] args)throws Exception {
        var c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(c,0);patch(c);
        if(changes==0)throw new IllegalStateException("No patch for "+c.name);
        var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Path.of(args[1]),w.toByteArray());
        System.out.println(c.name+" patches="+changes);
    }
}
