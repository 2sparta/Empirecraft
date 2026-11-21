/* SPDX-License-Identifier: LGPL-3.0-only */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Connect the upstream overlay to the JourneyMap button bounds and canvas transform. */
public final class PlaceToggle {
    private static final String VIEW = "me/mss1r/recruitsrtscommand/api/WorldMapView";

    public static void main(String[] args) throws Exception {
        var overlay = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(overlay, 0);
        int callSites = 0;
        for (var method : overlay.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals(overlay.name) && call.name.equals("toggleLeft")) {
                    var oldTop = call.getNext();
                    if (!(oldTop instanceof IntInsnNode number) || number.operand != 10) {
                        throw new IllegalStateException("Unexpected RTS button position in " + method.name);
                    }
                    method.instructions.set(oldTop, new FieldInsnNode(Opcodes.GETSTATIC, VIEW, "TOGGLE_TOP", "I"));
                    callSites++;
                }
            }
        }
        if (callSites != 5) throw new IllegalStateException("Unexpected RTS button call sites: " + callSites);

        var left = overlay.methods.stream().filter(method -> method.name.equals("toggleLeft")).findFirst().orElseThrow();
        left.localVariables = null;
        var storeWidth = Arrays.stream(left.instructions.toArray())
                .filter(op -> op.getOpcode() == Opcodes.ISTORE).findFirst().orElseThrow();
        for (var instruction = storeWidth.getNext(); instruction != null; ) {
            var next = instruction.getNext();
            left.instructions.remove(instruction);
            instruction = next;
        }
        left.instructions.add(new VarInsnNode(Opcodes.ILOAD, ((VarInsnNode) storeWidth).var));
        left.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, VIEW, "toggleLeft", "(I)I", false));
        left.instructions.add(new InsnNode(Opcodes.IRETURN));

        for (String axis : new String[]{"worldToScreenX", "worldToScreenY"}) {
            var method = overlay.methods.stream().filter(candidate -> candidate.name.equals(axis)).findFirst().orElseThrow();
            method.instructions.clear();
            method.localVariables = null;
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, overlay.name, "view", "L" + VIEW + ";"));
            method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 1));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VIEW, axis, "(D)D", false));
            method.instructions.add(new InsnNode(Opcodes.DRETURN));
        }

        var output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        overlay.accept(output);
        Files.write(Path.of(args[1]), output.toByteArray());
    }
}
