package ua.empirecraft.packfixes;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Static content IDs: never resolves an aliased ItemStack back to its paper replacement. */
public final class PonderSafety {
    private static final Set<String> ALLOWED_CREATE=new HashSet<>(),BLOCKED_COMPONENTS=new HashSet<>(),BLOCKED_SCENES=new HashSet<>(),BLOCKED_DESCRIPTIONS=new HashSet<>();
    static {
        try(var input=PonderSafety.class.getResourceAsStream("/ponder-safety.json")) {
            if(input==null)throw new IllegalStateException("Missing Ponder safety policy");
            var data=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            data.getAsJsonArray("allowed_create").forEach(v->ALLOWED_CREATE.add(v.getAsString()));
            data.getAsJsonArray("blocked_components").forEach(v->BLOCKED_COMPONENTS.add(v.getAsString()));
            data.getAsJsonArray("blocked_scenes").forEach(v->BLOCKED_SCENES.add(v.getAsString()));
            data.getAsJsonArray("blocked_descriptions").forEach(v->BLOCKED_DESCRIPTIONS.add(v.getAsString()));
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    public static boolean itemAllowed(String id) {
        return !BLOCKED_COMPONENTS.contains(id)&&(!id.startsWith("create:")||ALLOWED_CREATE.contains(id));
    }
    public static boolean sceneAllowed(String component,String schematic) {
        return itemAllowed(component)&&!BLOCKED_SCENES.contains(schematic);
    }
    public static boolean descriptionAllowed(String id) {
        return itemAllowed(id)&&!BLOCKED_DESCRIPTIONS.contains(id);
    }
}
