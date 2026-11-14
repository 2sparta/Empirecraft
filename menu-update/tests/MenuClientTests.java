import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import ua.empirecraft.townymap.client.ClientText;
import ua.empirecraft.townymap.client.FleetUiHooks;

/** Real localization classes and shipped bytecode; selectors do not instantiate Minecraft widgets. */
public final class MenuClientTests {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static class Parent {JsonObject data;Parent(JsonObject data){this.data=data;}}
    public static void main(String[] args)throws Exception {
        for(String locale:List.of("en","uk","ru")){
            var data=JsonParser.parseString("{\"players\":[{\"id\":\"uuid\",\"name\":\"Місто\"}],\"ships\":[{\"id\":\"42\",\"name\":\"Чайка\",\"town\":\"Сума\",\"uses\":[\"MANAGE\"]}]}").getAsJsonObject();data.addProperty("locale",locale);
            var schema=JsonParser.parseString("{\"id\":\"captain\",\"shipUse\":\"MANAGE\"}").getAsJsonObject();
            var choices=FleetUiHooks.choices(data,schema,true);check(choices.size()==1,"Exact permitted ship list");
            check(choices.get(0).name().equals("Чайка")&&choices.get(0).value().equals("42"),"User ship name and identifier preserved");
            check(choices.get(0).detail().equals(ClientText.tr("Місто: ",locale)+"Сума · #42"),"Town name preserved inside localized prose");
            var people=FleetUiHooks.choices(data,schema,false);check(people.get(1).name().equals("Місто"),"Player names are never translated");
            check(people.get(0).name().equals(ClientText.tr("Зняти призначення",locale)),"Localized dismissal option");
            Class<?> picker=Class.forName("ua.empirecraft.townymap.client.FleetUiHooks$Picker");var ctor=picker.getDeclaredConstructor(Object.class,int.class,boolean.class,List.class);ctor.setAccessible(true);
            var model=ctor.newInstance(new Parent(data),0,true,choices);var method=FleetUiHooks.class.getDeclaredMethod("data",picker);method.setAccessible(true);var shown=(JsonObject)method.invoke(null,model);
            check(shown.get("locale").getAsString().equals(locale),"Nested picker retains menu preference");
            check(shown.getAsJsonArray("rows").get(0).getAsJsonObject().get("title").getAsString().startsWith(ClientText.tr("Пошук: ",locale)),"Nested search uses menu locale");
        }
        try(var zip=new ZipFile(Path.of(args[0]).toFile())){
            String name="ua/empirecraft/townymap/client/FleetScreen.class";var node=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(name))).accept(node,0);
            check(node.methods.stream().anyMatch(m->m.name.equals("tr")),"Shipped fleet screen reads server locale");
            for(String oldName:List.of("init","render","tick","charTyped","mouseClicked","mouseScrolled","keyPressed","onClose"))
                check(node.methods.stream().noneMatch(m->m.name.equals(oldName)),"Native callback remapping: "+oldName);
            for(String locale:List.of("en","ru")){
                var catalog=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/empirecraft_townymap/locales/"+locale+".json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                for(String cls:List.of("FleetScreen","FleetUiHooks")){
                    var c=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry("ua/empirecraft/townymap/client/"+cls+".class"))).accept(c,0);
                    for(var m:c.methods)for(var instruction:m.instructions)if(instruction instanceof LdcInsnNode ldc&&ldc.cst instanceof String text&&text.matches(".*[А-Яа-яІіЇїЄєҐґ].*"))check(catalog.has(text),"Missing fleet translation: "+locale+" / "+text);
                }
            }
        }
        System.out.println("MENU_CLIENT_PASS checks="+checks+" locales=en,uk,ru");
    }
}
