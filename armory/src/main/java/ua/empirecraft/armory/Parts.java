package ua.empirecraft.armory;

import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.dries007.tfc.common.capabilities.heat.*;
import java.util.*;

public final class Parts {
    public static final String DATA="EmpirecraftArmor",TREATMENT="ArmoryTreatment";
    public static final List<String> QUALITY=List.of("poor","well","expert","perfect","master");
    private Parts(){}
    public static int quality(ItemStack s){int i=s.hasTag()?QUALITY.indexOf(s.getTag().getString("ForgingQuality")):-1;return i<0?1:i;}
    public static boolean steel(String metal){return metal.equals("steel")||metal.equals("black_steel");}
    public static Set<String> blueprints(String form){
        if(Set.of("dome","nape","visor").contains(form))return Set.of("armor","helmet");
        if(Set.of("thighs","knees","greaves").contains(form))return Set.of("armor","leggings");
        if(Set.of("feet","ankles").contains(form))return Set.of("armor","boots");
        if(Set.of("mail_panel","mail_sleeves","lamellar_panel","brigandine_panel").contains(form))return Set.of("armor","helmet","chestplate","leggings","boots");
        return Set.of("armor","chestplate");
    }
    public static float melt(String metal){return switch(metal){case "iron"->1535;case "steel"->1540;case "black_steel"->1485;case "bronze"->950;case "copper"->1080;case "silver"->961;case "gold"->1060;default->1535;};}
    public static void attachHeat(AttachCapabilitiesEvent<ItemStack> e){
        if(e.getObject().getItem() instanceof PartItem p&&!e.getCapabilities().containsKey(HeatCapability.KEY))e.addCapability(HeatCapability.KEY,new HeatHandler(1.0f*Armory.FORMS.get(p.form),melt(p.metal)*.6f,melt(p.metal)*.8f));
        var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.getObject().getItem());
        if(key!=null&&key.getNamespace().equals("hamzabus_ottoman_mod")&&Set.of("yatagan","kilij","topuz","devrish_axe","nakkareci").contains(key.getPath())&&!e.getCapabilities().containsKey(HeatCapability.KEY))e.addCapability(HeatCapability.KEY,new HeatHandler(1,924,1232));
    }
    public static int treatment(ItemStack s){return s.hasTag()?s.getTag().getInt(TREATMENT):0;}
    public static boolean ready(ItemStack s){return !(s.getItem() instanceof PartItem p)||!steel(p.metal)||treatment(s)==2;}
    public static void tooltip(ItemStack s,List<Component> lines){
        ArmorProvenance.tooltip(s,lines);
        if(s.getItem() instanceof PartItem p){
            lines.add(Component.translatable("armory.quality",Component.translatable("armory.quality."+QUALITY.get(quality(s)))).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("armory.metal_cost",Armory.FORMS.get(p.form)).withStyle(ChatFormatting.DARK_GRAY));
            if(steel(p.metal)){
                lines.add(Component.translatable("armory.treatment."+treatment(s)).withStyle(ready(s)?ChatFormatting.GREEN:ChatFormatting.GOLD));
                if(!ready(s)){float t=HeatCapability.getTemperature(s);boolean hardened=treatment(s)==1;float min=hardened?250:800,max=hardened?450:1050;String state=t<min?"cold":t>max?"hot":"ready";lines.add(Component.translatable("armory.thermal."+state,(int)min,(int)max).withStyle(state.equals("ready")?ChatFormatting.GREEN:ChatFormatting.GRAY));}
            }
        }
    }
    public static ItemStack sample(Item item){ItemStack s=new ItemStack(item);s.getOrCreateTag().putString("ForgingQuality","expert");if(item instanceof PartItem p&&steel(p.metal))s.getOrCreateTag().putInt(TREATMENT,2);return s;}
    public static CompoundTag partRecord(ItemStack s){var tag=new CompoundTag();tag.putString("id",net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).toString());tag.putString("quality",QUALITY.get(quality(s)));return tag;}
}
