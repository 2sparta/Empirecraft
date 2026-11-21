import java.util.*;import com.quintonc.vs_sails.ship.FloodModel;
public class WaterRegression{
 public static void main(String[]args){
  var graph=FloodModelTest.room(12,3);int[] q=new int[36];q[2]=8;var m=new FloodModel(graph,q,0);for(int i=0;i<1000;i++)m.step(128);for(int x=0;x<12;x++)if(m.amount(x*3+1)>0||m.amount(x*3+2)>0)throw new AssertionError("Water hangs above a free lower level");if(m.total()!=8)throw new AssertionError("Volume lost");
  var sea=new FloodModel(graph,new int[36],0);var port=new FloodModel.Port(0,.125,0,0,true,true,false);sea.setPorts(List.of(port));for(int i=0;i<1000;i++)sea.step(128);int[] settled=new int[36];for(int i=0;i<36;i++)settled[i]=sea.amount(i);
  for(int step=0;step<100;step++){sea.step(128);for(int i=0;i<36;i++)if(sea.amount(i)!=settled[i])throw new AssertionError("Shallow ocean-connected water flickers");}
  int removed=sea.bail(0,8);if(removed<=0)throw new AssertionError("Stable water cannot be bailed");
  var moved=new FloodModel(graph,new int[36],0);moved.setPorts(List.of(new FloodModel.Port(0,.25,0,0,true,true,false)));moved.shiftSea(1);for(int i=0;i<1000;i++)moved.step(128);if(moved.amount(0)!=8)throw new AssertionError("Downward ship motion must raise relative ocean head");moved.shiftSea(-2);for(int i=0;i<1000;i++)moved.step(128);if(moved.amount(0)!=0)throw new AssertionError("Raised breach must lose its old pressure head");
  System.out.println("WATER_REGRESSION_PASS gravity=true shallowStable=true bail=true relativeSeaHead=true");
 }
}
