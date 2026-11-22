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
    public static float melt(String metal){return switch(metal){case "iron"->1535;case "steel"->1540;case "black_steel"->1485;case "bronze"->950;case "copper"->1080;case "silver"->961;case "gold"->1060;default->1535;};}
    public static void attachHeat(AttachCapabilitiesEvent<ItemStack> e){
        if(e.getObject().getItem() instanceof PartItem p&&!e.getCapabilities().containsKey(HeatCapability.KEY))e.addCapability(HeatCapability.KEY,new HeatHandler(1.0f*Armory.FORMS.get(p.form),melt(p.metal)*.6f,melt(p.metal)*.8f));
        var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.getObject().getItem());
        if(key!=null&&key.getNamespace().equals("hamzabus_ottoman_mod")&&Set.of("yatagan","kilij","topuz","devrish_axe","nakkareci").contains(key.getPath())&&!e.getCapabilities().containsKey(HeatCapability.KEY))e.addCapability(HeatCapability.KEY,new HeatHandler(1,924,1232));
    }
    public static int treatment(ItemStack s){return s.hasTag()?s.getTag().getInt(TREATMENT):0;}
    public static boolean ready(ItemStack s){return true;}
    public static void tooltip(ItemStack s,List<Component> lines){
        ArmorProvenance.tooltip(s,lines);
        if(s.getItem() instanceof PartItem p){
            lines.add(Component.translatable("armory.quality",Component.translatable("armory.quality."+QUALITY.get(quality(s)))).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("armory.metal_cost",Armory.FORMS.get(p.form)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
    public static ItemStack sample(Item item){
        ItemStack stack=new ItemStack(item);
        stack.getOrCreateTag().putString("ForgingQuality","expert");
        return stack;
    }
    public static CompoundTag partRecord(ItemStack s){var tag=new CompoundTag();tag.putString("id",net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).toString());tag.putString("quality",QUALITY.get(quality(s)));return tag;}
}
