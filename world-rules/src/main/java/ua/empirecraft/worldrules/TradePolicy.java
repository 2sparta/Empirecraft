package ua.empirecraft.worldrules;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

/** Reject retired trade templates before stack migration disguises their original contents. */
public final class TradePolicy {
    private static final Set<Item> RETIRED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Item> ARROWS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static Item potion;
    private TradePolicy() {}

    public static void initialize() {
        RETIRED.clear(); ARROWS.clear();
        try (var input=TradePolicy.class.getResourceAsStream("/empirecraft-retired-trades.json")) {
            if(input==null)throw new IllegalStateException("Missing reviewed trade exclusions");
            var data=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var value:data.getAsJsonArray("items")) {
                var id=new ResourceLocation(value.getAsString());
                if(ForgeRegistries.ITEMS.containsKey(id))RETIRED.add(ForgeRegistries.ITEMS.getValue(id));
            }
        } catch(IOException error) { throw new IllegalStateException("Cannot load trade exclusions",error); }
        for(String id:List.of("minecraft:tipped_arrow","overgeared:iron_arrow","overgeared:steel_arrow","overgeared:diamond_arrow","overgeared:lingering_arrow")) {
            var key=new ResourceLocation(id);
            if(ForgeRegistries.ITEMS.containsKey(key))ARROWS.add(ForgeRegistries.ITEMS.getValue(key));
        }
        potion=ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:potion"));
    }

    /** Called on raw fields before any sanitizing getter; does not call another ItemStack method. */
    public static boolean retired(Item raw,CompoundTag tag) {
        if(raw==null)return false;
        if(RETIRED.contains(raw))return true;
        if(tag==null)return false;
        if(tag.m_128425_("Enchantments",9) && !tag.m_128437_("Enchantments",10).isEmpty())return true;
        if(tag.m_128425_("StoredEnchantments",9) && !tag.m_128437_("StoredEnchantments",10).isEmpty())return true;
        if(ARROWS.contains(raw))return tag.m_128441_("Potion") || tag.m_128441_("CustomPotionEffects") || tag.m_128441_("LingeringPotion");
        if(raw!=potion)return false;
        String kind=tag.m_128461_("Potion");
        return (!kind.isEmpty() && !kind.equals("minecraft:water")) || (tag.m_128425_("CustomPotionEffects",9) && !tag.m_128437_("CustomPotionEffects",10).isEmpty());
    }

    private static boolean validStack(ItemStack stack,boolean required) {
        // Getter triggers both existing migration and the higher-priority origin capture.
        stack.m_41720_(); stack.m_41783_();
        if((Object)stack instanceof TradeStack origin && origin.empirecraft$retiredTradeItem())return false;
        return !required || !stack.m_41619_();
    }
    public static boolean valid(MerchantOffer offer) {
        if(offer==null)return false;
        ItemStack first=offer.m_45352_(),second=offer.m_45364_(),result=offer.m_45368_();
        if(!validStack(first,true) || !validStack(second,false) || !validStack(result,true))return false;
        // Old saves may already contain the former tipped-arrow/book trade after migration.
        // Paying extra for the same unchanged input gives no product and must not survive.
        return !sameOrLess(first,result) && !sameOrLess(second,result);
    }
    private static boolean sameOrLess(ItemStack input,ItemStack result) {
        if(input.m_41619_() || input.m_41613_()<result.m_41613_() || input.m_41720_()!=result.m_41720_())return false;
        CompoundTag left=input.m_41783_(),right=result.m_41783_();
        if(left!=null && left.m_128456_())left=null;
        if(right!=null && right.m_128456_())right=null;
        return Objects.equals(left,right);
    }
    public static void prune(MerchantOffers offers) { offers.removeIf(offer->!valid(offer)); }
    public static MerchantOffer offer(VillagerTrades.ItemListing listing,Entity trader,RandomSource random) {
        MerchantOffer result=listing.m_213663_(trader,random);
        return valid(result)?result:null;
    }
}
