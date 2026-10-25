package ua.empirecraft.worldrules;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Operate on raw stack fields: calling stack.getItem/getTag here would recurse into the mixin. */
public final class ItemPolicy {
    private static final Map<Item,Item> REPLACEMENTS = new IdentityHashMap<>();
    private static final Set<Item> EFFECT_ARROWS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Item potion, splash, lingering, tipped, potionBong;
    public static void initialize() {
        REPLACEMENTS.clear();
        EFFECT_ARROWS.clear();
        replace("minecraft:enchanted_book","minecraft:book");
        replace("minecraft:splash_potion","minecraft:glass_bottle");
        replace("minecraft:lingering_potion","minecraft:glass_bottle");
        replace("minecraft:tipped_arrow","minecraft:arrow");
        replace("overgeared:lingering_arrow","minecraft:arrow");
        for(String id:new String[]{"overgeared:iron_arrow","overgeared:steel_arrow","overgeared:diamond_arrow","overgeared:lingering_arrow"}) {
            Item arrow=item(id);if(arrow!=null)EFFECT_ARROWS.add(arrow);
        }
        replace("nirvana:potion_bong","nirvana:bong");
        potion=item("minecraft:potion"); splash=item("minecraft:splash_potion");
        lingering=item("minecraft:lingering_potion"); tipped=item("minecraft:tipped_arrow"); potionBong=item("nirvana:potion_bong");
    }
    private static Item item(String value) {
        ResourceLocation id=new ResourceLocation(value);
        return ForgeRegistries.ITEMS.containsKey(id)?ForgeRegistries.ITEMS.getValue(id):null;
    }
    private static void replace(String from,String to) { Item a=item(from),b=item(to);if(a!=null && b!=null)REPLACEMENTS.put(a,b); }
    public static Item replacement(Item raw) { return REPLACEMENTS.getOrDefault(raw,raw); }
    public static CompoundTag clean(Item raw,CompoundTag tag) {
        return TagPolicy.clean(tag,raw!=null && raw==potion,raw!=null && (raw==splash || raw==lingering || raw==tipped || raw==potionBong || EFFECT_ARROWS.contains(raw)));
    }
    private ItemPolicy() {}
}

