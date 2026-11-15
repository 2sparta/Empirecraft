import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Structural validation of the remapped JAR, including native slot and GUI overrides. */
public final class PackagedTest {
 public static void main(String[] args)throws Exception{
  int methods=0,classes=0;try(var z=new ZipFile(args[0])){
   if(JsonParser.parseString(new String(z.getInputStream(z.getEntry("pack.mcmeta")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack").get("pack_format").getAsInt()!=15)throw new AssertionError("Minecraft 1.20.1 resource pack metadata");
   for(var entry:Collections.list(z.entries())){
    if(entry.getName().startsWith("net/minecraft/")||entry.getName().startsWith("net/minecraftforge/")||entry.getName().startsWith("net/dries007/"))throw new AssertionError("Foreign API classes bundled");
    if(!entry.getName().endsWith(".class"))continue;var node=new ClassNode();new ClassReader(z.getInputStream(entry)).accept(node,0);classes++;
    for(var method:node.methods)if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){new Analyzer<>(new BasicVerifier()).analyze(node.name,method);methods++;}
    if(entry.getName().matches(".*CastingMenu\\$[12]\\.class")&&node.methods.stream().noneMatch(m->m.name.equals("m_5857_")))throw new AssertionError("Native slot placement override not remapped");
    if(entry.getName().endsWith("CastingScreen.class")&&node.methods.stream().noneMatch(m->m.name.equals("m_7286_")))throw new AssertionError("Native container background callback not remapped");
   }
  }System.out.println("CASTING_PACKAGED_PASS classes="+classes+" methods="+methods);
 }
}
