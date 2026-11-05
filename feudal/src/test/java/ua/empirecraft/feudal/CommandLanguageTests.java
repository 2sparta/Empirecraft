package ua.empirecraft.feudal;

import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;

public final class CommandLanguageTests {
    static void route(String root,String expected,String...args){eq(String.join(" ",CommandLanguage.normalize(root,args)),expected);}
    static void walk(CommandLanguage.Node node,Set<CommandLanguage.Node> visited){if(!visited.add(node))return;for(String locale:List.of("uk_ua","ru_ru")){
        Map<String,String> used=new HashMap<>();for(var entry:node.words.entrySet()){String key=entry.getKey();if(!CommandLanguage.WORDS.containsKey(key))throw new AssertionError("Missing translation: "+key);String local=CommandLanguage.local(key,locale),prev=used.put(local,key);if(prev!=null&&!prev.equals(key))throw new AssertionError("Ambiguous "+locale+": "+prev+"/"+key);eq(CommandLanguage.literal(node,local),key);walk(entry.getValue(),visited);}
    }if(node.argument!=null)walk(node.argument,visited);}
    public static void main(String[] args)throws Exception{
        count=0;
        test("All localized grammar branches resolve without ambiguous sibling aliases",()->walk(CommandLanguage.ROOT,Collections.newSetFromMap(new IdentityHashMap<>())));
        test("Ukrainian and Russian commands preserve names, currency codes and free text",()->{
            route("валюта","reserve deposit gold 64 KRN","резерв","внести","золото","64","KRN");
            route("город","set board внести золото","установить","объявление","внести","золото");
            route("місто","vassal offer місто:Резерв держава:Місто 50 KRN висока оборона 0","васал","пропозиція","місто:Резерв","держава:Місто","50","KRN","висока","оборона","0");
            route("cu","reserve deposit gold 10 SOL","reserve","diposit","gold","10","SOL");
        });
        test("Nested locale suggestions include deposit, physical gold and treasury parity",()->{
            eq(CommandLanguage.suggest("cu",new String[]{"reserve",""},"uk_ua"),List.of("внести"));
            eq(CommandLanguage.suggest("cu",new String[]{"reserve",""},"ru_ru"),List.of("внести"));
            eq(CommandLanguage.suggest("cu",new String[]{"reserve","deposit",""},"uk_ua"),List.of("золото"));
            eq(CommandLanguage.suggest("t",new String[]{"bank",""},"uk_ua"),CommandLanguage.suggest("t",new String[]{"treasury",""},"uk_ua"));
            eq(CommandHints.types("cu",new String[]{"reserve","deposit","gold","64",""}),Set.of("currency"));
            eq(CommandHints.types("cu",new String[]{"market",""}),Set.of());
            eq(CommandHints.types("cu",new String[]{"pay","Player",""}),Set.of("money"));
        });
        test("Help snippets select the requested language but leave typed values intact",()->{
            eq(CommandLanguage.text("/cu reserve deposit gold <кількість> KRN","ru_ru"),"/валюта резерв внести золото <кількість> KRN");
        });
        test("English is the default command grammar, independent of the client",()->{
            eq(CommandLanguage.suggest("cu",new String[]{"reserve",""},"en"),List.of("deposit"));eq(CommandLanguage.locale((org.bukkit.command.CommandSender)null),"en");
            eq(CommandLanguage.text("/cu reserve deposit gold 64 KRN","en"),"/currency reserve deposit gold 64 KRN");
            route("рынок","auction bid id 12.00","аукцион","ставка","id","12.00");route("рынок","contract id deliver 5","договор","id","доставить","5");
        });
        test("Command preferences persist by UUID and reject unsupported languages",()->{
            java.nio.file.Path root=java.nio.file.Files.createTempDirectory("command-language-");String url="jdbc:h2:"+root.resolve("db")+";DATABASE_TO_LOWER=TRUE";UUID player=UUID.randomUUID(),other=UUID.randomUUID();
            try(var f=new Fixture(url)){var prefs=new CommandPreferences(f.db);eq(prefs.get(player),"en");prefs.set(player,"ru");eq(prefs.get(player),"ru");eq(prefs.get(other),"en");no(()->prefs.set(player,"de"));eq(prefs.get(player),"ru");}
            try(var f=new Fixture(url)){var prefs=new CommandPreferences(f.db);eq(prefs.get(player),"ru");prefs.set(player,"en");eq(prefs.get(player),"en");}
            try(var files=java.nio.file.Files.walk(root)){for(var file:files.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.delete(file);}
        });
        test("The language command and tab completion never read Minecraft's locale",()->{
            try(var f=new Fixture()){
                UUID id=UUID.randomUUID();var messages=new ArrayList<String>();
                var player=(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(CommandLanguageTests.class.getClassLoader(),new Class<?>[]{org.bukkit.entity.Player.class},(proxy,method,values)->switch(method.getName()){
                    case "getUniqueId"->id;case "hasPermission"->true;
                    case "getLocale"->throw new AssertionError("Client locale must not select command language");
                    case "sendMessage"->{if(values.length==1&&values[0] instanceof String text)messages.add(text);yield null;}
                    case "toString"->"LocaleTestPlayer";default->throw new UnsupportedOperationException(method.getName());
                });
                var preferences=new CommandPreferences(f.db);var handler=new CommandLanguage(null,preferences);
                eq(CommandLanguage.locale(player),"en");handler.onCommand(player,null,"language",new String[]{"ru"});eq(preferences.get(id),"ru");
                eq(messages.get(messages.size()-1).startsWith("Язык команд: русский"),true);
                eq(handler.onTabComplete(player,null,"language",new String[]{""}),List.of("en","uk","ru"));
                handler.onCommand(player,null,"language",new String[]{"en"});eq(CommandLanguage.locale(player),"en");
                handler.onCommand(player,null,"language",new String[]{"de"});eq(CommandLanguage.locale(player),"en");
            }finally{CommandLanguage.reset();}
        });
        System.out.println("Command locale scenarios: "+count);
    }
}
