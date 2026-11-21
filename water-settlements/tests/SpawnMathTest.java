import ua.empirecraft.frontier.SpawnMath;
import java.util.Random;
public class SpawnMathTest{public static void main(String[]args){var r=new Random(17);for(double[] ring:new double[][]{{0,2000},{1000,3000},{0,30000}})for(int i=0;i<100000;i++){var p=SpawnMath.point(-607.5,-486.5,ring[0],ring[1],r.nextDouble(),r.nextDouble());double d=Math.hypot(p[0]+607.5,p[1]+486.5);if(d<ring[0]-1e-8||d>ring[1]+1e-8)throw new AssertionError(d);}System.out.println("SPAWN_RING_PASS points=300000");}}
