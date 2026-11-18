import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Redirects native map entry points. Original route/claim models remain binary-compatible. */
public final class MigrateRecruitsMap {
    public static void main(String[] args)throws Exception {
        int redirects=0,removed=0;var files=new TreeMap<String,byte[]>();
        String legacy="com/talhanation/recruits/client/gui/worldmap/";
        String storage=legacy+"storage/WorldMapStorageId";
        try(var zip=new ZipFile(args[0])) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();if(entry.isDirectory())continue;
                if(entry.getName().startsWith(legacy)&&!entry.getName().equals(storage+".class")){removed++;continue;}
                byte[] raw=zip.getInputStream(entry).readAllBytes();
                if(entry.getName().endsWith(".class")&&!entry.getName().contains("/gui/worldmap/")) {
                    var node=new ClassNode();new ClassReader(raw).accept(node,0);boolean changed=false;
                    if(node.name.equals("com/talhanation/recruits/client/events/ClientEvent"))for(var method:node.methods)
                        if(Set.of("modelBakingCompleted","textureStitchCompleted").contains(method.name)) {
                            method.instructions.clear();method.instructions.add(new InsnNode(Opcodes.RETURN));
                            method.tryCatchBlocks.clear();method.localVariables=null;method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
                            changed=true;System.out.println("REMOVE_MAP_CALLBACK "+node.name+"."+method.name);
                        }
                    // Discard only legacy cache/index calls, preserving route-world identity and all other event work.
                    for(var method:node.methods)for(var code:method.instructions.toArray())if(code instanceof MethodInsnNode call
                            &&Set.of(legacy+"storage/WorldMapCacheManager",legacy+"claim/WorldMapClaimIndex").contains(call.owner)) {
                        var replacement=new InsnList();
                        if(call.name.equals("getInstance")&&call.getOpcode()==Opcodes.INVOKESTATIC)replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                        else {
                            if(Type.getReturnType(call.desc)!=Type.VOID_TYPE)throw new IllegalStateException("Unexpected legacy cache return");
                            Type[] parameters=Type.getArgumentTypes(call.desc);
                            for(int i=parameters.length-1;i>=0;i--)replacement.add(new InsnNode(parameters[i].getSize()==2?Opcodes.POP2:Opcodes.POP));
                            if(call.getOpcode()!=Opcodes.INVOKESTATIC)replacement.add(new InsnNode(Opcodes.POP));
                            if(replacement.size()==0)replacement.add(new InsnNode(Opcodes.NOP));
                        }
                        method.instructions.insertBefore(code,replacement);method.instructions.remove(code);changed=true;
                        System.out.println("REMOVE_MAP_CACHE_CALL "+node.name+"."+method.name+" -> "+call.name);
                    }
                    for(var method:node.methods)for(var instruction:method.instructions.toArray()) {
                        if(!(instruction instanceof TypeInsnNode type)||type.getOpcode()!=Opcodes.NEW
                                ||!type.desc.equals("com/talhanation/recruits/client/gui/worldmap/WorldMapScreen"))continue;
                        var dup=instruction.getNext();while(dup instanceof LabelNode||dup instanceof LineNumberNode)dup=dup.getNext();
                        if(dup==null||dup.getOpcode()!=Opcodes.DUP)throw new IllegalStateException("Unexpected world map allocation");
                        var init=dup.getNext();while(init instanceof LabelNode||init instanceof LineNumberNode)init=init.getNext();
                        if(!(init instanceof MethodInsnNode constructor)||!constructor.name.equals("<init>")||!constructor.desc.equals("()V"))
                            throw new IllegalStateException("Unexpected world map constructor");
                        method.instructions.remove(instruction);method.instructions.remove(dup);
                        var factory=new MethodInsnNode(Opcodes.INVOKESTATIC,"ua/empirecraft/townymap/client/ClientAccess","newMapScreen","()Ljava/lang/Object;",false);
                        method.instructions.set(init,factory);
                        method.instructions.insert(factory,new TypeInsnNode(Opcodes.CHECKCAST,"net/minecraft/client/gui/screens/Screen"));
                        redirects++;changed=true;System.out.println("REDIRECT "+node.name+"."+method.name);
                    }
                    if(changed){var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);raw=writer.toByteArray();}
                    for(var method:node.methods)for(var code:method.instructions.toArray()) {
                        String owner=code instanceof MethodInsnNode call?call.owner:code instanceof FieldInsnNode field?field.owner
                                :code instanceof TypeInsnNode type?type.desc:code instanceof LdcInsnNode ldc&&ldc.cst instanceof Type type?type.getInternalName():"";
                        if(owner.startsWith(legacy)&&!owner.equals(storage))throw new IllegalStateException("Remaining map reference: "+node.name+"."+method.name+" -> "+owner);
                    }
                }
                files.put(entry.getName(),raw);
            }
        }
        if(redirects==0)throw new IllegalStateException("No Recruits map entry points migrated");
        try(var out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            for(var row:files.entrySet()){var entry=new ZipEntry(row.getKey());entry.setTime(0);out.putNextEntry(entry);out.write(row.getValue());out.closeEntry();}
        }
        System.out.println("RECRUITS_MAP_REDIRECTS "+redirects);
        System.out.println("LEGACY_MAP_CLASSES_REMOVED "+removed);
    }
}
