package ua.empirecraft.townymap.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The server's persisted preference selects UI text independently of Minecraft's language. */
public final class ClientText {
    private static final Map<String,Map<String,String>> TEXT=new HashMap<>();
    static {
        for(String locale:List.of("en","ru")){
            var values=new HashMap<String,String>();
            try(var input=ClientText.class.getResourceAsStream("/assets/empirecraft_townymap/locales/"+locale+".json")){
                if(input!=null)try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
                    JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));
                }
            }catch(IOException ex){throw new ExceptionInInitializerError(ex);}
            TEXT.put(locale,Map.copyOf(values));
        }
    }
    private ClientText(){}
    public static String tr(String source,String locale){return TEXT.getOrDefault(locale,Map.of()).getOrDefault(source,source);}
}
