import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Correct the pinned migration cursor's six inclusive-maximum operations. */
public final class PatchMassBounds {
    public static void main(String[] args)throws Exception{
        ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node,0);
        int positions=0,sections=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions.toArray()){
            if(!(instruction instanceof MethodInsnNode call) || !call.owner.equals("org/joml/primitives/AABBic")
                    || !call.name.matches("max[XYZ]") || !call.desc.equals("()I"))continue;
            if(method.name.equals("advance")){
                AbstractInsnNode next=call.getNext();while(next!=null && next.getOpcode()<0)next=next.getNext();
                if(!(next instanceof JumpInsnNode jump) || jump.getOpcode()!=Opcodes.IF_ICMPGT)
                    throw new IllegalStateException("Unexpected pinned block bounds comparison");
                jump.setOpcode(Opcodes.IF_ICMPGE);positions++;
            }else if(method.name.equals("nextSection")){
                InsnList subtract=new InsnList();subtract.add(new InsnNode(Opcodes.ICONST_1));subtract.add(new InsnNode(Opcodes.ISUB));
                method.instructions.insert(call,subtract);sections++;
            }
        }
        if(positions!=3 || sections!=3)throw new IllegalStateException("Pinned cursor changed: "+positions+"/"+sections);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);
        Files.write(Path.of(args[1]),writer.toByteArray());
        System.out.println("MASS_BOUNDS_PATCH_PASS blockComparisons="+positions+" sectionLimits="+sections);
    }
}
