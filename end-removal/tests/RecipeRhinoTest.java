import dev.latvian.mods.rhino.*;
import java.nio.file.*;
public final class RecipeRhinoTest {
    public static void main(String[] args)throws Exception{
        Context context=Context.enter();Scriptable scope=context.initStandardObjects();
        context.evaluateString(scope,"var fixtures="+Files.readString(Path.of(args[0]))+";","fixtures",1,null);
        for(int i=1;i<args.length;i++)context.evaluateString(scope,Files.readString(Path.of(args[i])),args[i],1,null);
        System.out.println(context.toString(context.evaluateString(scope,"verifyNoEnd()","assertions",1,null)));
    }
}
