import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Inspect the real shipped UI hooks and unchanged upstream vanilla-chat fallback. */
public final class PreferencesBytecodeTests {
    static ClassNode node(JarFile jar,String name)throws Exception {
        var node=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class"))).accept(node,0);return node;
    }
    static boolean call(MethodNode method,String owner,String name){for(var n:method.instructions)if(n instanceof MethodInsnNode m&&m.owner.equals(owner)&&m.name.equals(name))return true;return false;}
    static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    public static void main(String[] args)throws Exception {
        String base="com/ebicep/chatplus/",prefs="ua/empirecraft/chat/ClientPreferences";
        try(var original=new JarFile(args[0]);var shipped=new JarFile(args[1])){
            for(String name:List.of("config/ConfigScreen","config/forge/ConfigScreenImpl"))
                if(!call(method(node(shipped,base+name),"getConfigScreen"),prefs,"screen"))throw new AssertionError("Settings screen not reachable: "+name);
            if(!call(method(node(shipped,base+"config/ConfigScreen"),"setOpen"),prefs,"requestOpen"))throw new AssertionError("Config command/key path remains blocked");
            for(String name:List.of("forge/ChatPlusPlatformInitImpl","events/EventBus","mixin/MixinChatComponent","mixin/MixinChatScreen"))
                if(!Arrays.equals(original.getInputStream(original.getJarEntry(base+name+".class")).readAllBytes(),shipped.getInputStream(shipped.getJarEntry(base+name+".class")).readAllBytes()))throw new AssertionError("Upstream settings registration/disable fallback changed: "+name);
            var configure=method(node(shipped,"ua/empirecraft/chat/ClientChannels"),"configure");
            if(call(configure,base+"config/ConfigVariables","setEnabled"))throw new AssertionError("Startup overrides the persisted enabled flag");
            var apply=method(node(shipped,prefs),"apply");
            if(!call(apply,base+"config/ConfigVariables","setEnabled")||!call(apply,base+"config/Config","save"))throw new AssertionError("Enabled flag is not persisted natively");
            if(call(method(node(shipped,prefs),"tick"),base+"config/ConfigVariables","getEnabled"))throw new AssertionError("Settings cannot be opened after disabling ChatPlus");
            if(!call(method(node(shipped,base+"features/PeekChat"),"getPeeking"),"ua/empirecraft/chat/ClientChannels","enabled"))throw new AssertionError("Peek scrolling still overrides vanilla input while disabled");
            for(String name:List.of("features/HideChat","features/TranslateMessage","features/chattabs/ChatTabs","features/speechtotext/MicrophoneThread")){
                int guarded=0;
                for(var m:node(shipped,base+name).methods)if(m.desc.endsWith(")Ldev/architectury/event/EventResult;")){
                    var instructions=Arrays.stream(m.instructions.toArray()).filter(n->n.getOpcode()>=0).toList();
                    if(!(instructions.get(0) instanceof MethodInsnNode first&&first.owner.equals("ua/empirecraft/chat/ClientChannels")&&first.name.equals("enabled")))throw new AssertionError("Raw input bypasses disabled state: "+name+"."+m.name);
                    if(!(instructions.get(2) instanceof MethodInsnNode pass&&pass.name.equals("pass"))||instructions.get(3).getOpcode()!=Opcodes.ARETURN)throw new AssertionError("Disabled raw callback consumes input");
                    guarded++;
                }
                if(guarded==0)throw new AssertionError("Expected raw input callbacks: "+name);
            }
        }
        System.out.println("CHAT_SETTINGS_BYTECODE_PASS native Forge config button, command/key access, persistent disable and unchanged vanilla fallback");
    }
}
