package ua.townyrecruits.test;

import ua.townyrecruits.*;
import java.util.*;

public final class FeedbackPolicyTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static String route(String s,boolean pending){return CommandRouting.route(s,pending,"confirm","cancel");}
    public static void main(String[] args){
        UUID first=UUID.fromString("10000000-0000-4000-8000-000000000001"),second=UUID.fromString("10000000-0000-4000-8000-000000000002");
        var names=List.of(new TownyNames.Entry(first,"TRTest",List.of("Land of TRTest","Land of TRTest (Nation)")),
            new TownyNames.Entry(second,"Київська_Русь",List.of("§6Київська Русь (Держава)")));
        check(TownyNames.resolve(names,"Land_of_TRTest").equals(first),"display prefix with underscores");
        check(TownyNames.resolve(names,"land of trtest (nation)").equals(first),"full formatted nation");
        check(TownyNames.resolve(names,"КИЇВСЬКА РУСЬ").equals(second),"Unicode case and spaces");
        check(TownyNames.resolve(names,"§6Київська Русь (Держава)").equals(second),"formatted color codes");
        check(TownyNames.resolve(names,first.toString()).equals(first),"UUID bypasses alias ambiguity");
        boolean missing=false;try{TownyNames.resolve(names,"TR");}catch(IllegalArgumentException expected){missing=true;}check(missing,"partial names do not select arbitrary nation");
        var duplicate=new ArrayList<>(names);duplicate.add(new TownyNames.Entry(UUID.randomUUID(),"Another",List.of("Land of TRTest")));
        boolean ambiguous=false;try{TownyNames.resolve(duplicate,"Land of TRTest");}catch(IllegalArgumentException expected){ambiguous=expected.getMessage().contains("UUID");}check(ambiguous,"duplicate display alias requires UUID");
        check(route("/give @s minecraft:gold_ingot 15",false).equals("/minecraft:give @s minecraft:gold_ingot 15"),"give selector reaches vanilla");
        check(route("/clear @s minecraft:gold_ingot",false).equals("/minecraft:clear @s minecraft:gold_ingot"),"clear selector reaches vanilla");
        check(route("/tp @s 10 70 -20",false).equals("/minecraft:tp @s 10 70 -20"),"tp source selector");
        check(route("/tp Alex @s",false).equals("/minecraft:tp Alex @s"),"tp destination selector");
        check(route("/give @a[distance=..5] minecraft:stone 2",false).equals("/minecraft:give @a[distance=..5] minecraft:stone 2"),"selector options preserved");
        check(route("/give Alex minecraft:stone 1",false).equals("/give Alex minecraft:stone 1"),"ordinary plugin command preserved");
        check(route("/essentials:give @s stone 1",false).equals("/essentials:give @s stone 1"),"explicit namespace preserved");
        check(route("/minecraft:give @s stone 1",false).equals("/minecraft:give @s stone 1"),"native namespace preserved");
        check(route("/say @s",false).equals("/say @s"),"unrelated commands untouched");
        check(route("/confirm",false).equals("/confirm"),"no Towny request never hijacks confirm");
        check(route("/confirm",true).equals("/trconfirm"),"active Towny confirmation routed");
        check(route("/towny:confirm",true).equals("/trconfirm"),"explicit Towny confirmation routed");
        check(route("/cancel",true).equals("/trcancel"),"active Towny cancellation routed");
        check(route("/confirm extra",true).equals("/confirm extra"),"arguments never dropped from confirm");
        check(route("/essentials:confirm",true).equals("/essentials:confirm"),"other plugin confirmation untouched");
        check(CommandRouting.route("/yes",true,"yes","no").equals("/trconfirm"),"Towny configured confirm name");
        check(CommandRouting.route("/no",true,"yes","no").equals("/trcancel"),"Towny configured cancel name");
        System.out.println("RECRUITMENT_FEEDBACK_POLICY_PASS checks="+checks);
    }
}
