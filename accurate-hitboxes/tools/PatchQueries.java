import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Only discovery changes; Accurate Hitboxes retains its actual OBB ray tests. */
public final class PatchQueries {
    public static void main(String[] args)throws Exception{
        ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node,0);int changed=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call
                && call.name.equals("m_6249_") && call.desc.equals("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")){
            call.setOpcode(Opcodes.INVOKESTATIC);call.owner="net/devra/accuratehitboxes/empirecraft/WhaleCandidates";call.name="query";call.itf=false;
            call.desc="(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;";changed++;
        }
        if(changed!=1)throw new AssertionError(node.name+" query sites="+changed);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);Files.write(Path.of(args[1]),writer.toByteArray());System.out.println("WHALE_QUERY_PATCH_PASS "+node.name);
    }
}
