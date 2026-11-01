import org.joml.primitives.AABBi;
import java.lang.reflect.*;
import java.util.*;

/** Exercise the actual patched production Job cursor without a Minecraft world. */
public final class MassBoundsTest {
    static int checks;
    static void set(Object o,String name,Object v)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
    static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    public static void main(String[] args)throws Exception{
        Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)singleton.get(null);
        Class<?> type=Class.forName("ua.empirecraft.shipphysics.MassSync$Job");
        Method next=type.getDeclaredMethod("nextSection");next.setAccessible(true);
        for(int min:new int[]{-33,-16,-1,0,1,15,16,29999952})for(int size:new int[]{1,2,15,16,17,32}){
            int max=min+size;Object job=unsafe.allocateInstance(type);
            set(job,"box",new AABBi(min,min,min,max,max,max));
            for(String axis:new String[]{"sx","sy","sz"})set(job,axis,min>>4);
            Set<String> visited=new HashSet<>();
            while(!(Boolean)get(job,"done")){
                int x=(Integer)get(job,"sx"),y=(Integer)get(job,"sy"),z=(Integer)get(job,"sz");
                if(x>(max-1)>>4 || y>(max-1)>>4 || z>(max-1)>>4)throw new AssertionError("read beyond exclusive hull bounds");
                if(!visited.add(x+":"+y+":"+z))throw new AssertionError("repeated section");
                checks++;next.invoke(job);
            }
            int side=((max-1)>>4)-(min>>4)+1;
            if(visited.size()!=side*side*side)throw new AssertionError("missing hull section");checks++;
        }
        System.out.println("MASS_BOUNDS_PASS assertions="+checks);
    }
}
