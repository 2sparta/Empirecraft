package ua.empirecraft.content;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public final class Stacks {
    public static boolean active;
    private static final Map<Item,Item> REPLACEMENTS=new IdentityHashMap<>();
    public static void initialize() {
        REPLACEMENTS.clear();
        for(var pair:Policy.REPLACEMENTS.entrySet()) {
            var from=new ResourceLocation(pair.getKey());var to=new ResourceLocation(pair.getValue());
            if(!ForgeRegistries.ITEMS.containsKey(from))continue;
            if(!ForgeRegistries.ITEMS.containsKey(to))throw new IllegalStateException("Missing permitted replacement: "+to);
            REPLACEMENTS.put(ForgeRegistries.ITEMS.getValue(from),ForgeRegistries.ITEMS.getValue(to));
        }
        active=true;
    }
    public static Item replacement(Item item){return REPLACEMENTS.getOrDefault(item,item);}
    public static void clean(CompoundTag tag,int oldMaximum,int newMaximum) {
        if(tag==null)return;
        tag.m_128473_("AttributeModifiers");
        if(tag.m_128441_("Damage"))tag.m_128405_("Damage",Policy.damage(tag.m_128451_("Damage"),oldMaximum,newMaximum));
    }
    private Stacks(){}
}
