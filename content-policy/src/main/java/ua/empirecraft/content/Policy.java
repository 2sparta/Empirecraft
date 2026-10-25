package ua.empirecraft.content;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Data-only policy: safe to use from optional Mixins before Minecraft registries exist. */
public final class Policy {
    public static final Map<String,String> REPLACEMENTS;
    public static final Set<String> BLOCKS;
    public static final Set<String> ENTITIES;
    static {
        try(var stream=Policy.class.getResourceAsStream("/empirecraft-content-policy.json")) {
            if(stream==null)throw new IOException("Missing content policy");
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var replacements=new LinkedHashMap<String,String>();
            for(var row:data.getAsJsonObject("replacements").entrySet())replacements.put(row.getKey(),row.getValue().getAsString());
            REPLACEMENTS=Map.copyOf(replacements);
            BLOCKS=ids(data.getAsJsonArray("blocks"));ENTITIES=ids(data.getAsJsonArray("entities"));
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private static Set<String> ids(JsonArray array){var result=new HashSet<String>();for(var row:array)result.add(row.getAsString());return Set.copyOf(result);}
    public static boolean blocked(String id){return id!=null && REPLACEMENTS.containsKey(id);}
    public static int damage(int oldDamage,int oldMaximum,int newMaximum) {
        if(oldMaximum<=0 || newMaximum<=0)return 0;
        return (int)Math.min(newMaximum-1,Math.max(0,(long)oldDamage*newMaximum/oldMaximum));
    }
    private Policy(){}
}
