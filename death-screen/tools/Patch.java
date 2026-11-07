import java.nio.file.*;
import java.util.zip.ZipFile;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Surgical SRG overlay, with strict counts so an upstream update cannot silently mispatch. */
public final class Patch {
    static final String OWNER = "com/epicdeathscreen/client/CinematicDeathScreen";
    static final String HELPER = "com/epicdeathscreen/client/EmpirecraftDeaths";
    static final String CUE = "Lcom/epicdeathscreen/client/SceneCue;";
    static void require(boolean ok) { if (!ok) throw new IllegalStateException("Unexpected upstream screen layout"); }
    static InsnList capture() {
        var code = new InsnList(); code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "capture", "()Ljava/lang/String;", false));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, "empirecraft$sound", "Ljava/lang/String;")); return code;
    }
    static InsnList start() {
        var code = new InsnList(); code.add(new VarInsnNode(Opcodes.ALOAD, 0)); code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "empirecraft$sound", "Ljava/lang/String;"));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "start", "(Ljava/lang/String;)"+CUE, false));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, "empirecraft$cue", CUE)); return code;
    }
    static void save(Path out, ClassNode type) throws Exception {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);
        Path path = out.resolve(type.name+".class"); Files.createDirectories(path.getParent()); Files.write(path,writer.toByteArray());
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[1]);
        try (var jar = new ZipFile(args[0])) {
            var screen = new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry(OWNER+".class"))).accept(screen,0);
            screen.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"empirecraft$sound","Ljava/lang/String;",null,null));
            screen.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"empirecraft$cue",CUE,null,null));
            int constructors=0,phrases=0,starts=0,stops=0;
            for(var method:screen.methods) {
                int playCalls=0;
                for(var insn:method.instructions.toArray()) {
                    if(method.name.equals("<init>") && insn.getOpcode()==Opcodes.RETURN) {method.instructions.insertBefore(insn,capture());constructors++;}
                    if(insn instanceof MethodInsnNode call && call.owner.equals("com/epicdeathscreen/client/ClientFiles") && call.name.equals("phrase")) {call.owner=HELPER;phrases++;}
                    if(method.name.equals("m_7856_") && insn instanceof MethodInsnNode call && call.owner.equals(OWNER) && call.name.equals("play") && ++playCalls==2) {method.instructions.insert(insn,start());starts++;}
                }
                if(method.name.equals("stopAudio")) {
                    var code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new FieldInsnNode(Opcodes.GETFIELD,OWNER,"empirecraft$cue",CUE));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"stop","("+CUE+")V",false));method.instructions.insert(code);stops++;
                }
            }
            require(constructors==1 && phrases==1 && starts==1 && stops==1);save(out,screen);
            var files=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry("com/epicdeathscreen/client/ClientFiles.class"))).accept(files,0);
            int reloads=0;
            for(var method:files.methods) if(method.name.equals("reload")) for(var insn:method.instructions.toArray()) if(insn.getOpcode()==Opcodes.RETURN) {
                method.instructions.insertBefore(insn,new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"ensureUkrainianFile","()V",false));reloads++;
            }
            require(reloads==1);save(out,files);
            System.out.println("DEATH_SCREEN_PATCH_PASS constructor=1 phrase=1 start=1 stop=1 locale=1");
        }
    }
}
