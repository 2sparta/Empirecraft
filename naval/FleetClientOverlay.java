import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow hooks into the checksum-pinned, previously shipped native client GUI.
 * Existing widgets/rendering remain unchanged; no compile-time Minecraft substitutes.
 */
public final class FleetClientOverlay implements Opcodes {
    static final String UI="ua/empirecraft/townymap/client/FleetUiHooks",MAP="ua/empirecraft/townymap/client/FleetWaypoints";
    static int initHooks,sendHooks,mapHooks,disconnectHooks;
    static MethodInsnNode call(String owner,String name,String desc){return new MethodInsnNode(INVOKESTATIC,owner,name,desc,false);}
    static byte[] screen(byte[] raw){
        ClassNode c=new ClassNode();new ClassReader(raw).accept(c,0);
        for(MethodNode m:c.methods){
            if(m.name.equals("m_7856_")&&m.desc.equals("()V"))for(var instruction:m.instructions.toArray())if(instruction.getOpcode()==RETURN){
                InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(call(UI,"init","(Ljava/lang/Object;)V"));m.instructions.insertBefore(instruction,hook);initHooks++;
            }
            if(m.name.equals("send")&&m.desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/lang/String;I)V")){
                InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new VarInsnNode(ALOAD,2));hook.add(new VarInsnNode(ALOAD,3));hook.add(call(UI,"values","(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;)Ljava/util/List;"));hook.add(new VarInsnNode(ASTORE,3));
                for(int i=0;i<=4;i++)hook.add(new VarInsnNode(ALOAD,i));hook.add(new VarInsnNode(ILOAD,5));
                hook.add(call(UI,"send","(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/lang/String;I)Z"));
                LabelNode proceed=new LabelNode();hook.add(new JumpInsnNode(IFEQ,proceed));hook.add(new InsnNode(RETURN));hook.add(proceed);hook.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(hook);sendHooks++;
            }
        }
        for(String spec:List.of("m_5534_:(CI)Z:typed","m_7933_:(III)Z:key")){
            String[] p=spec.split(":");if(c.methods.stream().anyMatch(m->m.name.equals(p[0])&&m.desc.equals(p[1])))throw new IllegalStateException("Already patched input handler");
            MethodNode m=new MethodNode(ACC_PUBLIC,p[0],p[1],null,null);MethodVisitor v=m;v.visitCode();v.visitVarInsn(ALOAD,0);
            int count=p[2].equals("typed")?2:3;for(int i=1;i<=count;i++)v.visitVarInsn(ILOAD,i);
            String desc=p[2].equals("typed")?"(Ljava/lang/Object;CI)Ljava/lang/Boolean;":"(Ljava/lang/Object;III)Ljava/lang/Boolean;";
            v.visitMethodInsn(INVOKESTATIC,UI,p[2],desc,false);v.visitInsn(DUP);Label fallback=new Label();v.visitJumpInsn(IFNULL,fallback);
            v.visitMethodInsn(INVOKEVIRTUAL,"java/lang/Boolean","booleanValue","()Z",false);v.visitInsn(IRETURN);v.visitLabel(fallback);v.visitFrame(F_SAME1,0,null,1,new Object[]{"java/lang/Boolean"});v.visitInsn(POP);
            v.visitVarInsn(ALOAD,0);for(int i=1;i<=count;i++)v.visitVarInsn(ILOAD,i);v.visitMethodInsn(INVOKESPECIAL,c.superName,p[0],p[1],false);v.visitInsn(IRETURN);v.visitMaxs(0,0);v.visitEnd();c.methods.add(m);
        }
        MethodNode close=new MethodNode(ACC_PUBLIC,"m_7379_","()V",null,null);close.visitCode();close.visitVarInsn(ALOAD,0);close.visitMethodInsn(INVOKESTATIC,UI,"close","(Ljava/lang/Object;)Z",false);Label fallback=new Label();close.visitJumpInsn(IFEQ,fallback);close.visitInsn(RETURN);close.visitLabel(fallback);close.visitFrame(F_SAME,0,null,0,null);close.visitVarInsn(ALOAD,0);close.visitMethodInsn(INVOKESPECIAL,c.superName,"m_7379_","()V",false);close.visitInsn(RETURN);close.visitMaxs(0,0);close.visitEnd();c.methods.add(close);
        if(initHooks<4||sendHooks!=1)throw new IllegalStateException("Unexpected fleet screen bytecode: "+initHooks+"/"+sendHooks);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);return writer.toByteArray();
    }
    static byte[] map(byte[] raw){
        ClassNode c=new ClassNode();new ClassReader(raw).accept(c,0);
        for(MethodNode m:c.methods){
            String target=null;if(m.name.equals("initialize")&&m.desc.equals("(Ljourneymap/api/v2/client/IClientAPI;)V"))target="initialize";else if(m.name.equals("marks"))target="marks";else if(m.name.equals("look"))target="look";
            if(target!=null){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.instructions.add(new VarInsnNode(ALOAD,m.name.equals("initialize")?1:0));m.instructions.add(call(MAP,target,m.desc));m.instructions.add(new InsnNode(RETURN));mapHooks++;}
            if(m.name.equals("tick"))for(var instruction:m.instructions.toArray())if(instruction instanceof MethodInsnNode invoke&&invoke.owner.equals("java/util/Map")&&invoke.name.equals("clear")){
                m.instructions.insertBefore(instruction,call(MAP,"clear","()V"));disconnectHooks++;
            }
        }
        if(mapHooks!=3||disconnectHooks!=1)throw new IllegalStateException("Unexpected map bytecode: "+mapHooks+"/"+disconnectHooks);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);return writer.toByteArray();
    }
    static byte[] notice(byte[] raw){
        ClassNode c=new ClassNode();new ClassReader(raw).accept(c,0);int changes=0;
        for(MethodNode m:c.methods)for(var instruction:m.instructions)if(instruction instanceof LdcInsnNode text&&text.cst instanceof String value&&value.contains("міст Empirecraft 1.9.0")){
            text.cst=value.replace("міст Empirecraft 1.9.0","міст Empirecraft 1.11.0");changes++;
        }
        if(changes!=1)throw new IllegalStateException("Unexpected client installation notice");ClassWriter writer=new ClassWriter(0);c.accept(writer);return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        Map<String,byte[]> files=new TreeMap<>();try(ZipFile z=new ZipFile(args[0])){for(var it=z.entries();it.hasMoreElements();){var e=it.nextElement();if(!e.isDirectory())files.put(e.getName(),z.getInputStream(e).readAllBytes());}}
        String screen="ua/empirecraft/townymap/client/FleetScreen.class",map="ua/empirecraft/townymap/client/FleetMapPlugin.class";
        files.put(screen,screen(files.get(screen)));files.put(map,map(files.get(map)));
        String network="ua/empirecraft/townymap/FleetNetwork.class";files.put(network,notice(files.get(network)));
        try(ZipFile z=new ZipFile(args[1])){for(var it=z.entries();it.hasMoreElements();){var e=it.nextElement();if(e.getName().endsWith(".class"))files.put(e.getName(),z.getInputStream(e).readAllBytes());}}
        Path resources=Path.of(args[2]);try(var paths=Files.walk(resources)){for(Path p:paths.filter(Files::isRegularFile).toList())files.put(resources.relativize(p).toString().replace('\\','/'),Files.readAllBytes(p));}
        Path output=Path.of(args[3]);Files.createDirectories(output.getParent());try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(output))){for(var e:files.entrySet()){ZipEntry entry=new ZipEntry(e.getKey());entry.setTime(0);z.putNextEntry(entry);z.write(e.getValue());z.closeEntry();}}
        System.out.println("FLEET_CLIENT_OVERLAY_PASS init="+initHooks+" send="+sendHooks+" map="+mapHooks+" disconnect="+disconnectHooks);
    }
}
