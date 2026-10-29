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
        test("Help snippets select client language but leave typed values intact",()->{
            eq(CommandLanguage.text("/cu reserve deposit gold <кількість> KRN","ru_ru"),"/валюта резерв внести золото <кількість> KRN");
        });
        System.out.println("Command locale scenarios: "+count);
    }
}
