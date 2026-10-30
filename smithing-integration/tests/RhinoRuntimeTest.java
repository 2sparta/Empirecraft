import dev.latvian.mods.rhino.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.file.*;
import java.util.*;

/** Executes real scripts using the shipped Rhino and real Fastutil map/Java List wrappers. */
public final class RhinoRuntimeTest {
    public interface TradeFactory {}
    public static class SteelTrade implements TradeFactory {}
    public static class SteelSubclass extends SteelTrade {}
    public static class NormalTrade implements TradeFactory {}
    public static class TradeEvent {
        private final Map<Integer,List<TradeFactory>> trades;
        public TradeEvent(Map<Integer,List<TradeFactory>> trades){this.trades=trades;}
        public Map<Integer,List<TradeFactory>> getTrades(){return trades;}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Object eval(Context cx,Scriptable scope,String script){return cx.evaluateString(scope,script,"runtime-contract",1,null);}
    static void load(Context cx,Scriptable scope,Path repo,String path)throws Exception {
        cx.evaluateString(scope,Files.readString(repo.resolve(path)),path,1,null);
    }
    static void tradeTest(Context cx,Scriptable scope,Map<Integer,List<TradeFactory>> map){
        var originals=new ArrayList<NormalTrade>();
        for(int level:new int[]{1,2,4,5}){
            var ordinary=new NormalTrade();originals.add(ordinary);
            map.put(level,new ArrayList<>(List.of(new SteelTrade(),ordinary,new SteelSubclass(),new SteelTrade())));
        }
        cx.addToScope(scope,"tradeEvent",new TradeEvent(map));
        eval(cx,scope,"nativeCallbacks.forEach(function(callback){callback(tradeEvent);callback(tradeEvent)})");
        int index=0;for(int level:new int[]{1,2,4,5}){
            check(map.get(level).size()==1 && map.get(level).get(0)==originals.get(index++),"Preserve ordinary factory identity at level "+level);
        }
        check(!map.containsKey(3),"Missing profession level must not be created");
    }
    public static void main(String[] args)throws Exception {
        Path repo=Path.of(args[0]);Context cx=Context.enter();Scriptable scope=cx.initStandardObjects();
        var broken=new Int2ObjectOpenHashMap<List<TradeFactory>>();broken.put(1,new ArrayList<>());
        cx.addToScope(scope,"oldTrades",broken);
        boolean mapFailed=false;
        try{eval(cx,scope,"oldTrades.values()");}catch(ClassCastException expected){mapFailed=expected.getMessage().contains("Integer");}
        check(mapFailed,"Negative control must reproduce the uploaded Fastutil String/Integer crash");
        boolean recursionFailed=false;
        try{eval(cx,scope,"function oldCanonical(v){if(v && typeof v==='object'){const result={};Object.keys(v).forEach(k=>result[k]=oldCanonical(v[k]));return result;}return v;} oldCanonical({a:{b:'steel'}})");}
        catch(EvaluatorException expected){recursionFailed=expected.getMessage().contains("redeclaration");}
        check(recursionFailed,"Negative control must reproduce recursive const redeclaration");
        System.out.println("PASS real Rhino negative controls reproduce both uploaded startup/recipe failures");
        cx.addToScope(scope,"RhinoSteelTrade",new NativeJavaClass(cx,scope,SteelTrade.class));
        cx.addToScope(scope,"RhinoTradeEvent",new NativeJavaClass(cx,scope,TradeEvent.class));
        eval(cx,scope,"var fixtures="+Files.readString(repo.resolve("smithing-integration/build/fixtures.json"))+";");
        load(cx,scope,repo,"smithing-integration/tests/runtime-fixture.js");
        load(cx,scope,repo,"server/kubejs/startup_scripts/empirecraft_steel_trades.js");
        tradeTest(cx,scope,new Int2ObjectOpenHashMap<>());
        tradeTest(cx,scope,new HashMap<>());
        cx.addToScope(scope,"tradeEvent",new TradeEvent(new Int2ObjectOpenHashMap<>()));
        eval(cx,scope,"nativeCallbacks[0](tradeEvent)");
        System.out.println("PASS actual Java Fastutil/HashMap maps, levels 1–5, missing levels, subclasses, normal factories and repeated cleanup");
        load(cx,scope,repo,"server/kubejs/server_scripts/empirecraft_plate_compat.js");
        eval(cx,scope,"testLegacy()");
        System.out.println("PASS actual Rhino legacy inventory slots/count/NBT, idempotence, saved offers and repeated canonical tags");
        load(cx,scope,repo,"server/kubejs/server_scripts/zz_medieval_create.js");
        load(cx,scope,repo,"server/kubejs/server_scripts/zzzz_empirecraft_smithing.js");
        eval(cx,scope,"runRecipes()");
        Files.writeString(repo.resolve("smithing-integration/build/rhino-recipes.json"),cx.toString(eval(cx,scope,"JSON.stringify(registry)")));
        System.out.println("PASS unchanged production recipe scripts complete in real Rhino over every installed-JAR fixture");
        check(!Files.exists(repo.resolve("server/kubejs/server_scripts/4_tables.js")),"Retired table recipe override must stay absent");
        System.out.println("PASS retired table recipe override remains absent");
    }
}
