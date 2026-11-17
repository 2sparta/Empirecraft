package ua.empirecraft.menuart;

import net.minecraft.nbt.CompoundTag;

/** NBT protocol v1. SRG names below are compiled directly against the pack's installed Forge API. */
public final class SkinProtocol {
    public static final int BASE = 742100;
    public static final int ICONS = 44;
    public static final String KEY = "empirecraft:menu_skin";
    private SkinProtocol() {}

    public static String section(CompoundTag tag) {
        if (tag == null || !tag.m_128425_("PublicBukkitValues", 10)) return "";
        String section = tag.m_128469_("PublicBukkitValues").m_128461_(KEY);
        return section.equals("town/1") || section.equals("provision/1") ? section : "";
    }

    public static int icon(CompoundTag tag) {
        if (section(tag).isEmpty() || !tag.m_128425_("CustomModelData", 3)) return -1;
        int index = tag.m_128451_("CustomModelData") - BASE;
        return index >= 0 && index < ICONS ? index : -1;
    }

    public static boolean anchor(CompoundTag tag) {
        return (section(tag).equals("town/1") && icon(tag) == 15)
            || (section(tag).equals("provision/1") && icon(tag) == 14);
    }
}
