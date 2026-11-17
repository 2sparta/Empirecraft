package ua.empirecraft.chinese;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.armory.*;
public final class ChineseRules {
 private ChineseRules(){}
 public static boolean subject(ItemStack stack){var id=ForgeRegistries.ITEMS.getKey(stack.getItem());return id!=null&&id.getNamespace().equals("chineseweapons");}
 public static String combat(String id){if(!id.startsWith("chineseweapons:"))return null;var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));if(!(item instanceof ArmorItem armor))return null;return armor.getEquipmentSlot()==EquipmentSlot.HEAD?"plate":"mail";}
 public static String weapon(String id){if(!id.startsWith("chineseweapons:"))return null;if(id.startsWith("chineseweapons:wooden_")||id.contains("guduo"))return "chineseweapons:mace";if(id.contains("glaive")||id.contains("dagger_axe")||id.contains("halberd")||id.contains("saber")||id.contains("javelin"))return "chineseweapons:sword";return null;}
 public static Profile profile(ItemStack stack){var item=stack.getItem();if(!(item instanceof ArmorItem armor)||!subject(stack))return null;var id=ForgeRegistries.ITEMS.getKey(item).getPath();List<String> forms;String construction;
  if(armor.getEquipmentSlot()==EquipmentSlot.HEAD){construction="plate";forms=id.contains("face_guard")?List.of("dome","nape","visor"):List.of("dome","nape");}
  else{construction="lamellar";forms=switch(armor.getEquipmentSlot()){case CHEST->List.of("lamellar_panel","lamellar_panel","lamellar_panel","shoulders");case LEGS->List.of("lamellar_panel","lamellar_panel","knees");default->List.of("lamellar_panel","ankles");};}
  return new Profile("iron",construction,forms);
 }
}
