import dev.latvian.mods.rhino.*;
import java.nio.file.*;

public final class ContentRhinoTest {
    public static void main(String[] args)throws Exception {
        Context cx=Context.enter();Scriptable scope=cx.initStandardObjects();
        for(String file:args)cx.evaluateString(scope,Files.readString(Path.of(file)),file,1,null);
        System.out.println(cx.toString(cx.evaluateString(scope,"checkContentScripts()","assertions",1,null)));
    }
}
