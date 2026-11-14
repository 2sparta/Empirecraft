import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import com.google.gson.*;
import me.lucko.spark.proto.SparkSamplerProtos;

public class ReadSpark {
    static Map<Class<?>,Map<String,Method>> getters=new HashMap<>();
    static JsonElement json(Object value)throws Exception {
        if(value==null)return JsonNull.INSTANCE;
        if(value instanceof Number n)return new JsonPrimitive(n);
        if(value instanceof Boolean b)return new JsonPrimitive(b);
        if(value instanceof CharSequence s)return new JsonPrimitive(s.toString());
        if(value instanceof Enum<?> e)return new JsonPrimitive(e.name());
        if(value instanceof Map<?,?> map){var out=new JsonObject();for(var e:map.entrySet())out.add(e.getKey().toString(),json(e.getValue()));return out;}
        if(value instanceof Iterable<?> list){var out=new JsonArray();for(var e:list)out.add(json(e));return out;}
        var cls=value.getClass();
        if(!cls.getName().startsWith("me.lucko.spark.proto."))return new JsonPrimitive(value.toString());
        var methods=getters.computeIfAbsent(cls,c->{
            var found=new LinkedHashMap<String,Method>();
            for(var field:c.getFields())if(field.getName().endsWith("_FIELD_NUMBER")) {
                String key=field.getName().replace("_FIELD_NUMBER","");StringBuilder stem=new StringBuilder();
                for(String token:key.toLowerCase(Locale.ROOT).split("_"))stem.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
                for(String suffix:new String[]{"Map","List",""})try {found.put(key.toLowerCase(Locale.ROOT),c.getMethod("get"+stem+suffix));break;}catch(NoSuchMethodException ignored){}
            }return found;
        });
        var out=new JsonObject();for(var e:methods.entrySet())out.add(e.getKey(),json(e.getValue().invoke(value)));return out;
    }
    public static void main(String[] args)throws Exception {
        var parsed=SparkSamplerProtos.SamplerData.parseFrom(Files.readAllBytes(Path.of(args[0])));
        Files.writeString(Path.of(args[1]),new Gson().toJson(json(parsed)));
        System.out.println("SPARK_DECODED threads="+parsed.getThreadsCount()+" windows="+parsed.getTimeWindowsCount());
    }
}
