import ua.empirecraft.recruits.RationMath;
public class RationTests {
 public static void main(String[] args){int checks=0;
  for(int i=0;i<100;i++){float h=RationMath.drain(i,100000,101000,2);assert h>=0&&h<=i;checks++;}
  assert Math.abs(RationMath.drain(100,100000,160000,2)-98)<.001;checks++;
  assert RationMath.drain(100,100000,0,2)==100;checks++;
  assert RationMath.drain(100,100000,3700000,2)==98;checks++;
  assert RationMath.restore(20,10)==70;checks++;assert RationMath.restore(98,10)==100;checks++;
  assert !RationMath.starvation(1,0,500000,10);checks++;assert !RationMath.starvation(0,0,9999,10);checks++;assert RationMath.starvation(0,0,10000,10);checks++;
  float hunger=100;long now=100000;for(int i=0;i<3001;i++){hunger=RationMath.drain(hunger,now,now+1000,2);now+=1000;}assert hunger==0;checks++;
  float health=20;long at=0;for(int i=1;i<=200;i++){if(RationMath.starvation(hunger,at,i*1000L,10)){health-=1;at=i*1000L;}}assert health==0;checks++;
  System.out.println("PASS RationTests: "+checks+" hunger, supply conversion, starvation and clock checks");
 }
}
