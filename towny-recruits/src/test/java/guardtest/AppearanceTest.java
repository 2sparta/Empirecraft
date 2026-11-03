package guardtest;
import ua.townyrecruits.Appearance;
import java.util.*;
public final class AppearanceTest {
 public static void main(String[] args){
  int samples=300000,typical=0,taller=0,tall=0,shorter=0;Set<Integer> skins=new HashSet<>();float min=3,max=0;
  for(int i=0;i<samples;i++){
   UUID id=new UUID(0x1234a9fe00880000L+i*123456791L,i*91815541L);
   float h=Appearance.height(id);assert Float.isFinite(h)&&h>=1.5f&&h<=2.5f;
   assert h==Appearance.height(UUID.fromString(id.toString()));
   int skin=Appearance.skin(id);assert skin>=0&&skin<128;assert skin==Appearance.skin(UUID.fromString(id.toString()));skins.add(skin);
   if(h>=1.85f&&h<=1.95f)typical++;if(h>=1.97f&&h<2.12f)taller++;if(h>=2.12f)tall++;if(h<1.80f)shorter++;min=Math.min(min,h);max=Math.max(max,h);
   assert Math.abs(2f*Appearance.renderScale(id)-h)<0.000001f;
   float factor=Appearance.scale(id,1.95f);assert Math.abs(1.95f*factor-h)<0.000001f;
   float eye=Appearance.eyeHeight(1.75f,factor,h);assert eye<h&&eye>0;
  }
  assert skins.size()==128;assert typical/(float)samples>0.915f&&typical/(float)samples<0.925f;
  assert shorter/(float)samples>0.018f&&shorter/(float)samples<0.022f : "Too many visibly short recruits";
  assert taller/(float)samples>0.047f&&taller/(float)samples<0.053f;
  assert tall/(float)samples>0.008f&&tall/(float)samples<0.012f;
  assert min<1.501f&&max>2.499f;
  System.out.printf(Locale.ROOT,"PASS %d UUIDs: all 128 skins, stable identities, height %.4f..%.4f, %.2f%% near player, %.2f%% moderately taller, %.2f%% visibly short, %.2f%% very tall, consistent eye/box scale%n",samples,min,max,100.0*typical/samples,100.0*taller/samples,100.0*shorter/samples,100.0*tall/samples);
 }
}
