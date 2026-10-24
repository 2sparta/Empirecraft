import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import journeymap.client.ui.theme.Theme;

/** Reads our definition through the real Theme model shipped in JourneyMap 6.0.7. */
public final class NativeThemeTest {
    private static int assertions;
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private static void compare(JsonElement input, JsonElement nativeValue, String path) {
        if (input.isJsonObject()) {
            check(nativeValue != null && nativeValue.isJsonObject(), "Native object: " + path);
            for (var member : input.getAsJsonObject().entrySet()) {
                check(nativeValue.getAsJsonObject().has(member.getKey()), "Native field: " + path + "." + member.getKey());
                compare(member.getValue(), nativeValue.getAsJsonObject().get(member.getKey()), path + "." + member.getKey());
            }
        } else if (input.isJsonPrimitive() && input.getAsJsonPrimitive().isNumber()) {
            check(Math.abs(input.getAsDouble()-nativeValue.getAsDouble())<0.00001, "Native number: " + path);
        } else check(input.equals(nativeValue), "Native value: " + path);
    }
    public static void main(String[] args) throws Exception {
        Path base = Path.of(args[0]);
        Gson gson = new Gson();
        String definition = Files.readString(base.resolve("EmpireCraftAtlas.theme2.json"));
        Theme theme = gson.fromJson(definition, Theme.class);
        compare(JsonParser.parseString(definition), gson.toJsonTree(theme), "theme");
        check(theme.schema == 2, "Schema 2");
        check(theme.name.equals("EmpireCraft Atlas"), "Theme name");
        for (int size : new int[] {256,512}) {
            var mask = ImageIO.read(base.resolve("minimap/circle/mask_"+size+".png").toFile());
            var rim = ImageIO.read(base.resolve("minimap/circle/rim_"+size+".png").toFile());
            check(mask.getWidth()==size && mask.getHeight()==size, "Native mask size");
            check((mask.getRGB(size/2,size/2)>>>24)==255, "Mask shows mapped terrain");
            check((mask.getRGB(0,0)>>>24)==0, "Mask clips corners");
            check((rim.getRGB(size/2,size/2)>>>24)==0, "Frame does not obscure mapped terrain");
        }
        check(theme.control.button.iconOn.getColor()==0x422e22, "Native ink color parser");
        System.out.println("PASS real JourneyMap 6.0.7 Theme model and PNG masks: "+assertions+" assertions");
    }
}
