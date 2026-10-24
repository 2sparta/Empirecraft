package ua.empirecraft.worldrules;
import net.minecraft.nbt.CompoundTag;

/** Enchantment removal is universal; potion metadata removal is limited to the forbidden item types. */
public final class TagPolicy {
    public static CompoundTag clean(CompoundTag tag,boolean waterBottle,boolean removePotionData) {
        if(tag!=null) { tag.m_128473_("Enchantments");tag.m_128473_("StoredEnchantments"); }
        if(waterBottle) {
            if(tag==null)tag=new CompoundTag();
            tag.m_128359_("Potion","minecraft:water");
        }
        if(tag!=null && (waterBottle || removePotionData)) {
            if(removePotionData)tag.m_128473_("Potion");
            tag.m_128473_("CustomPotionEffects");tag.m_128473_("CustomPotionColor");
        }
        return tag;
    }
    private TagPolicy() {}
}
