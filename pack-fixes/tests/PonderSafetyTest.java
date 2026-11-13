import com.google.gson.JsonParser;
import java.nio.file.*;
import ua.empirecraft.packfixes.PonderSafety;

public final class PonderSafetyTest {
    public static void main(String[] args)throws Exception {
        var report=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();int checks=0;
        for(String scene:report.getAsJsonObject("blocked_schematics").keySet()) {
            if(PonderSafety.sceneAllowed("minecraft:paper",scene))throw new AssertionError("Aliased paper has unsafe Ponder "+scene);checks++;
        }
        for(var scene:report.getAsJsonArray("safe_schematics")) {
            if(!PonderSafety.sceneAllowed("create:shaft",scene.getAsString()))throw new AssertionError("Safe scene hidden "+scene);checks++;
        }
        for(var id:report.getAsJsonArray("blocked_descriptions")) {
            if(PonderSafety.descriptionAllowed(id.getAsString()))throw new AssertionError("Nether description "+id);checks++;
        }
        if(PonderSafety.itemAllowed("create:empty_blaze_burner")||PonderSafety.itemAllowed("minecraft:blaze_rod"))throw new AssertionError("Retired content allowed");
        if(!PonderSafety.itemAllowed("create:water_wheel")||!PonderSafety.descriptionAllowed("create:mechanical_press"))throw new AssertionError("Active content hidden");
        System.out.println("PONDER_SAFETY_PASS checks="+(checks+4)+" paper_empty_blaze_burner=blocked");
    }
}
