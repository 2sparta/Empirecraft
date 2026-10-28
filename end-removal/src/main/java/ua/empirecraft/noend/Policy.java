package ua.empirecraft.noend;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Policy {
    public static final Map<String,String> REPLACEMENTS;
    public static final Set<String> BLOCKS, ENTITIES;
    static {
        try (var stream=Policy.class.getResourceAsStream("/no-end.json")) {
            if(stream==null)throw new IOException("Missing no-end.json");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var replacements=new HashMap<String,String>();
            for(var entry:json.getAsJsonObject("replacements").entrySet())replacements.put(entry.getKey(),entry.getValue().getAsString());
            REPLACEMENTS=Map.copyOf(replacements);BLOCKS=ids(json.getAsJsonArray("blocks"));ENTITIES=ids(json.getAsJsonArray("entities"));
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private static Set<String> ids(JsonArray values){var result=new HashSet<String>();for(var value:values)result.add(value.getAsString());return Set.copyOf(result);}
    public static boolean blocked(String id){return REPLACEMENTS.containsKey(id);}
    private Policy(){}
}
