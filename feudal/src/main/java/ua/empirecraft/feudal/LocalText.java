package ua.empirecraft.feudal;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.command.CommandSender;

/** Only server-authored literals are translated. Names, book text and command arguments remain data. */
public final class LocalText {
    private static final ThreadLocal<String> CURRENT=ThreadLocal.withInitial(()->"en");
    private static final Map<String,Map<String,String>> CATALOGS=new HashMap<>();
    static {
        for(String language:List.of("en","ru")){
            var values=new HashMap<String,String>();
            try(var input=LocalText.class.getResourceAsStream("/empirecraft-feudal/lang/"+language+".json")){
                if(input!=null)try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
                    JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));
                }
            }catch(IOException ex){throw new ExceptionInInitializerError(ex);}
            CATALOGS.put(language,Map.copyOf(values));
        }
    }
    private LocalText(){}
    static boolean translated(String source){return CATALOGS.getOrDefault(CURRENT.get(),Map.of()).containsKey(source);}
    public static String tr(String source){
        String language=CURRENT.get();String translated=CATALOGS.getOrDefault(language,Map.of()).getOrDefault(source,source);
        return CommandLanguage.text(translated,language);
    }
    public static Scope use(CommandSender sender){return use(CommandLanguage.locale(sender));}
    public static Scope use(UUID person){return use(CommandLanguage.locale(person));}
    public static Scope use(String language){String previous=CURRENT.get();CURRENT.set(CommandPreferences.code(language));return new Scope(previous);}
    public static boolean cancel(String value){return Set.of("cancel","скасувати","отмена","отменить").contains(value.trim().toLowerCase(Locale.ROOT));}
    public static final class Scope implements AutoCloseable {
        private final String previous;private boolean closed;
        Scope(String previous){this.previous=previous;}
        @Override public void close(){if(!closed){CURRENT.set(previous);closed=true;}}
    }
}
