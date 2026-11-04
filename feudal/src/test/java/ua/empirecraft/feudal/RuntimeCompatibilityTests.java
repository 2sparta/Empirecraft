package ua.empirecraft.feudal;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.CityMarketTests.*;

/** Compare recovered policy with the actual installed bytecode, then exercise its integration. */
public final class RuntimeCompatibilityTests {
    static Object previousView(ClassLoader loader,int topology,boolean pact,boolean war)throws Exception {
        var land=loader.loadClass("ua.empirecraft.feudal.ArmyPolicy$Land").getConstructor(String.class,String.class,String.class,String.class);
        var soldier=loader.loadClass("ua.empirecraft.feudal.ArmyPolicy$Soldier").getConstructor(String.class,String.class,String.class,boolean.class);
        var a=land.newInstance("t:A","n:A","rootA","ownerA");
        var b=land.newInstance("t:B",topology==1?"n:A":"n:B",topology==2?"rootA":"rootB","ownerB");
        var type=loader.loadClass("ua.empirecraft.feudal.ArmyPolicy$View");
        return type.getConstructor(long.class,Map.class,Map.class,Map.class,Set.class,Set.class,Set.class,Set.class).newInstance(10000L,
            Map.of("t:A",a,"t:B",b),Map.of("actorA","t:A","actorB","t:B","ownerA","t:A","ownerB","t:B"),
            Map.of("actorA",soldier.newInstance("ownerA","t:A","t:A",true),"actorB",soldier.newInstance("ownerB","t:B","t:B",true)),
            topology==3?Set.of("t:A|t:B"):Set.of(),pact?Set.of("t:A|t:B"):Set.of(),war?Set.of("t:A|t:B"):Set.of(),Set.of());
    }
    static ArmyPolicy.View currentView(int topology,boolean pact,boolean war){
        return new ArmyPolicy.View(10000L,
            Map.of("t:A",new ArmyPolicy.Land("t:A","n:A","rootA","ownerA"),"t:B",new ArmyPolicy.Land("t:B",topology==1?"n:A":"n:B",topology==2?"rootA":"rootB","ownerB")),
            Map.of("actorA","t:A","actorB","t:B","ownerA","t:A","ownerB","t:B"),
            Map.of("actorA",new ArmyPolicy.Soldier("ownerA","t:A","t:A",true),"actorB",new ArmyPolicy.Soldier("ownerB","t:B","t:B",true)),
            topology==3?Set.of("t:A|t:B"):Set.of(),pact?Set.of("t:A|t:B"):Set.of(),war?Set.of("t:A|t:B"):Set.of(),Set.of());
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Відновлена бойова політика збігається з установленим 1.11 у 3072 випадках",()->{
            Path fixture=Path.of("src/test/resources/army-policy-1.11.0.jar");
            if(!fixture.toFile().isFile())fixture=Path.of("feudal").resolve(fixture);
            int cases=0;
            try(var loader=new URLClassLoader(new java.net.URL[]{fixture.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
                var method=loader.loadClass("ua.empirecraft.feudal.ArmyPolicy$View").getMethod("decide",String.class,String.class,boolean.class,String.class,String.class,boolean.class,boolean.class,boolean.class,boolean.class,boolean.class,long.class);
                for(int topology=0;topology<4;topology++)for(boolean pact:List.of(false,true))for(boolean war:List.of(false,true)){
                    Object previous=previousView(loader,topology,pact,war);var current=currentView(topology,pact,war);
                    for(int bits=0;bits<64;bits++)for(long now:new long[]{9999,10001,16000}){
                        boolean ra=(bits&1)>0,rb=(bits&2)>0,civil=(bits&4)>0,monster=(bits&8)>0,pvp=(bits&16)>0,defense=(bits&32)>0;
                        String expected=method.invoke(previous,"actorA","ownerA",ra,"actorB","ownerB",rb,civil,monster,pvp,defense,now).toString();
                        eq(current.decide("actorA","ownerA",ra,"actorB","ownerB",rb,civil,monster,pvp,defense,now).toString(),expected);cases++;
                    }
                }
            }
            eq(cases,3072);
        });
        test("Меню зберігає права на володіння, пагінацію та захист текстових полів",()->{try(var f=new F()){
            var data=new TownMenuData(f.s);data.access(d,"t:D","t:D");no(()->data.access(b,"t:D","n:C"));
            no(()->TownMenuData.input("/op player"));eq(TownMenuData.slice(java.util.stream.IntStream.range(0,80).boxed().toList(),1).size(),36);
            eq(TownMenuData.slice(java.util.stream.IntStream.range(0,80).boxed().toList(),2).size(),8);
            FeudalPlugin.class.getMethod("armyRequest",String.class,Map.class);FeudalPlugin.class.getMethod("armyCommand",org.bukkit.command.CommandSender.class,org.bukkit.command.Command.class,String.class,String[].class);
            FeudalPlugin.class.getMethod("townMenuOpen",org.bukkit.entity.Player.class);FeudalPlugin.class.getMethod("contractMenuOpen",org.bukkit.entity.Player.class);
        }});
        test("Збережений конструктор не підписує васалітет в обхід усіх механічних сторінок",()->{try(var f=new F()){
            var builder=new ContractService(f.s,null);f.s.contractBuilder=builder;long id=builder.create(a,"n:B","n:A");builder.agree(a,id,1);builder.agree(b,id,1);
            no(()->builder.sign(a,id,1));var doc=f.m.documents.vassalBuilder(id);eq(f.m.documents.view(a,doc,1).token(),"");
            String token=MarketExtensionTests.token(f,a,doc);no(()->f.m.documents.acceptBuilder(b,id,1,token));f.m.documents.acceptBuilder(a,id,1,token);
            var next=f.m.documents.vassalBuilder(id);f.m.documents.acceptBuilder(b,id,1,MarketExtensionTests.token(f,b,next));eq(number(builder.get(id),"active_rev"),1L);
            no(()->f.s.offer(a,"n:B","n:A",0,"","висока","оборона",0));
        }});
        test("Армійський облік розрізняє повторний найм, загибель і зберігає стан",()->{try(var f=new F()){
            var army=new ArmyService(f.s,new ProvisionService(f.s,f.commerce),null);String unit=UUID.randomUUID().toString();army.observe(unit,E,"t:E",5.0);army.assign(e,unit,"t:E","t:E");eq(string(army.unit(unit),"estate"),"t:E");
            army.retire(unit);eq(bool(army.unit(unit),"alive"),false);var restored=new ArmyService(f.s,new ProvisionService(f.s,f.commerce),null);eq(bool(restored.unit(unit),"retired"),true);restored.observe(unit,E,"t:E",5.0);eq(bool(restored.unit(unit),"alive"),true);eq(string(restored.unit(unit),"estate"),null);restored.presence(unit,true);restored.observe(unit,E,"t:E",5.0);eq(bool(restored.unit(unit),"alive"),false);
        }});
        System.out.println("Runtime compatibility scenarios: "+count+"; installed policy comparisons: 3072");
    }
}
