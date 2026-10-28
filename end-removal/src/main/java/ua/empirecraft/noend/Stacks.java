package ua.empirecraft.noend;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.registries.ForgeRegistries;

public final class Stacks {
    public static boolean active;
    private static final Map<Item,Item> ITEMS=new IdentityHashMap<>();
    private static final ThreadLocal<Integer> CREATIVE=ThreadLocal.withInitial(()->0);
    public static void beginCreative(){CREATIVE.set(CREATIVE.get()+1);}
    public static void endCreative(){CREATIVE.set(Math.max(0,CREATIVE.get()-1));}
    public static void initialize(){
        for(var entry:Policy.REPLACEMENTS.entrySet()){
            var from=new ResourceLocation(entry.getKey());var to=new ResourceLocation(entry.getValue());
            if(!ForgeRegistries.ITEMS.containsKey(from))continue;
            if(!ForgeRegistries.ITEMS.containsKey(to))throw new IllegalStateException("Missing migration target: "+to);
            ITEMS.put(ForgeRegistries.ITEMS.getValue(from),ForgeRegistries.ITEMS.getValue(to));
        }active=true;
    }
    public static Item replacement(Item item){return active && CREATIVE.get()==0 && EffectiveSide.get().isServer()?ITEMS.getOrDefault(item,item):item;}
    public static void clean(CompoundTag tag,int oldMaximum,int newMaximum){
        if(tag==null)return;
        tag.m_128473_("AttributeModifiers");tag.m_128473_("BlockStateTag");
        if(tag.m_128441_("Damage"))tag.m_128405_("Damage",oldMaximum<=0||newMaximum<=0?0:(int)Math.min(newMaximum-1,(long)tag.m_128451_("Damage")*newMaximum/oldMaximum));
        // Keep stored contents when an old shulker-box item becomes a chest item.
        if(tag.m_128441_("BlockEntityTag"))tag.m_128469_("BlockEntityTag").m_128473_("id");
    }
    private Stacks(){}
}
