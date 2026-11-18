import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Checks real client API definitions and migrated bytecode without initializing a graphical client. */
public final class StrategyLinkageProbe {
    static final Map<String,ClassNode> classes=new HashMap<>();
    static int checks;
    static void load(Path path)throws Exception {try(var zip=new ZipFile(path.toFile())){for(var entries=zip.entries();entries.hasMoreElements();){var entry=entries.nextElement();
        if(!entry.getName().endsWith(".class"))continue;var node=new ClassNode();new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(node,0);classes.put(node.name,node);
    }}}
    static ClassNode type(String name){var node=classes.get(name);if(node==null)throw new AssertionError("Missing real class "+name);return node;}
    static void method(String owner,String name,int arguments){
        var node=type(owner);if(node.methods.stream().noneMatch(m->m.name.equals(name)&&Type.getArgumentTypes(m.desc).length==arguments))
            throw new AssertionError("Missing API method "+owner+"."+name+"/"+arguments);checks++;
    }
    static void field(String owner,String name){var node=type(owner);if(node.fields.stream().noneMatch(f->f.name.equals(name)))
        throw new AssertionError("Missing API field "+owner+"."+name);checks++;}
    public static void main(String[] args)throws Exception {
        Path repo=Path.of(args[0]);load(repo.resolve("server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-server.jar"));
        load(repo.resolve("server/libraries/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"));
        load(repo.resolve("strategy-map/dist/recruits-1.20.1-1.15.2-empirecraft.1.jar"));load(repo.resolve("strategy-map/dist/TownyRecruits.jar"));
        load(repo.resolve("mods/journeymap-forge-1.20.1-6.0.7.jar"));
        String mc="net/minecraft/client/Minecraft",gui="net/minecraft/client/gui/GuiGraphics",screen="net/minecraft/client/gui/screens/Screen";
        method(mc,"m_91087_",0);method(mc,"m_91403_",0);method(mc,"m_91152_",1);
        for(String name:List.of("f_91080_","f_91074_","f_91062_","f_91073_","f_91076_"))field(mc,name);
        field(screen,"f_96543_");field(screen,"f_96544_");field("net/minecraft/client/gui/components/AbstractWidget","f_93623_");
        method(gui,"m_280509_",5);method(gui,"m_280488_",5);method(gui,"m_280480_",3);method(gui,"m_280168_",0);method(gui,"m_280163_",9);
        method("net/minecraft/client/gui/Font","m_92895_",1);
        for(String[] row:new String[][]{{"m_85836_","0"},{"m_85849_","0"},{"m_85837_","3"},{"m_85841_","3"}})
            method("com/mojang/blaze3d/vertex/PoseStack",row[0],Integer.parseInt(row[1]));
        method("net/minecraft/client/multiplayer/ClientPacketListener","m_105142_",0);method("net/minecraft/client/multiplayer/ClientPacketListener","m_104910_",0);
        method("net/minecraft/client/multiplayer/PlayerInfo","m_105312_",0);
        String full="journeymap/client/ui/fullscreen/Fullscreen";
        for(String name:List.of("isButtonsVisable","hideButtons","zoomIn","zoomOut"))method(full,name,0);
        method(full,"centerOn",2);method("journeymap/client/ui/component/screens/JmUILegacy","getRenderables",0);
        String manager="com/talhanation/recruits/client/ClientManager";
        method(manager,"getRoutesList",0);method(manager,"saveRoute",1);method(manager,"renameRoute",2);method(manager,"deleteRoute",1);field(manager,"groups");
        String route="com/talhanation/recruits/world/RecruitsRoute";
        method(route,"<init>",1);method(route,"getWaypoints",0);method(route,"addWaypoint",1);method(route,"removeWaypoint",1);
        method(route+"$Waypoint","<init>",3);method(route+"$Waypoint","setAction",1);method(route+"$Waypoint","setPosition",1);
        method("com/talhanation/recruits/network/MessageTransferRoute","<init>",2);method("com/talhanation/recruits/network/MessagePatrolLeaderSetRoute","<init>",4);
        method("ua/townyrecruits/tactics/TacticalOrder","<init>",6);method("ua/townyrecruits/tactics/TacticalNetwork$Request","<init>",3);
        method("ua/townyrecruits/client/JourneyMapArmyLayer","visible",1);
        String old="com/talhanation/recruits/client/gui/worldmap/",storage=old+"storage/WorldMapStorageId";
        for(var node:classes.values())if(node.name.startsWith("com/talhanation/recruits/")) {
            if(node.name.startsWith(old)&&!node.name.equals(storage))throw new AssertionError("Old map engine remains: "+node.name);
            for(var m:node.methods) {
                if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,m);
                for(var code:m.instructions) {
                    String owner=code instanceof MethodInsnNode call?call.owner:code instanceof FieldInsnNode f?f.owner:code instanceof TypeInsnNode t?t.desc:"";
                    if(owner.startsWith(old)&&!owner.equals(storage))throw new AssertionError("Dangling map reference: "+node.name+"."+m.name);
                }
            }
        }
        method(storage,"detectCurrent",0);method(storage,"setServerWorldId",1);method(storage,"clearServerWorldId",0);
        System.out.println("STRATEGY_LINKAGE_PASS "+checks+" API assertions; native Recruits bytecode verified; legacy renderer absent; route identity retained");
    }
}
