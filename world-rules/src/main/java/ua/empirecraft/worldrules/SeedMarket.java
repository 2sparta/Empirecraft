package ua.empirecraft.worldrules;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Small rotating seed/sapling stock; no new entity, forced spawn, or new currency. */
public final class SeedMarket {
    public static final List<String> STOCK = stock();
    private static List<String> stock() {
        var ids=new ArrayList<String>();
        for(String fruit:List.of("apple","bayberry","durian","fig","hawberry","kiwi","lychee","mango","mangosteen","orange","peach","pear","persimmon"))ids.add("fruitsdelight:"+fruit+"_sapling");
        ids.addAll(List.of("fruitsdelight:lemon_seeds","fruitsdelight:hamimelon_seeds","fruitsdelight:pineapple","fruitsdelight:blueberry","fruitsdelight:cranberry",
            "vinery:apple_tree_sapling","vinery:dark_cherry_sapling","vinery:red_grape_seeds","vinery:white_grape_seeds",
            "vinery:taiga_grape_seeds_red","vinery:taiga_grape_seeds_white","vinery:jungle_grape_seeds_red","vinery:jungle_grape_seeds_white","vinery:savanna_grape_seeds_red","vinery:savanna_grape_seeds_white",
            "caupona:fig_sapling","caupona:walnut_sapling","caupona:wolfberry_sapling","caupona:silphium",
            "ecologics:walnut_sapling","ecologics:coconut","spawn:date_seed","farmersdelight:rice","antiquelegacy:raw_tin"));
        return List.copyOf(ids);
    }
    public static void trades(WandererTradesEvent event) {
        Item emerald=ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:emerald"));
        for(String value:STOCK) {
            ResourceLocation id=new ResourceLocation(value);
            if(!ForgeRegistries.ITEMS.containsKey(id))continue;
            Item item=ForgeRegistries.ITEMS.getValue(id);
            int count=value.contains(":raw_")?2:1;
            VillagerTrades.ItemListing trade=(trader,random)->new MerchantOffer(new ItemStack(emerald,2),new ItemStack(item,count),4,1,.05f);
            event.getRareTrades().add(trade);
        }
    }
    private SeedMarket() {}
}

