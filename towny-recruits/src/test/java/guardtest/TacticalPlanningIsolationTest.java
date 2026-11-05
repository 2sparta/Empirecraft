package guardtest;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Native createPath mutates cached targets; ensure previews call it on an independent navigation. */
public final class TacticalPlanningIsolationTest {
    public static void main(String[] args) throws Exception {
        ClassNode node = new ClassNode();
        try (var in = TacticalPlanningIsolationTest.class.getClassLoader().getResourceAsStream(
            "ua/townyrecruits/tactics/TacticalTerrain.class")) { new ClassReader(in.readAllBytes()).accept(node, 0); }
        MethodNode route = node.methods.stream().filter(m -> m.name.equals("routeFrom")).findFirst().orElseThrow();
        Frame<SourceValue>[] frames = new Analyzer<>(new SourceInterpreter()).analyze(node.name, route);
        Map<AbstractInsnNode,Integer> positions = new IdentityHashMap<>();
        for (int i = 0; i < route.instructions.size(); i++) positions.put(route.instructions.get(i), i);
        int tested = 0;
        for (int i = 0; i < route.instructions.size(); i++) {
            if (!(route.instructions.get(i) instanceof MethodInsnNode call) || !call.name.equals("m_26524_")) continue;
            var frame = frames[i]; int receiver = frame.getStackSize() - Type.getArgumentTypes(call.desc).length - 1;
            assert fresh(frame.getStack(receiver), frames, positions, new HashSet<>()) :
                "Preview path search can mutate the recruit's active navigation target";
            tested++;
        }
        assert tested == 1;
        assert route.instructions.iterator().hasNext();
        boolean probe = false;
        for (var insn : route.instructions) {
            if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.endsWith("TacticalTerrain$Probe")) probe = true;
            if (insn instanceof MethodInsnNode call) assert !Set.of("m_6021_", "m_6034_", "m_7678_", "m_6027_").contains(call.name)
                : "Route preview must never reposition the actual recruit/mount";
        }
        assert probe : "Future route legs need an unspawned probe at their virtual starting point";
        System.out.println("PASS production preview path-search receiver originates from a fresh GroundPathNavigation; active navigation targets remain isolated");
    }
    static boolean fresh(SourceValue value, Frame<SourceValue>[] frames, Map<AbstractInsnNode,Integer> positions, Set<AbstractInsnNode> seen) {
        if (value.insns.isEmpty()) return false;
        for (AbstractInsnNode insn : value.insns) {
            if (!seen.add(insn)) return false;
            if (insn instanceof TypeInsnNode type && insn.getOpcode() == Opcodes.NEW
                && type.desc.equals("net/minecraft/world/entity/ai/navigation/GroundPathNavigation")) continue;
            Frame<SourceValue> frame = frames[positions.get(insn)]; SourceValue previous;
            if (insn instanceof VarInsnNode var && var.getOpcode() == Opcodes.ALOAD) previous = frame.getLocal(var.var);
            else if (insn.getOpcode() == Opcodes.ASTORE || insn.getOpcode() == Opcodes.DUP) previous = frame.getStack(frame.getStackSize() - 1);
            else return false;
            if (!fresh(previous, frames, positions, seen)) return false;
        }
        return true;
    }
}
