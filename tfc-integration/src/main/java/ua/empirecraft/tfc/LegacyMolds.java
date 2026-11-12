package ua.empirecraft.tfc;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Exchange old inventory casts atomically, including their metal and finished output. */
public final class LegacyMolds {
    private static final Map<String,String[]> METALS=Map.of(
        "iron",new String[]{"minecraft:iron_ingot","minecraft:iron_nugget"},
        "gold",new String[]{"minecraft:gold_ingot","minecraft:gold_nugget"},
        "copper",new String[]{"minecraft:copper_ingot","overgeared:copper_nugget"},
        "steel",new String[]{"overgeared:steel_ingot","overgeared:steel_nugget"});
    private LegacyMolds(){}
    public static boolean isLegacy(ItemStack stack){return Set.of("overgeared:clay_tool_cast","overgeared:unfired_tool_cast").contains(Integration.id(stack.getItem()));}
    private static Item get(String id){return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));}
    public static boolean migrate(Player player,int slot,ItemStack stack){
        if(!isLegacy(stack))return false;
        CompoundTag data=stack.getOrCreateTag(),materials=data.getCompound("Materials");
        List<ItemStack> refunds=new ArrayList<>();
        for(String metal:materials.getAllKeys()){
            double amount=materials.getDouble(metal);
            // Unrecognised addon alloys remain intact instead of being discarded.
            if(!METALS.containsKey(metal)||!Double.isFinite(amount)||amount<0||amount>100000||Math.abs(amount-Math.rint(amount))>1e-6)return false;
            int nuggets=(int)Math.rint(amount)*stack.getCount();String[] ids=METALS.get(metal);
            if(nuggets/9>0)refunds.add(new ItemStack(get(ids[0]),nuggets/9));
            if(nuggets%9>0)refunds.add(new ItemStack(get(ids[1]),nuggets%9));
        }
        if(data.contains("Output",10)){
            if(!materials.isEmpty())return false;
            ItemStack output=ItemStack.of(data.getCompound("Output"));
            if(!output.isEmpty()){output.setCount(output.getCount()*stack.getCount());refunds.add(output);}
        }
        String tool=data.getString("ToolType").toLowerCase(Locale.ROOT);
        if(!Set.of("axe","pickaxe","shovel","hoe","hammer","sword").contains(tool))tool="hammer";
        String shape=tool.equals("sword")?"sword_blade":tool+"_head";
        String prefix=Integration.id(stack.getItem()).endsWith("unfired_tool_cast")?"unfired_":"";
        Item mold=get("tfc:ceramic/"+prefix+shape+"_mold");if(mold==null||mold==Items.AIR)return false;
        // Clear the old stack before giving refunds: repeated ticks cannot refund twice.
        player.getInventory().setItem(slot,new ItemStack(mold,stack.getCount()));
        for(ItemStack refund:refunds)while(!refund.isEmpty()){
            ItemStack part=refund.split(Math.min(refund.getCount(),refund.getMaxStackSize()));
            if(!player.getInventory().add(part))player.drop(part,false);
        }
        player.getInventory().setChanged();return true;
    }
}
