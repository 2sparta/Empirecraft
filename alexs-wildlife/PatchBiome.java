import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class PatchBiome {
    public static void main(String[] args)throws Exception{
        ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(c,0);int edits=0;
        for(var m:c.methods)if(m.name.equals("modify"))for(var n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){
            var code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,3));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"ua/empirecraft/wildlife/BiomeFilter","prune","(Lnet/minecraftforge/common/world/ModifiableBiomeInfo$BiomeInfo$Builder;)V",false));m.instructions.insertBefore(n,code);edits++;
        }
        for(var m:c.methods)if(m.name.equals("m_142674_")){
            var code=new InsnList();var ok=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"ua/empirecraft/wildlife/BiomeFilter","antFeature","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",false));code.add(new JumpInsnNode(Opcodes.IFNE,ok));code.add(new InsnNode(Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));code.add(ok);code.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(code);edits++;
        }
        if(edits!=1)throw new AssertionError("Unexpected native biome modifier: "+edits);
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(out);Files.write(Path.of(args[1]),out.toByteArray());
    }
}
