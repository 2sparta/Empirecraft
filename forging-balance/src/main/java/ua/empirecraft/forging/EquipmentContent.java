package ua.empirecraft.forging;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

public final class EquipmentContent {
    public static final Set<String> STEEL_ARMOR = Set.of("overgeared:steel_helmet", "overgeared:steel_chestplate",
        "overgeared:steel_leggings", "overgeared:steel_boots");
    private EquipmentContent() {}
    public static boolean removed(Item item) {
        var id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && STEEL_ARMOR.contains(id.toString());
    }
    public static boolean potionArrow(Item item, CompoundTag tag) {
        var id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return false;
        String name = id.toString();
        return name.equals("minecraft:tipped_arrow") || name.equals("overgeared:lingering_arrow")
            || (name.equals("overgeared:iron_arrow") || name.equals("overgeared:steel_arrow") || name.equals("overgeared:diamond_arrow"))
                && hasPotion(tag);
    }
    public static boolean hasPotion(CompoundTag tag) {
        return tag != null && (tag.m_128441_("Potion") || tag.m_128441_("CustomPotionEffects") || tag.m_128441_("LingeringPotion"));
    }
    /** Registry IDs stay readable for old saves; removed equipment cannot remain usable. */
    public static void clean(ItemStack stack, Item item, CompoundTag tag) {
        if (item == null) return;
        if (removed(item)) {
            stack.setItem(Items.f_41852_);
            stack.m_41764_(0);
            return;
        }
        if (!potionArrow(item, tag)) return;
        var id = ForgeRegistries.ITEMS.getKey(item).toString();
        if (id.equals("minecraft:tipped_arrow") || id.equals("overgeared:lingering_arrow")) stack.setItem(Items.f_42412_);
        if (tag != null) for (String key : List.of("Potion", "CustomPotionEffects", "CustomPotionColor", "LingeringPotion")) tag.m_128473_(key);
    }
}
