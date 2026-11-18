import java.nio.file.*;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
public class AnimationCheck {
 public static void main(String[] args)throws Exception{
  try(var r=Files.newBufferedReader(Path.of(args[0]))){
   var all=AnimationSerializing.deserializeAnimation(r);
   if(all.size()!=1||!all.get(0).isInfinite||all.get(0).getPart("body")==null)throw new AssertionError("Invalid looping breathing animation");
   System.out.println("ANIMATION_PASS "+all.get(0).extraData+" loop="+all.get(0).isInfinite+" ticks="+all.get(0).getLength());
  }
 }
}
