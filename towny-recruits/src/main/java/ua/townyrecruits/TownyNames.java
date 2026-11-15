package ua.townyrecruits;

import java.text.Normalizer;
import java.util.*;

/** Exact normalized aliases only. Ambiguous display names never choose an arbitrary nation. */
public final class TownyNames {
    public record Entry(UUID id,String name,List<String> aliases){}
    private TownyNames(){}
    public static String fold(String text){
        return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("(?i)[§&][0-9A-FK-ORX]","")
            .replace('_',' ').strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
    public static UUID resolve(Collection<Entry> entries,String text){
        try{UUID id=UUID.fromString(text);if(entries.stream().anyMatch(e->e.id().equals(id)))return id;}
        catch(IllegalArgumentException ignored){}
        String key=fold(text);
        var found=entries.stream().filter(e->fold(e.name()).equals(key)||e.aliases().stream().anyMatch(n->fold(n).equals(key))).toList();
        if(found.size()==1)return found.get(0).id();
        if(found.size()>1)throw new IllegalArgumentException("Назва неоднозначна. Використайте UUID: "+found.stream().map(e->e.name()+" ["+e.id()+"]").collect(java.util.stream.Collectors.joining(", ")));
        throw new IllegalArgumentException("Назву не знайдено: "+text+". Доступні: "+entries.stream().sorted(Comparator.comparing(Entry::name)).limit(12).map(Entry::name).collect(java.util.stream.Collectors.joining(", ")));
    }
}
