package ua.empirecraft.launcherclient;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
public final class HomePreferences {
 private static boolean loaded, close=true, armor=true;
 private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/empirecraft-home.json");}
 private static void load(){if(loaded)return;loaded=true;try{if(Files.isRegularFile(file())&&Files.size(file())<4096){var j=JsonParser.parseString(Files.readString(file())).getAsJsonObject();if(j.has("waistUp"))close=j.get("waistUp").getAsBoolean();if(j.has("serverArmor"))armor=j.get("serverArmor").getAsBoolean();}}catch(Exception ignored){}}
 public static boolean closeUp(){load();return close;}
 public static boolean armor(){load();return armor;}
 public static void toggleClose(){load();close=!close;save();}
 public static void toggleArmor(){load();armor=!armor;save();}
 private static void save(){try{var j=new JsonObject();j.addProperty("waistUp",close);j.addProperty("serverArmor",armor);HomeEquipment.atomic(file(),j.toString());}catch(Exception e){com.mojang.logging.LogUtils.getLogger().warn("Could not save home appearance preference",e);}}
}
