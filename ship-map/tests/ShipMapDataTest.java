package ua.empirecraft.townymap;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public class ShipMapDataTest {
 @TempDir Path directory;
 ShipObservation sight(long time,double x,double yaw,double heading,String icon){return new ShipObservation(1,"minecraft:overworld",x,64,8,heading,3,time,"Каравела","Січ","Місто","STATE","{}",yaw,icon);}
 @Test void silhouettesAreGeneratedFromActualTopColumnsAndKeepAsymmetry(){
  var p=new ShipIcon.Projection(100,40,100,112,80,120,new ShipIcon.Projection.Columns(){public int top(int x,int z){return 70;}public int color(int x,int y,int z){if(y==70&&x<104&&z<106)return 0xffeeeecc;return y==40?0xff81633e:0;}});
  int calls=0;while(!p.complete()){assertTrue(p.step(31)<=31);assertTrue(++calls<4000);}
  var icon=p.image();assertEquals(0,icon.pixel(0,0));assertEquals(0xffeeeecc,icon.pixel(20,10));assertEquals(0xff81633e,icon.pixel(40,50));
  assertNotEquals(icon.pixel(20,10),icon.pixel(43,53));
 }
 @Test void smallTickBudgetsSurviveTallTransparentColumns(){
  int[] reads={0};var p=new ShipIcon.Projection(0,-64,0,1,320,1,new ShipIcon.Projection.Columns(){public int top(int x,int z){reads[0]++;return 319;}public int color(int x,int y,int z){reads[0]++;return y==-64?0xff888888:0;}});
  int loops=0;while(!p.complete()){int before=reads[0];int used=p.step(1);assertTrue(used<=1);assertEquals(used,reads[0]-before);assertTrue(++loops<6000);}
  assertEquals(0xff888888,p.image().pixel(32,32));assertTrue(reads[0]<400,"Repeated pixels reuse resolved columns");
 }
 @Test void unavailableChunksSuspendWithoutReadingOrInventingPixels(){
  int[] blocks={0};var p=new ShipIcon.Projection(0,0,0,10,10,10,new ShipIcon.Projection.Columns(){public int top(int x,int z){return Integer.MIN_VALUE;}public int color(int x,int y,int z){blocks[0]++;return 0xffaaaaaa;}});
  assertTrue(p.step(100)<=100);assertTrue(p.blocked());assertFalse(p.complete());assertEquals(0,blocks[0]);assertThrows(IllegalStateException.class,p::image);
 }
 @Test void imagesAreImmutableAndDiskRoundTripsAreExact()throws Exception{
  int[] pixels=new int[4096];pixels[22]=0xff3988ab;var icon=new ShipIcon(pixels);pixels[22]=0;assertEquals(0xff3988ab,icon.pixels()[22]);var copy=icon.pixels();copy[22]=0;assertEquals(0xff3988ab,icon.pixels()[22]);
  var bytes=new ByteArrayOutputStream();icon.write(new DataOutputStream(bytes));var restored=ShipIcon.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));assertEquals(icon.key(),restored.key());assertArrayEquals(icon.pixels(),restored.pixels());
  assertThrows(IOException.class,()->ShipIcon.read(new DataInputStream(new ByteArrayInputStream(new byte[1]))));assertThrows(IOException.class,()->ShipIcon.read(new DataInputStream(new ByteArrayInputStream(new byte[]{0,0,0,99}))));
 }
 @Test void hullRotationAndVelocityBearingAreIndependentAndPersistTogether()throws Exception{
  String key=new ShipIcon(new int[4096]).key();var original=sight(100,8,90,270,key);
  try(var store=new ShipObservationStore(directory,e->fail(e))){store.save(new UUID(0,1),List.of(original));}
  try(var store=new ShipObservationStore(directory,e->fail(e))){var row=store.load(new UUID(0,1)).get(1);assertEquals(original,row);assertEquals(90,row.hullYaw());assertEquals(270,row.heading());assertEquals(key,row.icon());}
 }
 @Test void legacyOpticalLogsKeepTheirCoordinatesAndOwnership(){
  var json=JsonParser.parseString(new Gson().toJson(sight(100,12,90,270,""))).getAsJsonObject();json.remove("hullYaw");json.remove("icon");
  var old=new Gson().fromJson(json,ShipObservation.class);assertEquals(12,old.x());assertEquals("Січ",old.country());assertEquals(270,old.heading());assertEquals("",old.icon());
 }
 @Test void liveMovementCannotMutateAnExplicitSnapshot(){
  var saved=new ShipObservation.Log();var captured=sight(100,12,90,270,"");saved.observe(captured);
  for(int n=0;n<1000;n++){var live=sight(101+n,13+n,180,90,"");assertNotEquals(captured.x(),live.x());}
  assertEquals(captured,saved.get(1));
 }
 @Test void malformedKeysAndOutOfOrderPacketsAreRejectedWithoutChangingHistory(){
  assertThrows(IllegalArgumentException.class,()->sight(100,12,90,270,"../other-file"));
  assertThrows(IllegalArgumentException.class,()->new StrategyNetwork.Save(-1));assertThrows(IllegalArgumentException.class,()->new StrategyNetwork.IconRequest(-1));
  assertThrows(IllegalArgumentException.class,()->new StrategyNetwork.Contacts("[]",1,1,0,9));
  var pages=new ShipObservation.Pages();var original=sight(100,12,90,270,"");assertEquals(List.of(original),pages.accept(2,0,1,List.of(original)).orElseThrow());assertTrue(pages.accept(1,0,1,List.of(sight(20,3,0,0,""))).isEmpty());
 }
 @Test void clientViewDistanceIsBoundedToValidMinecraftValues(){assertEquals(2,ShipViewDistance.clamp(-1));assertEquals(32,ShipViewDistance.clamp(Integer.MAX_VALUE));assertEquals(8,ShipViewDistance.clamp(8));}
 @Test void portraitsRefreshOnlyAfterTwentyFourRealHours(){
  long t=1_790_000_000_000L;assertTrue(ShipIcon.refreshDue(t,0));assertFalse(ShipIcon.refreshDue(t,t));
  assertFalse(ShipIcon.refreshDue(t+ShipIcon.REFRESH_MILLIS-1,t));assertTrue(ShipIcon.refreshDue(t+ShipIcon.REFRESH_MILLIS,t));
  assertFalse(ShipIcon.refreshDue(t-1000,t));
 }
 @Test void oneTimePortraitForAHugeHullStillUsesOnlyFixedMemory(){
  var p=new ShipIcon.Projection(0,0,0,4000,384,3000,new ShipIcon.Projection.Columns(){public int top(int x,int z){return 1;}public int color(int x,int y,int z){return 0xffaa8844;}});
  int steps=0;while(!p.complete()){assertTrue(p.step(256)<=256);assertTrue(++steps<40);}assertEquals(4096,p.image().pixels().length);
 }
}
