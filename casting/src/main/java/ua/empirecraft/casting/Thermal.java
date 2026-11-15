package ua.empirecraft.casting;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.capabilities.heat.*;

/** All heat is stored, cooled and synchronized by TFC's native item capability. */
public final class Thermal {
 private static final JsonObject DATA=load();
 private static final TagKey<Item> TONGS=TagKey.create(Registries.ITEM,new ResourceLocation("overgeared:tongs"));
 public static final float BURN_TEMPERATURE=80;
 private static JsonObject load(){try(var in=Thermal.class.getResourceAsStream("/thermal.json")){return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
 public static String id(ItemStack s){return Objects.toString(ForgeRegistries.ITEMS.getKey(s.getItem()),"");}
 public static boolean legacy(String id){return DATA.getAsJsonObject("legacy").has(id);}
 public static JsonObject material(String id){
  if(id==null||!id.contains(":"))return null;
  if(id.equals("empirecraft_tfc:barrel_blank")||id.equals("tfc_muskets:musket_barrel")||id.equals("tfc_muskets:blunderbuss_barrel"))return DATA.getAsJsonObject("metals").getAsJsonObject("steel");
  if(id.equals("empirecraft_tfc:gun_lock")||id.startsWith(Casting.ID+":arquebus_")||id.equals(Casting.ID+":matchlock"))return DATA.getAsJsonObject("metals").getAsJsonObject("wrought_iron");
  String namespace=id.substring(0,id.indexOf(':'));if(!Set.of("minecraft","forge","tfc","overgeared","create","createbigcannons","magistuarmory","magistuarmoryaddon","antiquelegacy","darkagesarmory","slavicarmory","epic_knights__japanese_armory",Casting.ID,"empirecraft_tfc","tfc_muskets").contains(namespace))return null;
  String path=id.substring(id.indexOf(':')+1);var names=new ArrayList<>(DATA.getAsJsonObject("metals").keySet());names.add("iron");names.sort(Comparator.comparingInt(String::length).reversed());for(String m:names){
   if(path.startsWith(m+"_")||path.startsWith(m+"/")||path.endsWith("/"+m))return DATA.getAsJsonObject("metals").getAsJsonObject(m.equals("iron")?"wrought_iron":m);
  }return null;
 }
 public static void attach(AttachCapabilitiesEvent<ItemStack> e){var s=e.getObject();if(s.isEmpty()||e.getCapabilities().containsKey(HeatCapability.KEY))return;var m=material(id(s));if(m!=null)e.addCapability(HeatCapability.KEY,new HeatHandler(m.get("capacity").getAsFloat(),m.get("working").getAsFloat(),m.get("welding").getAsFloat()));}
 public static ItemStack normalize(ItemStack s){
  if(s.isEmpty())return s;String old=id(s);if(old.equals("create:dough")||old.equals("farmersdelight:wheat_dough")||old.equals("create:wheat_flour")){var nbt=s.save(new net.minecraft.nbt.CompoundTag());nbt.putString("id",old.endsWith("flour")?"tfc:food/wheat_flour":"tfc:food/wheat_dough");return ItemStack.of(nbt);}boolean marked=s.hasTag()&&s.getTag().getBoolean("Heated");boolean migrate=legacy(old);if(!migrate&&!marked){if(s.hasTag())s.getTag().remove("HeatedSince");return s;}float previous=HeatCapability.getTemperature(s);
  if(migrate){var nbt=s.save(new net.minecraft.nbt.CompoundTag());nbt.putString("id",DATA.getAsJsonObject("legacy").get(old).getAsString());s=ItemStack.of(nbt);}
  if(s.hasTag()){s.getTag().remove("Heated");s.getTag().remove("HeatedSince");}
  if(migrate||marked){var heat=HeatCapability.get(s);if(heat!=null)heat.setTemperature(Math.max(previous,Math.max(BURN_TEMPERATURE,heat.getWorkingTemperature()*1.1f)));}
  return s;
 }
 public static void heatFurnaceOutput(ItemStack s){var heat=HeatCapability.get(s);if(heat==null)return;float temperature=1100;var m=material(id(s));if(m!=null)temperature=Math.min(temperature,m.get("melting").getAsFloat()-1);heat.setTemperatureIfWarmer(temperature);}
 public static boolean quench(ItemStack s){if(HeatCapability.getTemperature(s)<=0)return false;HeatCapability.setTemperature(s,0);return true;}
 public static void player(TickEvent.PlayerTickEvent e){
  var p=e.player;if(p.level().isClientSide)return;var inv=p.getInventory();
  if(e.phase==TickEvent.Phase.START){for(int i=0;i<inv.getContainerSize();i++){var s=inv.getItem(i);var n=normalize(s);if(n!=s)inv.setItem(i,n);}var carried=p.containerMenu.getCarried();var n=normalize(carried);if(n!=carried)p.containerMenu.setCarried(n);return;}
  if(p.tickCount%20!=0||p.isCreative()||p.isSpectator()||p.hasEffect(MobEffects.FIRE_RESISTANCE))return;
  boolean hot=HeatCapability.getTemperature(p.containerMenu.getCarried())>=BURN_TEMPERATURE;
  for(int i=0;!hot&&i<inv.getContainerSize();i++)hot=HeatCapability.getTemperature(inv.getItem(i))>=BURN_TEMPERATURE;
  if(!hot)return;var main=p.getMainHandItem();var off=p.getOffhandItem();var tongs=usable(main)?main:usable(off)?off:ItemStack.EMPTY;
  if(tongs.isEmpty())p.hurt(p.damageSources().hotFloor(),1);else if(p.tickCount%40==0)tongs.hurtAndBreak(1,p,l->l.broadcastBreakEvent(tongs==main?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND));
 }
 private static boolean usable(ItemStack s){return s.is(TONGS)&&(!s.isDamageableItem()||s.getDamageValue()<s.getMaxDamage());}
 public static JsonElement canonical(JsonElement value){
  if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()){var v=value.getAsString();return new JsonPrimitive(legacy(v)?DATA.getAsJsonObject("legacy").get(v).getAsString():v);}
  if(value.isJsonArray()){var a=new JsonArray();for(var v:value.getAsJsonArray())a.add(canonical(v));return a;}
  if(value.isJsonObject()){var o=new JsonObject();for(var e:value.getAsJsonObject().entrySet())o.add(e.getKey(),canonical(e.getValue()));return o;}return value.deepCopy();
 }
 public static void forging(JsonObject json){
  if(!json.has("key"))return;var key=json.getAsJsonObject("key");
  for(var e:key.entrySet()){if(!e.getValue().isJsonObject())continue;var ingredient=canonical(e.getValue()).getAsJsonObject();if(ingredient.has("type"))continue;
   String id=ingredient.has("item")?ingredient.get("item").getAsString():ingredient.has("tag")?ingredient.get("tag").getAsString():"";var metal=material(id);
   if(metal!=null){var base=ingredient.deepCopy();base.remove("requires_heated");base.remove("transfer_nbt");var hot=new JsonObject();hot.addProperty("type","tfc:heatable");hot.add("ingredient",base);hot.addProperty("min_temp",metal.get("working").getAsInt());if(ingredient.has("transfer_nbt"))hot.add("transfer_nbt",ingredient.get("transfer_nbt"));e.setValue(hot);}
   else e.setValue(ingredient);
  }json.addProperty("need_quenching",false);
 }
}
