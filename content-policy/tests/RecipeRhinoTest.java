import dev.latvian.mods.rhino.*;
import java.nio.file.*;
public final class RecipeRhinoTest {
    public static void main(String[] args)throws Exception {
        Context cx=Context.enter();Scriptable scope=cx.initStandardObjects();
        cx.evaluateString(scope,"var fixtures="+Files.readString(Path.of(args[0]))+";","fixtures",1,null);
        for(int i=1;i<args.length;i++)cx.evaluateString(scope,Files.readString(Path.of(args[i])),args[i],1,null);
        System.out.println(cx.toString(cx.evaluateString(scope,"verifyProgression()","assertions",1,null)));
    }
}
