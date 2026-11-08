import java.util.Random;
import ua.empirecraft.packfixes.FlagWind;

public final class FlagWindTest {
  static void near(double a,double b){if(Math.abs(a-b)>1e-8)throw new AssertionError(a+" != "+b);}
  public static void main(String[] args){
    var random=new Random(9413);int checks=0;
    for(int i=0;i<100000;i++){
      double x=random.nextDouble()*2-1,z=random.nextDouble()*2-1,yaw=FlagWind.yaw(x,z,17),length=Math.hypot(x,z);
      near(Math.cos(Math.toRadians(yaw)),x/length);near(-Math.sin(Math.toRadians(yaw)),z/length);checks+=2;
    }
    var spring=new FlagWind.Turn();near(spring.update(179,0),179);
    double next=spring.update(-179,1);if(Math.abs(FlagWind.wrap(next-179))>=2)throw new AssertionError("Took the long path across north");
    near(spring.update(-179,1),next);near(spring.update(30,-100),30);
    near(FlagWind.yaw(0,0,17),17);near(FlagWind.yaw(Double.NaN,0,17),17);
    System.out.println("FLAG_WIND_PASS checks="+checks+"; quadrants, shortest-path smoothing, paused frames, time reset and calm fallback");
  }
}
