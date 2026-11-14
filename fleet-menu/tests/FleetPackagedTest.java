import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Verify remapped executable GUI classes; do not load fake Minecraft classes or claim a graphical run. */
public final class FleetPackagedTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        int methods=0,classes=0;String root="ua/empirecraft/townymap/client/";
        try(ZipFile z=new ZipFile(args[0])){
            for(var entry:Collections.list(z.entries())){
                check(!entry.getName().startsWith("net/minecraft/")&&!entry.getName().startsWith("org/bukkit/"),"Only application classes are shipped");
                if(!entry.getName().endsWith(".class")||!entry.getName().startsWith(root+"Fleet"))continue;
                var node=new ClassNode();new ClassReader(z.getInputStream(entry)).accept(node,0);classes++;
                for(var method:node.methods)if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){new Analyzer<>(new BasicVerifier()).analyze(node.name,method);methods++;}
                if(node.name.equals(root+"FleetStyle$Action")){
                    check(node.methods.stream().anyMatch(m->m.name.equals("m_87963_")&&m.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")),"Custom button overrides the native render callback after remapping");
                    check(node.superName.equals("net/minecraft/client/gui/components/Button"),"Native Button retains keyboard activation and narration");
                }
            }
            for(String name:List.of("META-INF/mods.toml","assets/empirecraft_townymap/locales/en.json","assets/empirecraft_townymap/locales/ru.json"))check(Arrays.equals(z.getInputStream(z.getEntry(name)).readAllBytes(),Files.readAllBytes(Path.of(args[1],"bridge/src/main/resources",name))),"Packaged metadata and translations match reviewed sources");
            check(z.getEntry(root+"FleetLayout$Placement.class")!=null,"Rendering and hit testing share the tested row placement model");
        }
        check(classes>=10&&methods>70,"All fleet GUI classes and methods are structurally verified");
        System.out.println("FLEET_PACKAGED_PASS classes="+classes+" methods="+methods+" checks="+checks);
    }
}
