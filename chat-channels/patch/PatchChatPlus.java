import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Exact, guarded changes to the installed ChatPlus 2.8.1. Unrelated UI remains upstream. */
public final class PatchChatPlus implements Opcodes {
    private static final String HOOK = "ua/empirecraft/chat/ClientChannels";
    private static final String PREFERENCES = "ua/empirecraft/chat/ClientPreferences";
    private static final String TAB = "com/ebicep/chatplus/features/chattabs/ChatTab";
    private static final String SCREEN = "Lnet/minecraft/client/gui/screens/Screen;";
    private static final String EVENT_RESULT = "dev/architectury/event/EventResult";
    private static MethodNode method(ClassNode c, String name, String desc) {
        return c.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
            .orElseThrow(() -> new IllegalStateException(c.name + "." + name + desc));
    }
    private static void body(MethodNode m, AbstractInsnNode... instructions) {
        m.instructions.clear(); m.tryCatchBlocks.clear();
        if (m.localVariables != null) m.localVariables.clear();
        m.visibleLocalVariableAnnotations = null; m.invisibleLocalVariableAnnotations = null;
        for (var instruction : instructions) m.instructions.add(instruction);
        m.maxStack = 8;
    }
    private static MethodInsnNode call(String name, String desc) { return new MethodInsnNode(INVOKESTATIC, HOOK, name, desc, false); }
    private static void closeScreen(ClassNode c) {
        body(method(c, "getConfigScreen", "("+SCREEN+")"+SCREEN), new VarInsnNode(ALOAD, 0), new MethodInsnNode(INVOKESTATIC,PREFERENCES,"screen","("+SCREEN+")"+SCREEN,false), new InsnNode(ARETURN));
    }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        int modified = 0;
        try (JarFile jar = new JarFile(input.toFile())) {
            for (JarEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode c = new ClassNode(); new ClassReader(jar.getInputStream(entry)).accept(c, 0);
                boolean changed = true;
                switch(c.name) {
                    case "com/ebicep/chatplus/config/Config" -> {
                        method(c,"correctValues","()V").instructions.insert(call("configure","()V"));
                    }
                    case TAB -> {
                        body(method(c,"getName","()Ljava/lang/String;"), new VarInsnNode(ALOAD,0), call("label","(L"+TAB+";)Ljava/lang/String;"), new InsnNode(ARETURN));
                        // The existing getter measures the translated getName() and updates xEnd.
                        // Its upstream cache otherwise survives language/font resource reloads.
                        var refreshWidth=new InsnList();refreshWidth.add(new VarInsnNode(ALOAD,0));refreshWidth.add(new InsnNode(ICONST_M1));refreshWidth.add(new FieldInsnNode(PUTFIELD,TAB,"width","I"));
                        method(c,"getWidth","()I").instructions.insert(refreshWidth);
                        body(method(c,"matches","(Lnet/minecraft/network/chat/Component;)Z"), new VarInsnNode(ALOAD,0),new VarInsnNode(ALOAD,1),call("matches","(L"+TAB+";Lnet/minecraft/network/chat/Component;)Z"),new InsnNode(IRETURN));
                    }
                    case "com/ebicep/chatplus/features/chattabs/ChatTabs" -> {
                        var m = method(c,"_init_$lambda$9","(Lcom/ebicep/chatplus/hud/ChatScreenSendMessagePreEvent;)Lkotlin/Unit;");
                        body(m,new VarInsnNode(ALOAD,0),call("send","(Lcom/ebicep/chatplus/hud/ChatScreenSendMessagePreEvent;)V"),new FieldInsnNode(GETSTATIC,"kotlin/Unit","INSTANCE","Lkotlin/Unit;"),new InsnNode(ARETURN));
                    }
                    case "com/ebicep/chatplus/config/ConfigScreen" -> {
                        body(method(c,"setOpen","(Z)V"),new VarInsnNode(ILOAD,1),new MethodInsnNode(INVOKESTATIC,PREFERENCES,"requestOpen","(Z)V",false),new InsnNode(RETURN));
                        body(method(c,"getOpen","()Z"),new InsnNode(ICONST_0),new InsnNode(IRETURN));
                        body(method(c,"handleOpenScreen","()V"),new InsnNode(RETURN));
                        body(method(c,"openConfigScreen","()V"),new InsnNode(ICONST_1),new MethodInsnNode(INVOKESTATIC,PREFERENCES,"requestOpen","(Z)V",false),new InsnNode(RETURN));
                        closeScreen(c);
                    }
                    case "com/ebicep/chatplus/config/forge/ConfigScreenImpl" -> closeScreen(c);
                    case "com/ebicep/chatplus/features/PeekChat" -> {
                        var active=new LabelNode();var guard=new InsnList();guard.add(call("enabled","()Z"));guard.add(new JumpInsnNode(IFNE,active));
                        guard.add(new InsnNode(ICONST_0));guard.add(new InsnNode(IRETURN));guard.add(active);guard.add(new FrameNode(F_SAME,0,null,0,null));
                        method(c,"getPeeking","()Z").instructions.insert(guard);
                    }
                    default -> changed = false;
                }
                // Architectury raw-input callbacks bypass ChatPlus's own guarded EventBus.
                // Returning PASS also preserves input for Minecraft and other mods while disabled.
                for(var m:c.methods)if(m.desc.endsWith(")L"+EVENT_RESULT+";")&&(m.access&(ACC_ABSTRACT|ACC_NATIVE))==0){
                    var active=new LabelNode();var guard=new InsnList();
                    guard.add(call("enabled","()Z"));guard.add(new JumpInsnNode(IFNE,active));
                    guard.add(new MethodInsnNode(INVOKESTATIC,EVENT_RESULT,"pass","()L"+EVENT_RESULT+";",false));guard.add(new InsnNode(ARETURN));
                    guard.add(active);guard.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(guard);changed=true;
                }
                if (!changed) continue;
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                c.accept(writer); byte[] bytes = writer.toByteArray();
                ClassNode verified = new ClassNode(); new ClassReader(bytes).accept(verified, ClassReader.SKIP_DEBUG);
                for (var m : verified.methods) if ((m.access & (ACC_ABSTRACT|ACC_NATIVE))==0) new Analyzer<>(new BasicVerifier()).analyze(c.name,m);
                Path path = output.resolve(entry.getName()); Files.createDirectories(path.getParent()); Files.write(path,bytes); modified++;
            }
        }
        if (modified != 9) throw new IllegalStateException("Expected nine exact class patches, got " + modified);
        System.out.println("PASS guarded ChatPlus patches + ASM BasicVerifier: " + modified + " classes");
    }
}
