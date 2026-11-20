package ua.empirecraft.launcherclient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraftforge.client.ForgeHooksClient;
import java.util.*;
/** Armor-only mannequin and models; no ClientLevel, connection, chunks or retained player world. */
public final class HomeArmor {
 private static HumanoidModel<LivingEntity> inner,outer;
 private static ArmorStand mannequin;
 private static boolean mannequinTried;
 private static final Set<String> failures=new HashSet<>();
 public static int renderedPieces;
 public static final Set<EquipmentSlot> renderedSlots=EnumSet.noneOf(EquipmentSlot.class);
 private static final class Mannequin extends ArmorStand {
  private EnumMap<EquipmentSlot,ItemStack> items=new EnumMap<>(EquipmentSlot.class);
  Mannequin(){super(EntityType.ARMOR_STAND,null);}
  @Override public ItemStack getItemBySlot(EquipmentSlot slot){return items==null?ItemStack.EMPTY:items.getOrDefault(slot,ItemStack.EMPTY);}
  @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack){if(items!=null)items.put(slot,stack);}
  @Override public Iterable<ItemStack> getArmorSlots(){return HomeEquipment.SLOTS.stream().map(this::getItemBySlot).toList();}
 }
 public static void invalidate(){inner=null;outer=null;}
 @SuppressWarnings({"rawtypes","unchecked"}) public static void render(PlayerModel<?> base,PoseStack pose){
  if(!HomePortrait.active()||!HomePreferences.armor()||!HomeEquipment.known())return;
  var mc=Minecraft.getInstance();
  if(inner==null){inner=new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));outer=new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));}
  if(!mannequinTried)try{mannequinTried=true;mannequin=new Mannequin();}catch(Exception e){if(failures.add("mannequin"))com.mojang.logging.LogUtils.getLogger().warn("Home armor mannequin unavailable; using item models without an entity",e);}
  for(var slot:HomeEquipment.SLOTS)if(mannequin!=null)mannequin.setItemSlot(slot,HomeEquipment.get(slot));
  renderedSlots.clear();
  for(var slot:HomeEquipment.SLOTS){var stack=mannequin==null?HomeEquipment.get(slot):mannequin.getItemBySlot(slot);if(!(stack.getItem() instanceof ArmorItem armor))continue;
   var standard=slot==EquipmentSlot.LEGS?inner:outer;((HumanoidModel)base).copyPropertiesTo(standard);standard.setAllVisible(false);
   switch(slot){case HEAD-> {standard.head.visible=true;standard.hat.visible=true;}case CHEST->{standard.body.visible=true;standard.leftArm.visible=true;standard.rightArm.visible=true;}case LEGS->{standard.body.visible=true;standard.leftLeg.visible=true;standard.rightLeg.visible=true;}case FEET->{standard.leftLeg.visible=true;standard.rightLeg.visible=true;}default->{}}
   try{
    var model=ForgeHooksClient.getArmorModel(mannequin,stack,slot,standard);
    if(stack.getItem() instanceof DyeableLeatherItem dye){int c=dye.getColor(stack);draw(model,pose,stack,slot,armor,null,((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f);draw(model,pose,stack,slot,armor,"overlay",1,1,1);}else draw(model,pose,stack,slot,armor,null,1,1,1);
    renderedPieces++;renderedSlots.add(slot);
   }catch(Exception e){String id=Objects.toString(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));if(failures.add(id))com.mojang.logging.LogUtils.getLogger().warn("Could not render home armor {}",id,e);}
  }
 }
 private static void draw(Model model,PoseStack pose,ItemStack stack,EquipmentSlot slot,ArmorItem armor,String suffix,float r,float g,float b){
  String material=armor.getMaterial().getName();int split=material.indexOf(':');String ns=split<0?"minecraft":material.substring(0,split),name=split<0?material:material.substring(split+1);
  String texture=ns+":textures/models/armor/"+name+"_layer_"+(slot==EquipmentSlot.LEGS?2:1)+(suffix==null?"":"_"+suffix)+".png";
  texture=ForgeHooksClient.getArmorTexture(mannequin,stack,texture,slot,suffix);
  var consumer=Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.armorCutoutNoCull(new ResourceLocation(texture)));
  PoseStack isolated=new PoseStack();isolated.last().pose().set(pose.last().pose());isolated.last().normal().set(pose.last().normal());
  model.renderToBuffer(isolated,consumer,0xf000f0,OverlayTexture.NO_OVERLAY,r,g,b,1);
 }
}
