import java.util.*;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import ua.empirecraft.packfixes.PaletteEntries;

/** Compare against Minecraft's real iterator, including sparse non-palette maps. */
public final class PaletteEntriesTest {
    static int checks;
    static <T> List<T> vanilla(CrudeIncrementalIntIdentityHashBiMap<T> map) {var out=new ArrayList<T>();map.iterator().forEachRemaining(out::add);return out;}
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Palette traversal "+checks);}
    public static void main(String[] args) {
        var random=new Random(481193);
        for(int size:new int[]{0,1,2,3,4,15,16,31,64,127,255,256,511,1024,4096}) {
            for(int capacity:new int[]{Math.max(1,size),8192}) {
                var map=CrudeIncrementalIntIdentityHashBiMap.<Object>create(capacity);
                var expected=new ArrayList<Object>();
                for(int i=0;i<size;i++){var value=new Object();expected.add(value);map.add(value);}
                check(PaletteEntries.dense(map).equals(vanilla(map)));
                check(PaletteEntries.dense(map).equals(expected));
                check(PaletteEntries.dense(map.copy()).equals(expected));
                map.clear();check(PaletteEntries.dense(map).isEmpty());
            }
        }
        for(int trial=0;trial<200;trial++) {
            var map=CrudeIncrementalIntIdentityHashBiMap.<Object>create(256);
            int size=random.nextInt(100);for(int i=0;i<size;i++)map.add(new Object());
            check(PaletteEntries.dense(map).equals(vanilla(map)));
        }
        var sparse=CrudeIncrementalIntIdentityHashBiMap.<Object>create(16);sparse.addMapping(new Object(),5);
        check(PaletteEntries.dense(sparse)==null);
        var map=CrudeIncrementalIntIdentityHashBiMap.<Object>create(8192);
        for(int i=0;i<64;i++)map.add(new Object());
        long sink=0;for(int i=0;i<4000;i++){sink+=vanilla(map).size();sink+=PaletteEntries.dense(map).size();}
        long[] oldTimes=new long[7],newTimes=new long[7];
        for(int round=0;round<7;round++) {
            long start=System.nanoTime();for(int i=0;i<12000;i++)sink+=vanilla(map).size();oldTimes[round]=System.nanoTime()-start;
            start=System.nanoTime();for(int i=0;i<12000;i++)sink+=PaletteEntries.dense(map).size();newTimes[round]=System.nanoTime()-start;
        }
        Arrays.sort(oldTimes);Arrays.sort(newTimes);
        System.out.println("PALETTE_ENTRIES_PASS checks="+checks+" old_median_ns="+oldTimes[3]+" new_median_ns="+newTimes[3]+" ratio="+((double)oldTimes[3]/newTimes[3])+" sink="+sink);
    }
}
