import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class Patch {
    public static void main(String[] args)throws Exception{
        String kind=args[0];ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[1]))).accept(c,0);int changed=0;
        if(kind.equals("items")){for(MethodNode m:c.methods)for(var instruction:m.instructions.toArray())if(instruction instanceof MethodInsnNode call&&call.owner.equals("net/minecraftforge/registries/DeferredRegister")&&call.name.equals("register")&&call.desc.equals("(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;")){
            call.setOpcode(Opcodes.INVOKESTATIC);call.owner="net/mcreator/hamzabusottomanmod/empirecraft/SteelAliases";call.name="register";call.desc="(Lnet/minecraftforge/registries/DeferredRegister;Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;";changed++;
        }}
        else if(kind.equals("noop")){for(MethodNode m:c.methods)if(m.name.equals("execute")){m.instructions.clear();m.instructions.add(new InsnNode(Opcodes.RETURN));m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.maxStack=0;changed++;}}
        else if(kind.equals("mace")){changed=c.methods.removeIf(m->m.name.equals("m_7579_")||m.name.equals("m_7373_"))?1:0;}
        if(changed==0)throw new IllegalStateException("No native patch matched "+kind+" "+c.name);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);Files.write(Path.of(args[2]),writer.toByteArray());System.out.println(kind+" "+c.name+" changes="+changed);
    }
}
