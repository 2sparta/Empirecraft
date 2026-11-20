package ua.empirecraft.launcherclient;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
/** Local, account-bound appearance cache. Never sends or restores items to the server. */
public final class HomeEquipment {
 public static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
 private static final EnumMap<EquipmentSlot,ItemStack> armor=new EnumMap<>(EquipmentSlot.class);
 private static String account="";
 private static long updated, lastWrite;
 private static boolean known,dirty,warned;
 private static String owner(){var u=Minecraft.getInstance().getUser();return u.getName()+"/"+u.getUuid();}
 private static Path file(){try{String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((LauncherClient.SERVER+"/"+owner()).getBytes(StandardCharsets.UTF_8)));return Minecraft.getInstance().gameDirectory.toPath().resolve("config/empirecraft-menu/equipment-"+key+".json");}catch(Exception e){throw new IllegalStateException(e);}}
 public static void load(){
  if(account.equals(owner()))return;account=owner();armor.clear();known=false;updated=0;dirty=false;
  try{var p=file();if(!Files.isRegularFile(p)||Files.size(p)>65536)return;var j=JsonParser.parseString(Files.readString(p)).getAsJsonObject();
   if(j.get("format").getAsInt()!=1||!LauncherClient.SERVER.equals(j.get("server").getAsString())||!account.equals(j.get("account").getAsString()))return;
   var slots=j.getAsJsonObject("armor");for(var slot:SLOTS)if(slots.has(slot.getName()))armor.put(slot,sanitize(ItemStack.of(TagParser.parseTag(slots.get(slot.getName()).getAsString()))));
   updated=Math.max(0,j.get("updatedAt").getAsLong());known=true;
  }catch(Exception e){armor.clear();known=false;}
 }
 public static ItemStack sanitize(ItemStack stack){
  if(!(stack.getItem() instanceof ArmorItem))return ItemStack.EMPTY;
  var copy=stack.copy();copy.setCount(1);if(copy.hasTag())copy.getTag().remove("EmpirecraftArmorLayers");return copy;
 }
 public static ItemStack get(EquipmentSlot slot){load();return armor.getOrDefault(slot,ItemStack.EMPTY).copy();}
 public static boolean known(){load();return known;}
 public static long updated(){load();return updated;}
 public static void capture(Player player,String host,int port){
  var mc=Minecraft.getInstance();if(player==null||player!=mc.player||!LauncherClient.allowed(host,port))return;load();
  var next=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);for(var slot:SLOTS)next.put(slot,sanitize(player.getItemBySlot(slot)));
  boolean changed=!known;for(var slot:SLOTS)if(!ItemStack.matches(next.get(slot),armor.getOrDefault(slot,ItemStack.EMPTY)))changed=true;
  if(changed){armor.clear();armor.putAll(next);known=true;updated=System.currentTimeMillis();dirty=true;}
  if(dirty&&System.currentTimeMillis()-lastWrite>=5000)flush();
 }
 private static JsonObject items(){var j=new JsonObject();for(var slot:SLOTS){var stack=armor.getOrDefault(slot,ItemStack.EMPTY);j.addProperty(slot.getName(),stack.save(new CompoundTag()).toString());}return j;}
 public static void flush(){if(!dirty)return;try{var j=new JsonObject();j.addProperty("format",1);j.addProperty("server",LauncherClient.SERVER);j.addProperty("account",account);j.addProperty("updatedAt",updated);j.add("armor",items());String value=j.toString();if(value.getBytes(StandardCharsets.UTF_8).length>65536)return;atomic(file(),value);lastWrite=System.currentTimeMillis();dirty=false;}catch(Exception e){if(!warned){warned=true;com.mojang.logging.LogUtils.getLogger().warn("Could not cache home armor appearance",e);}}}
 static void atomic(Path file,String text)throws java.io.IOException {Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(tmp,text,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}
 /** Test/reload hook; cached items are revalidated against the current registry. */
 public static void reload(){account="";load();}
}
