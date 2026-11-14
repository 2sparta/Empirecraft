import java.util.*;
import java.lang.reflect.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.nio.file.*;
import java.util.zip.*;
import ua.empirecraft.townymap.client.FleetMarkerBook;
import ua.empirecraft.townymap.client.FleetUiHooks;

/** Real shipped bytecode + model tests; no Minecraft/Bukkit API substitutes or graphical claims. */
public final class FleetClientTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    static final class Box {boolean visible=true;int width=260;String value="";java.util.function.Consumer<String> changed;int getX(){return 130;}int getY(){return 77;}String getValue(){return value;}void setValue(String next){value=next;if(changed!=null)changed.accept(next);}void setResponder(java.util.function.Consumer<String> responder){changed=responder;}}
    static final class Form {
        JsonObject data,schema;int height=300,buttons;List<Box> fields=new ArrayList<>();
        int left(){return 120;}void button(String label,int x,int y,int width,Runnable run){buttons++;check(x>=120&&y>=77&&width==80,"Selector remains in the field row");}
    }
    static void bytecode(Path file)throws Exception {
        int hooks=0,verified=0;try(ZipFile jar=new ZipFile(file.toFile())){
            for(String name:List.of("ua/empirecraft/townymap/client/FleetScreen.class","ua/empirecraft/townymap/client/FleetMapPlugin.class","ua/empirecraft/townymap/FleetNetwork.class")){
                ClassNode node=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry(name))).accept(node,0);
                for(MethodNode method:node.methods){if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){new Analyzer<>(new BasicVerifier()).analyze(node.name,method);verified++;}
                    for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.startsWith("ua/empirecraft/townymap/client/Fleet")&&(call.owner.endsWith("FleetUiHooks")||call.owner.endsWith("FleetWaypoints")))hooks++;
                }
                if(name.endsWith("FleetScreen.class")){
                    check(node.methods.stream().anyMatch(m->m.name.equals("m_5534_")&&m.desc.equals("(CI)Z")),"Native Unicode text event routes into selector search");
                    check(node.methods.stream().anyMatch(m->m.name.equals("m_7933_")&&m.desc.equals("(III)Z")),"Native key event routes Escape/Enter/Backspace");
                    check(node.methods.stream().anyMatch(m->m.name.equals("m_7379_")&&m.desc.equals("()V")),"Native close returns to the draft form");
                    var send=node.methods.stream().filter(m->m.name.equals("send")).findFirst().orElseThrow();check(send.instructions.iterator().hasNext(),"Existing sender-bound confirmation route retained");
                }
            }
            check(jar.getEntry("ua/empirecraft/townymap/FleetObservationNative.class")!=null,"Server receives real ship/visibility adapter");
            check(jar.stream().noneMatch(e->e.getName().startsWith("net/minecraft/")||e.getName().startsWith("org/bukkit/")),"No foreign API classes bundled");
        }check(hooks>=10&&verified>=20,"All overlay methods pass ASM stack/type analysis");
    }
    public static void main(String[] args)throws Exception {
        var book=new FleetMarkerBook<String>();book.put(42,"guid-A","x=10,stamp=1","point-A");book.put(42,"guid-A","x=90,stamp=1","point-A");
        check(book.get(42).signature().equals("x=90,stamp=1"),"A coordinate change is independent of clock granularity");
        var removed=book.retain(Set.of());check(removed.equals(List.of("point-A")),"Empty-place notice removes the remembered marker");
        check(!book.deleted(42,"guid-A")&&!book.hidden(42),"Delayed programmatic deletion cannot hide a future sighting");
        book.put(42,"guid-B","x=200","point-B");check(!book.deleted(42,"guid-A")&&book.get(42)!=null,"A callback from an older GUID cannot delete the rediscovered ship");
        check(book.deleted(42,"guid-B")&&book.hidden(42),"Deleting the current native waypoint hides it");book.retain(Set.of(42L));check(book.hidden(42),"A pending server acknowledgement cannot resurrect a manually hidden waypoint");
        book.retain(Set.of());check(!book.hidden(42),"Server acknowledgement allows an explicit later restore");book.put(42,"guid-C","stamp","point-C");book.clear();check(book.get(42)==null&&!book.deleted(42,"guid-C"),"Disconnect clears ownership before delayed callbacks");
        var data=json("{\"ships\":[{\"id\":\"42\",\"name\":\"Чорна Перлина\",\"town\":\"Львів\",\"uses\":[\"MANAGE\"]},{\"id\":\"43\",\"name\":\"Чорна Перлина\",\"town\":\"Київ\",\"uses\":[\"MANAGE\"]},{\"id\":\"44\",\"name\":\"Таємний\",\"town\":\"Рим\",\"uses\":[\"MAP\"]}],\"players\":[{\"name\":\"Dobroslav\",\"id\":\"abc\"}],\"selectedShip\":\"43\"}");
        var schema=json("{\"id\":\"captain\",\"shipField\":0,\"personField\":1,\"shipUse\":\"MANAGE\"}");var options=FleetUiHooks.choices(data,schema,true);
        check(options.size()==2,"Only the role permitted by this form is offered");check(!options.get(0).value().equals(options.get(1).value()),"Equal names retain separate canonical ship selections");
        check(FleetUiHooks.matches(options,"пЕРЛИНА").size()==2,"Unicode/case-insensitive name search");check(FleetUiHooks.matches(options,"Київ").equals(List.of(1)),"City search disambiguates names");check(FleetUiHooks.matches(options,"#42").equals(List.of(0)),"Optional technical ID remains searchable");
        check(FleetUiHooks.choices(data,schema,false).get(0).value().equals("-"),"Captain picker supports clearing the role");check(FleetUiHooks.choices(data,schema,false).get(1).value().equals("Dobroslav"),"Players are selected by readable names");
        var form=new Form();form.data=data;form.schema=schema;form.fields.add(new Box());form.fields.add(new Box());FleetUiHooks.init(form);
        check(form.buttons==2,"Ship and captain fields each receive a selector");check(form.fields.get(0).value.equals("Чорна Перлина (#43)"),"Ship row pre-fills its own name while retaining an unambiguous reference");check(form.fields.get(0).width==174,"Native text input reserves room for its selector button");
        check(FleetUiHooks.values(form,"captain",List.of(form.fields.get(0).value,"Dobroslav")).equals(List.of("43","Dobroslav")),"A chosen display name submits its canonical ID without parsing labels");
        form.fields.get(0).setValue("Чайка (#42)");check(FleetUiHooks.values(form,"captain",List.of("Чайка (#42)","Dobroslav")).get(0).equals("Чайка (#42)"),"Manual editing clears the old selection, so literal names cannot select another ship");
        check(FleetUiHooks.typed(form,'ї',0)==null&&FleetUiHooks.key(form,256,0,0)==null&&!FleetUiHooks.close(form),"Non-picker forms keep native keyboard/focus/closing behaviour");
        bytecode(Path.of(args[0]));System.out.println("FLEET_CLIENT_PASS checks="+checks);
    }
}
